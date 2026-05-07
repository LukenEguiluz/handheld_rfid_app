package com.dohealth.handheld.ui.esferica



import android.content.Context

import android.graphics.Rect

import android.net.Uri

import android.os.Bundle

import android.content.res.ColorStateList

import android.view.Menu

import android.view.MenuItem

import android.view.MotionEvent

import android.view.View

import android.view.inputmethod.InputMethodManager

import android.widget.ArrayAdapter

import android.widget.Filter

import android.widget.Toast

import androidx.core.content.ContextCompat

import androidx.activity.result.contract.ActivityResultContracts

import androidx.appcompat.app.AppCompatActivity

import androidx.core.widget.doOnTextChanged

import androidx.lifecycle.lifecycleScope

import androidx.recyclerview.widget.LinearLayoutManager

import com.cf.beans.CmdData

import com.cf.beans.KeyStateBean

import com.cf.beans.TagInfoBean

import com.cf.ble.interfaces.IOnNotifyCallback

import com.cf.zsdk.BleCore

import com.cf.zsdk.CfSdk

import com.cf.zsdk.SdkC

import com.cf.zsdk.cmd.CmdBuilder

import com.cf.zsdk.cmd.CmdType

import com.cf.zsdk.uitl.FormatUtil

import com.dohealth.handheld.R

import com.dohealth.handheld.data.esferica.EsfericaCountPersistedSession

import com.dohealth.handheld.data.esferica.EsfericaCountSessionStore

import com.dohealth.handheld.data.esferica.ProductCountResultDto

import com.dohealth.handheld.databinding.ActivityEsfericaCountBinding

import com.dohealth.handheld.domain.EsfericaReconcileLocal

import com.dohealth.handheld.utils.DeterminateLoadingProgress

import com.dohealth.handheld.utils.EsfericaExport

import com.dohealth.handheld.utils.EsfericaRfidNormalizer

import com.google.android.material.textfield.MaterialAutoCompleteTextView

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.Job

import kotlinx.coroutines.async

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler

import kotlinx.coroutines.launch

import kotlinx.coroutines.withContext

import java.util.LinkedHashSet

import java.util.Locale

import java.util.UUID



class EsfericaCountActivity : AppCompatActivity(), IOnNotifyCallback {



    companion object {

        const val EXTRA_SESSION_ID = "esferica_session_id"

    }



    private data class LabeledProductRow(val dto: ProductCountResultDto, val label: String)

    private enum class ProductStatusFilter {

        NOT_READ,

        INCOMPLETE,

        COMPLETE,

    }



    private lateinit var binding: ActivityEsfericaCountBinding

    private lateinit var bleCore: BleCore

    private lateinit var store: EsfericaCountSessionStore

    private lateinit var sessionId: String

    private lateinit var session: EsfericaCountPersistedSession

    private lateinit var expectedRfidSet: Set<String>



    private val serviceUuid = UUID.fromString(com.dohealth.handheld.utils.Constants.SERVICE_UUID)

    private val writeUuid = UUID.fromString(com.dohealth.handheld.utils.Constants.WRITE_UUID)



    private var isReading = false

    private var isTriggerHoldMode = false



    private val scannedUnique = LinkedHashSet<String>()

    private val expandedProductKeys = mutableSetOf<String>()



    private var reconciledProducts: List<ProductCountResultDto> = emptyList()



    private var reconcileGeneration = 0

    private var reconcileDebounceJob: Job? = null

    private var persistDebounceJob: Job? = null

    private val errorHandler = CoroutineExceptionHandler { _, throwable ->

        reportError(throwable)

    }



    private var productSearchChoices: List<LabeledProductRow> = emptyList()

    private var productStatusFilter: ProductStatusFilter = ProductStatusFilter.NOT_READ



    private var countBootFakeProgressJob: Job? = null



    private lateinit var productAdapter: EsfericaProductResultsAdapter



    private var pendingExportCsv: String? = null

    private val createExportCsvLauncher = registerForActivityResult(

        ActivityResultContracts.CreateDocument("text/csv"),

    ) { uri ->

        uri?.let { saveCsvToUri(it, pendingExportCsv) } ?: run { pendingExportCsv = null }

    }



    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        binding = ActivityEsfericaCountBinding.inflate(layoutInflater)

        setContentView(binding.root)



        store = EsfericaCountSessionStore(this)

        sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: store.getActiveSessionId().orEmpty()

        if (sessionId.isEmpty()) {

            Toast.makeText(this, R.string.send_error, Toast.LENGTH_SHORT).show()

            finish()

            return

        }

        store.setActiveSessionId(sessionId)

        val loaded = store.getSession(sessionId)

        if (loaded == null) {

            Toast.makeText(this, R.string.send_error, Toast.LENGTH_SHORT).show()

            finish()

            return

        }

        session = loaded

        expectedRfidSet = EsfericaReconcileLocal.normalizedExpectedRfidSet(session.expectedItems)



        scannedUnique.clear()

        scannedUnique.addAll(session.scannedRfidsOrdered)



        bleCore = CfSdk.get(SdkC.BLE)



        setSupportActionBar(binding.toolbar)

        supportActionBar?.title = getString(R.string.esferica_physical_count_title)

        binding.toolbar.setNavigationOnClickListener {

            standbyAndFinish()

        }



        binding.sessionContextText.text = "${session.clientName}\n${session.warehouseName}"



        productAdapter = EsfericaProductResultsAdapter { row ->

            val k = productKey(row.dto)

            if (expandedProductKeys.contains(k)) expandedProductKeys.remove(k) else expandedProductKeys.add(k)

            applyProductFilter()

        }

        binding.productsBreakdownRecycler.layoutManager = LinearLayoutManager(this)

        binding.productsBreakdownRecycler.adapter = productAdapter

        ContextCompat.getDrawable(this, R.drawable.spinner_dropdown_popup_light)?.let { ddBg ->

            binding.productSearchInput.setDropDownBackgroundDrawable(

                ddBg.constantState?.newDrawable()?.mutate() ?: ddBg,

            )

        }

        configureProductSearchField()

        binding.productSearchInput.setOnItemClickListener { _, _, _, _ -> applyProductFilter() }

        binding.productSearchInput.doOnTextChanged { _, _, _, _ -> applyProductFilter() }

        binding.productStatusFilterGroup.check(R.id.filterNotReadButton)

        binding.productStatusFilterGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->

            if (!isChecked) return@addOnButtonCheckedListener

            productStatusFilter = when (checkedId) {

                R.id.filterNotReadButton -> ProductStatusFilter.NOT_READ

                R.id.filterIncompleteButton -> ProductStatusFilter.INCOMPLETE

                R.id.filterCompleteButton -> ProductStatusFilter.COMPLETE

                else -> productStatusFilter

            }

            applyProductFilter()

        }



        refreshSessionCountLabels()

        reconcileProductsFromSession(showBootOverlay = true)



        binding.startReadButton.setOnClickListener {

            if (isReading) stopReading() else startReading()

        }



        checkTriggerHoldMode()

        setDeviceModeRfid()

        updateReadButtons()

    }



    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {

        if (ev.action == MotionEvent.ACTION_DOWN && ::binding.isInitialized) {

            val fv = currentFocus

            val searchInner = binding.productSearchInput

            if (fv != null && fv === searchInner) {

                val r = Rect()

                binding.productSearchInputLayout.getGlobalVisibleRect(r)

                val x = ev.rawX.toInt()

                val y = ev.rawY.toInt()

                if (!r.contains(x, y)) {

                    searchInner.clearFocus()

                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager

                    imm?.hideSoftInputFromWindow(binding.root.windowToken, 0)

                }

            }

        }

        return super.dispatchTouchEvent(ev)

    }



    override fun onCreateOptionsMenu(menu: Menu): Boolean {

        menuInflater.inflate(R.menu.menu_esferica_count, menu)

        return true

    }



    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when (item.itemId) {

            R.id.menu_export_expected_vs_counted_csv -> {

                startExportExpectedVsCountedCsv()

                return true

            }

        }

        return super.onOptionsItemSelected(item)

    }



    private fun productKey(p: com.dohealth.handheld.data.esferica.ProductCountResultDto) =

        "${p.code ?: ""}\u0000${p.description ?: ""}"



    private fun refreshSessionCountLabels() {

        val expected = session.expectedItems.size

        val matched = scannedUnique.count { it in expectedRfidSet }

        val offCatalog = scannedUnique.size - matched



        binding.expectedCountText.text = getString(R.string.esferica_expected_label, expected)

        binding.scannedMatchedCountText.text = getString(R.string.esferica_matched_unique_label, matched)

        binding.scannedOffCatalogText.text = getString(R.string.esferica_off_catalog_unique_label, offCatalog)

    }



    /** Tras nueva lectura: solo KPI; la conciliación del desglose va en segundo plano (sin pisar overlay). */

    private fun refreshStats() {

        refreshSessionCountLabels()

        val showBoot = binding.countBootOverlay.visibility == View.VISIBLE

        if (showBoot) {

            reconcileProductsFromSession(showBootOverlay = true)

            return

        }

        reconcileDebounceJob?.cancel()

        reconcileDebounceJob = lifecycleScope.launch(errorHandler) {

            kotlinx.coroutines.delay(240)

            if (isDestroyed) return@launch

            reconcileProductsFromSession(showBootOverlay = false)

        }

    }



    /**

     * Solo la primera entrada (overlay) anima barra **0→95** y acaba en **100** al cerrar.

     * Mientras llegan RFIDs durante la carga, [refreshStats] debe volver con overlay ya visible (`true`)

     * para repetir datos correctos sin que lecturas iniciadas antes cancelen esta corrutina con `gone`.
     */

    private fun reconcileProductsFromSession(showBootOverlay: Boolean) {

        countBootFakeProgressJob?.cancel()

        if (!showBootOverlay) {

            val gen = ++reconcileGeneration

            val capturedItems = session.expectedItems

            val scannedSnapshot = scannedUnique.toList()

            countBootFakeProgressJob = lifecycleScope.launch {

                val products = withContext(Dispatchers.Default) {

                    EsfericaReconcileLocal.reconcile(capturedItems, scannedSnapshot)

                        .products

                        .orEmpty()

                }

                if (gen != reconcileGeneration) return@launch

                reconciledProducts = products

                refreshProductSearchAdapter()

                applyProductFilter()

            }

            return

        }



        val gen = ++reconcileGeneration

        val capturedItems = session.expectedItems

        val scannedSnapshot = scannedUnique.toList()



        if (showBootOverlay) {

            binding.countBootOverlay.visibility = View.VISIBLE

            binding.countBootLoadingText.setText(R.string.esferica_loading_count_session)

            binding.countBootLinearProgress.isIndeterminate = false

            binding.countBootLinearProgress.setProgressCompat(0, false)

            binding.countBootProgressPercentText.text = "0%"

        }



        countBootFakeProgressJob = lifecycleScope.launch {

            val products =

                if (showBootOverlay) {

                    coroutineScope {

                        val reconcileDeferred = async(Dispatchers.Default) {

                            EsfericaReconcileLocal.reconcile(capturedItems, scannedSnapshot)

                                .products

                                .orEmpty()

                        }



                        val rampDeferred = async {

                            DeterminateLoadingProgress.rampSegmentEaseOut(

                                binding.countBootLinearProgress,

                                binding.countBootProgressPercentText,

                                from = 0,

                                to = 95,

                                durationMs = 320L,

                            )

                        }



                        rampDeferred.await()

                        reconcileDeferred.await()

                    }

                } else {

                    withContext(Dispatchers.Default) {

                        EsfericaReconcileLocal.reconcile(capturedItems, scannedSnapshot)

                            .products

                            .orEmpty()

                    }

                }



            if (gen != reconcileGeneration) return@launch



            reconciledProducts = products

            refreshProductSearchAdapter()

            applyProductFilter()



            if (!showBootOverlay) return@launch



            DeterminateLoadingProgress.ensureHeldAt(

                binding.countBootLinearProgress,

                binding.countBootProgressPercentText,

            )



            DeterminateLoadingProgress.flashFullProgress(

                binding.countBootLinearProgress,

                binding.countBootProgressPercentText,

            )



            binding.countBootOverlay.visibility = View.GONE

        }

    }



    private fun productSearchDisplayLabel(p: ProductCountResultDto): String {

        val c = p.code?.trim()?.takeIf { it.isNotEmpty() }

        val d = p.description?.trim()?.takeIf { it.isNotEmpty() }

        return when {

            c != null && d != null -> "$c · $d"

            c != null -> c

            d != null -> d

            else -> "—"

        }

    }



    private fun refreshProductSearchAdapter() {

        productSearchChoices = reconciledProducts

            .map { LabeledProductRow(it, productSearchDisplayLabel(it)) }

            .sortedWith(compareBy { it.label.lowercase(Locale.getDefault()) })

        val labels = productSearchChoices.map { it.label }

        binding.productSearchInput.setAdapter(

            ArrayAdapter(this, R.layout.item_autocomplete_dropdown_line, labels),

        )

    }

    private fun updateProductCompletionSummary() {

        val total = reconciledProducts.count { (it.expectedQty ?: 0) > 0 }

        val completed = reconciledProducts.count {

            val exp = it.expectedQty ?: 0

            val scn = it.scannedQty ?: 0

            exp > 0 && scn >= exp

        }

        binding.productCompletionSummaryText.text =

            getString(R.string.esferica_completed_codes_summary, completed, total)

    }



    private fun configureProductSearchField() {

        binding.productSearchInput.threshold = 0

        binding.productSearchInput.setOnClickListener { openProductSearchDropdown(binding.productSearchInput) }

        binding.productSearchInput.setOnFocusChangeListener { _, sel ->

            if (sel) openProductSearchDropdown(binding.productSearchInput)

        }

    }



    private fun openProductSearchDropdown(actv: MaterialAutoCompleteTextView) {

        @Suppress("UNCHECKED_CAST")

        val adapter = actv.adapter as? ArrayAdapter<String> ?: return

        if (adapter.count == 0) return

        actv.threshold = 0

        adapter.filter.filter(

            "",

            object : Filter.FilterListener {

                override fun onFilterComplete(count: Int) {

                    binding.root.post {

                        if (isDestroyed || count == 0) return@post

                        actv.showDropDown()

                    }

                }

            },

        )

    }



    private fun applyProductFilter() {

        updateProductCompletionSummary()

        val raw = binding.productSearchInput.text?.toString()?.trim().orEmpty()

        val ql = raw.lowercase(Locale.getDefault())

        val exactChoice = productSearchChoices.find { it.label.equals(raw, ignoreCase = true) }?.dto

        val base =

            when {

                raw.isBlank() -> reconciledProducts

                exactChoice != null -> listOf(exactChoice)

                else -> reconciledProducts.filter { p ->

                    (p.code?.lowercase(Locale.getDefault())?.contains(ql) == true) ||

                        (p.description?.lowercase(Locale.getDefault())?.contains(ql) == true)

                }

            }

        val filtered =

            when (productStatusFilter) {

                ProductStatusFilter.NOT_READ -> base.filter { (it.scannedQty ?: 0) <= 0 }

                ProductStatusFilter.INCOMPLETE -> base.filter {

                    val exp = it.expectedQty ?: 0

                    val scn = it.scannedQty ?: 0

                    exp > 0 && scn in 1 until exp

                }

                ProductStatusFilter.COMPLETE -> base.filter {

                    val exp = it.expectedQty ?: 0

                    val scn = it.scannedQty ?: 0

                    exp > 0 && scn >= exp

                }

            }

        productAdapter.submitList(

            filtered.map {

                EsfericaProductRow(

                    it,

                    expandedProductKeys.contains(productKey(it)),

                )

            },

        )

    }



    private fun persistScans() {

        val scannedNow = scannedUnique.toList()

        session = session.copy(scannedRfidsOrdered = scannedNow)

        persistDebounceJob?.cancel()

        persistDebounceJob = lifecycleScope.launch(Dispatchers.IO) {

            kotlinx.coroutines.delay(220)

            kotlin.runCatching {

                store.updateScanned(sessionId, scannedNow)

            }.onFailure { reportError(it) }

        }

        refreshStats()

    }

    private fun reportError(t: Throwable) {

        val msg = (t.message ?: t.javaClass.simpleName).take(180)

        runOnUiThread {

            Toast.makeText(

                this@EsfericaCountActivity,

                getString(R.string.esferica_read_error, msg),

                Toast.LENGTH_LONG,

            ).show()

            binding.statusReadingText.text =

                "${binding.statusReadingText.text}\n${getString(R.string.esferica_read_error_inline, msg)}"

        }

    }



    private fun startExportExpectedVsCountedCsv() {

        lifecycleScope.launch(Dispatchers.Default) {

            val csv =

                runCatching { EsfericaExport.buildExpectedVsValidCountedCsv(session) }.getOrNull()

            withContext(Dispatchers.Main) {

                if (csv == null || csv.isBlank()) {

                    Toast.makeText(this@EsfericaCountActivity, R.string.export_error, Toast.LENGTH_SHORT).show()

                } else {

                    pendingExportCsv = csv

                    val name =

                        "esferica_esperados_vs_contados_${System.currentTimeMillis()}.csv"

                    createExportCsvLauncher.launch(name)

                }

            }

        }

    }



    private fun saveCsvToUri(uri: Uri, csv: String?) {

        if (csv == null) return

        lifecycleScope.launch(Dispatchers.IO) {

            runCatching {

                contentResolver.openOutputStream(uri)?.use {

                    it.write(csv.toByteArray(Charsets.UTF_8))

                }

            }

            withContext(Dispatchers.Main) {

                pendingExportCsv = null

                Toast.makeText(this@EsfericaCountActivity, R.string.export_success, Toast.LENGTH_SHORT).show()

            }

        }

    }



    private fun updateReadButtons() {

        binding.startReadButton.visibility = View.VISIBLE

        if (isReading) {

            binding.statusReadingText.text = getString(R.string.esferica_reading_running)

            binding.startReadButton.setText(R.string.esferica_stop_reading)

            binding.startReadButton.backgroundTintList = ColorStateList.valueOf(

                ContextCompat.getColor(this, R.color.warning),

            )

        } else {

            binding.statusReadingText.text = getString(R.string.esferica_reading_stopped)

            binding.startReadButton.setText(R.string.esferica_start_reading)

            binding.startReadButton.backgroundTintList = ColorStateList.valueOf(

                ContextCompat.getColor(this, R.color.primary_red),

            )

        }

    }



    private fun checkTriggerHoldMode() {

        val prefs = getSharedPreferences(com.dohealth.handheld.utils.Constants.PREFS_NAME, MODE_PRIVATE)

        val workMode = prefs.getInt(

            com.dohealth.handheld.utils.Constants.KEY_WORK_MODE,

            com.dohealth.handheld.utils.Constants.DEFAULT_WORK_MODE,

        )

        isTriggerHoldMode = workMode != 2

    }



    private fun setDeviceModeRfid() {

        val modeCmd = CmdBuilder.buildSetReadModeCmd(0x00, ByteArray(7))

        bleCore.writeData(serviceUuid, writeUuid, modeCmd)

    }



    private fun startReading() {

        if (isReading) return

        bleCore.setOnNotifyCallback(this)

        setDeviceModeRfid()

        isReading = true

        updateReadButtons()

        binding.root.postDelayed({

            val inventoryCmd = CmdBuilder.buildInventoryISOContinueCmd(0x00, 0)

            bleCore.writeData(serviceUuid, writeUuid, inventoryCmd)

        }, 400)

    }



    private fun stopReading() {

        if (!isReading) return

        isReading = false

        val stopCmd = CmdBuilder.buildStopInventoryCmd()

        bleCore.writeData(serviceUuid, writeUuid, stopCmd)

        updateReadButtons()

    }



    private fun standbyAndFinish() {

        lifecycleScope.launch {

            kotlinx.coroutines.delay(80)

            val stopCmd = CmdBuilder.buildStopInventoryCmd()

            bleCore.writeData(serviceUuid, writeUuid, stopCmd)

            kotlinx.coroutines.delay(120)

            val rfidModeCmd = CmdBuilder.buildSetReadModeCmd(0x00, ByteArray(7))

            bleCore.writeData(serviceUuid, writeUuid, rfidModeCmd)

            finish()

        }

    }



    override fun onNotify(cmdType: Int, cmdData: CmdData) {

        kotlin.runCatching {

            when (cmdType) {

                CmdType.TYPE_INVENTORY -> handleRfid(cmdData)

                CmdType.TYPE_KEY_STATE -> handleTriggerKeyState(cmdData)

            }

        }.onFailure { reportError(it) }

    }



    override fun onNotify(bytes: ByteArray) {}



    private fun handleTriggerKeyState(cmdData: CmdData) {

        if (!isTriggerHoldMode) return

        val keyState = cmdData.getData() as? KeyStateBean ?: return

        runOnUiThread {

            when (keyState.mKeyState.toInt()) {

                0x01 -> {

                    startReading()

                }

                0x02 -> stopReading()

            }

        }

    }



    private fun handleRfid(cmdData: CmdData) {

        val tagInfo = kotlin.runCatching { cmdData.getData() as? TagInfoBean }.getOrNull() ?: return

        if (tagInfo.mStatus != 0x00 || tagInfo.mEPCNum == null) return

        val epcHex = kotlin.runCatching { FormatUtil.bytesToHexStr(tagInfo.mEPCNum) }
            .getOrNull()
            ?.replace(" ", "")
            ?: return

        val norm = EsfericaRfidNormalizer.normalize(epcHex) ?: return

        if (!scannedUnique.add(norm)) return

        runOnUiThread {

            kotlin.runCatching { persistScans() }.onFailure { reportError(it) }

        }

    }



    override fun onResume() {

        super.onResume()

        bleCore.setOnNotifyCallback(this)

        checkTriggerHoldMode()

        setDeviceModeRfid()

    }



    override fun onPause() {

        super.onPause()

        if (isReading) stopReading()

    }



    override fun onDestroy() {

        super.onDestroy()

        reconcileGeneration++

        countBootFakeProgressJob?.cancel()

        if (::binding.isInitialized) {

            binding.countBootOverlay.visibility = View.GONE

        }

        if (isReading) {

            bleCore.writeData(serviceUuid, writeUuid, CmdBuilder.buildStopInventoryCmd())

            isReading = false

        }

        bleCore.setOnNotifyCallback(null)

    }

}

