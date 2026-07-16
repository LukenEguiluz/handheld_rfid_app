package com.dohealth.handheld.ui.hgm



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

import com.dohealth.handheld.data.hgm.HgmCountPersistedSession

import com.dohealth.handheld.data.hgm.HgmCountSessionStore

import com.dohealth.handheld.data.esferica.ExpectedInventoryItemDto

import com.dohealth.handheld.data.esferica.ProductCountResultDto

import com.dohealth.handheld.databinding.ActivityHgmCountBinding

import com.dohealth.handheld.domain.EsfericaReconcileLocal

import com.dohealth.handheld.domain.HgmProductGrouping

import com.dohealth.handheld.utils.DeterminateLoadingProgress

import com.dohealth.handheld.utils.HgmExport

import com.dohealth.handheld.utils.EsfericaRfidNormalizer

import com.dohealth.handheld.utils.HgmSkuRfidConverter

import com.dohealth.handheld.utils.HgmTextNormalize

import com.dohealth.handheld.utils.RfidDistanceDialog
import com.dohealth.handheld.utils.RfidDistancePreset
import com.dohealth.handheld.utils.RfidDistancePresetHelper
import com.dohealth.handheld.utils.RfidParamSnapshot

import com.google.android.material.bottomsheet.BottomSheetDialog

import com.google.android.material.textfield.MaterialAutoCompleteTextView

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.Job

import kotlinx.coroutines.async

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler

import kotlinx.coroutines.delay

import kotlinx.coroutines.isActive

import kotlinx.coroutines.launch

import kotlinx.coroutines.withContext

import java.util.LinkedHashSet

import java.util.UUID



class HgmCountActivity : AppCompatActivity(), IOnNotifyCallback {



    companion object {

        const val EXTRA_SESSION_ID = "hgm_session_id"

        private const val PERSIST_INTERVAL_MS = 5_000L

        private const val RECONCILE_DEBOUNCE_MS = 500L

        private const val VERIFY_BLE_STOP_MS = 120L

        private const val VERIFY_BLE_MODE_MS = 450L

        private const val VERIFY_BLE_CONFIG_MS = 150L

    }



    private data class LabeledProductRow(val dto: ProductCountResultDto, val label: String)

    private enum class ProductStatusFilter {

        NOT_READ,

        INCOMPLETE,

        COMPLETE,

    }



    private lateinit var binding: ActivityHgmCountBinding

    private lateinit var bleCore: BleCore

    private lateinit var store: HgmCountSessionStore

    private lateinit var sessionId: String

    private lateinit var session: HgmCountPersistedSession

    private lateinit var expectedRfidSet: Set<String>



    private var isVerifyReading = false

    private var verifySheet: BottomSheetDialog? = null

    private var verifyConfigSnapshot: RfidParamSnapshot? = null

    private var verifyBleJob: Job? = null

    private var verifyUiRefresh: (() -> Unit)? = null

    private var verifyIsApplying = false

    private lateinit var catalogByRfid: Map<String, ExpectedInventoryItemDto>



    private val serviceUuid = UUID.fromString(com.dohealth.handheld.utils.Constants.SERVICE_UUID)

    private val writeUuid = UUID.fromString(com.dohealth.handheld.utils.Constants.WRITE_UUID)



    private var isReading = false

    private var isTriggerHoldMode = false



    private val scannedUnique = LinkedHashSet<String>()

    private val expandedHemoKeys = mutableSetOf<String>()

    private val expandedGrupoKeys = mutableSetOf<String>()

    private var reconciledProducts: List<ProductCountResultDto> = emptyList()

    private var reconcileGeneration = 0

    private var reconcileDebounceJob: Job? = null

    private var productFilterJob: Job? = null

    private var scansPersistDirty = false

    private var periodicPersistJob: Job? = null

    private var bootOverlayFinished = false

    private val errorHandler = CoroutineExceptionHandler { _, throwable ->

        reportError(throwable)

    }



    private var productSearchChoices: List<LabeledProductRow> = emptyList()

    private var productStatusFilter: ProductStatusFilter = ProductStatusFilter.NOT_READ



    private var countBootFakeProgressJob: Job? = null



    private lateinit var groupedProductAdapter: HgmGroupedProductAdapter



    private var pendingExportCsv: String? = null

    private val createExportCsvLauncher = registerForActivityResult(

        ActivityResultContracts.CreateDocument("text/csv"),

    ) { uri ->

        uri?.let { saveCsvToUri(it, pendingExportCsv) } ?: run { pendingExportCsv = null }

    }



    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        binding = ActivityHgmCountBinding.inflate(layoutInflater)

        setContentView(binding.root)



        store = HgmCountSessionStore(this)

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

        catalogByRfid = buildCatalogLookup()



        scannedUnique.clear()

        scannedUnique.addAll(session.scannedRfidsOrdered)



        bleCore = CfSdk.get(SdkC.BLE)



        setSupportActionBar(binding.toolbar)

        supportActionBar?.title = getString(R.string.hgm_physical_count_title)

        binding.toolbar.setNavigationOnClickListener {

            standbyAndFinish()

        }



        binding.countKpiPanel.sessionContextText.text = "${session.displayTitle()}\n${session.catalogFileName}"



        groupedProductAdapter = HgmGroupedProductAdapter(

            onToggleHemo = { row ->

                val k = row.group.hemocomponente

                if (expandedHemoKeys.contains(k)) expandedHemoKeys.remove(k) else expandedHemoKeys.add(k)

                applyProductFilter()

            },

            onToggleGrupo = { row ->

                val k = grupoKey(row.dto)

                if (expandedGrupoKeys.contains(k)) expandedGrupoKeys.remove(k) else expandedGrupoKeys.add(k)

                applyProductFilter()

            },

        )

        binding.productsBreakdownRecycler.layoutManager = LinearLayoutManager(this)

        binding.productsBreakdownRecycler.adapter = groupedProductAdapter

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



        binding.countKpiPanel.startReadButton.setOnClickListener {

            if (isReading) stopReading() else startReading()

        }

        binding.countKpiPanel.viewOffCatalogButton.setOnClickListener { showOffCatalogTagsDialog() }

        binding.verifyTagButton.setOnClickListener { showTagVerificationSheet() }



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

        menuInflater.inflate(R.menu.menu_hgm_count, menu)

        return true

    }



    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when (item.itemId) {
            R.id.menu_rfid_distance -> {
                showRfidDistanceDialog()
                return true
            }

            R.id.menu_export_hgm_csv -> {

                startExportExpectedVsCountedCsv()

                return true

            }

        }

        return super.onOptionsItemSelected(item)

    }

    private fun showRfidDistanceDialog() {
        RfidDistanceDialog.show(this) { preset ->
            applyRfidDistancePreset(preset)
        }
    }

    private fun applyRfidDistancePreset(preset: RfidDistancePreset) {
        val wasReading = isReading
        RfidDistancePresetHelper.apply(
            context = this,
            bleCore = bleCore,
            serviceUuid = serviceUuid,
            writeUuid = writeUuid,
            preset = preset,
            wasReading = wasReading,
        ) {
            binding.root.postDelayed({
                val inventoryCmd = CmdBuilder.buildInventoryISOContinueCmd(0x00, 0)
                bleCore.writeData(serviceUuid, writeUuid, inventoryCmd)
            }, 450)
        }
    }



    private fun grupoKey(p: ProductCountResultDto) =

        "${p.code ?: ""}\u0000${p.description ?: ""}"



    private fun refreshSessionCountLabels() {

        val expected = session.expectedItems.size

        val matched = scannedUnique.count { it in expectedRfidSet }

        val offCatalog = scannedUnique.size - matched



        binding.countKpiPanel.expectedCountText.text = getString(R.string.hgm_expected_label, expected)

        binding.countKpiPanel.scannedMatchedCountText.text = getString(R.string.hgm_matched_unique_label, matched)

        binding.countKpiPanel.scannedOffCatalogText.text = getString(R.string.hgm_off_catalog_unique_label, offCatalog)

        binding.countKpiPanel.viewOffCatalogButton.isEnabled = offCatalog > 0

    }



    private fun offCatalogRfidsOrdered(): List<String> =

        scannedUnique.filter { it !in expectedRfidSet }



    private fun buildCatalogLookup(): Map<String, ExpectedInventoryItemDto> {

        val map = LinkedHashMap<String, ExpectedInventoryItemDto>()

        session.expectedItems.forEach { item ->

            val norm = EsfericaRfidNormalizer.normalize(item.rfid ?: return@forEach) ?: return@forEach

            map[norm] = item

        }

        return map

    }



    private fun showTagVerificationSheet() {

        if (isReading) stopReading()

        if (verifySheet != null) dismissTagVerificationSheet()

        if (verifyConfigSnapshot == null) {

            val prefs = getSharedPreferences(com.dohealth.handheld.utils.Constants.PREFS_NAME, MODE_PRIVATE)

            verifyConfigSnapshot = RfidParamSnapshot.fromPrefs(prefs)

        }

        val sheetView = layoutInflater.inflate(R.layout.dialog_hgm_verify_tag, null)

        val statusText = sheetView.findViewById<android.widget.TextView>(R.id.verifyStatusText)

        val rfidText = sheetView.findViewById<android.widget.TextView>(R.id.verifyRfidHexText)

        val asciiText = sheetView.findViewById<android.widget.TextView>(R.id.verifyAsciiText)

        val catalogText = sheetView.findViewById<android.widget.TextView>(R.id.verifyCatalogText)

        val startStopButton = sheetView.findViewById<com.google.android.material.button.MaterialButton>(

            R.id.verifyStartStopButton,

        )

        fun updateVerifyButtons() {

            when {

                verifyIsApplying -> {

                    statusText.text = getString(R.string.hgm_verify_tag_applying)

                    startStopButton.isEnabled = false

                }

                isVerifyReading -> {

                    statusText.text = getString(R.string.hgm_verify_tag_running)

                    startStopButton.isEnabled = true

                    startStopButton.setText(R.string.hgm_verify_tag_stop)

                    startStopButton.backgroundTintList = ColorStateList.valueOf(

                        ContextCompat.getColor(this, R.color.warning),

                    )

                }

                else -> {

                    statusText.text = getString(R.string.hgm_verify_tag_stopped)

                    startStopButton.isEnabled = true

                    startStopButton.setText(R.string.hgm_verify_tag_start)

                    startStopButton.backgroundTintList = ColorStateList.valueOf(

                        ContextCompat.getColor(this, R.color.primary_red),

                    )

                }

            }

        }

        verifyUiRefresh = { updateVerifyButtons() }

        startStopButton.setOnClickListener {

            if (isVerifyReading) stopVerifyReading() else startVerifyReading()

        }

        updateVerifyButtons()

        verifySheet = BottomSheetDialog(this).apply {

            setContentView(sheetView)

            setOnDismissListener {

                verifySheet = null

                verifySheetUpdateViews = null

                verifyUiRefresh = null

                stopVerifyReading()

                exitVerifyConfigMode()

            }

            show()

        }

        verifySheetUpdateViews = { norm, rawHex ->

            rfidText.text = rawHex.uppercase()

            val sku = HgmSkuRfidConverter.normalizedRfidToSku(norm)

            asciiText.text = sku ?: getString(R.string.hgm_verify_tag_ascii_unknown)

            val catalogItem = catalogByRfid[norm]

            catalogText.text = if (catalogItem != null) {

                getString(

                    R.string.hgm_verify_tag_catalog_match,

                    catalogItem.code?.takeIf { it.isNotBlank() } ?: "—",

                    catalogItem.description?.takeIf { it.isNotBlank() } ?: "—",

                    catalogItem.batch?.takeIf { it.isNotBlank() } ?: "—",

                )

            } else {

                getString(R.string.hgm_verify_tag_catalog_none)

            }

        }

        startVerifyReading()

    }

    private var verifySheetUpdateViews: ((norm: String, rawHex: String) -> Unit)? = null



    private fun dismissTagVerificationSheet() {

        val sheet = verifySheet ?: return

        sheet.setOnDismissListener(null)

        sheet.dismiss()

        verifySheet = null

        verifySheetUpdateViews = null

        verifyUiRefresh = null

        stopVerifyReading()

        exitVerifyConfigMode()

    }



    private suspend fun applyVerifyZeroDbmConfig() {

        bleCore.writeData(serviceUuid, writeUuid, CmdBuilder.buildStopInventoryCmd())

        delay(VERIFY_BLE_STOP_MS)

        bleCore.writeData(serviceUuid, writeUuid, CmdBuilder.buildSetReadModeCmd(0x00, ByteArray(7)))

        delay(VERIFY_BLE_MODE_MS)

        RfidDistancePresetHelper.writePresetToDevice(

            this,

            bleCore,

            serviceUuid,

            writeUuid,

            RfidDistancePreset.ZERO,

        )

        delay(VERIFY_BLE_CONFIG_MS)

    }



    private fun exitVerifyConfigMode() {

        val snapshot = verifyConfigSnapshot ?: return

        verifyConfigSnapshot = null

        verifyBleJob?.cancel()

        lifecycleScope.launch {

            bleCore.writeData(serviceUuid, writeUuid, CmdBuilder.buildStopInventoryCmd())

            delay(VERIFY_BLE_STOP_MS)

            if (isDestroyed) return@launch

            RfidDistancePresetHelper.restoreSnapshotToDevice(

                this@HgmCountActivity,

                bleCore,

                serviceUuid,

                writeUuid,

                snapshot,

            )

        }

    }



    private fun startVerifyReading() {

        if (verifySheet == null) return

        if (isReading) stopReading()

        verifyBleJob?.cancel()

        bleCore.setOnNotifyCallback(this)

        verifyIsApplying = true

        verifyUiRefresh?.invoke()

        verifyBleJob = lifecycleScope.launch(errorHandler) {

            applyVerifyZeroDbmConfig()

            verifyIsApplying = false

            if (!isActive || verifySheet == null || isDestroyed) return@launch

            isVerifyReading = true

            bleCore.writeData(serviceUuid, writeUuid, CmdBuilder.buildInventoryISOContinueCmd(0x00, 0))

            runOnUiThread { verifyUiRefresh?.invoke() }

        }

    }



    private fun stopVerifyReading() {

        isVerifyReading = false

        verifyIsApplying = false

        verifyBleJob?.cancel()

        bleCore.writeData(serviceUuid, writeUuid, CmdBuilder.buildStopInventoryCmd())

        runOnUiThread { verifyUiRefresh?.invoke() }

    }



    private fun onVerifyTagRead(norm: String, rawHex: String) {

        verifySheetUpdateViews?.invoke(norm, rawHex)

    }



    private fun showOffCatalogTagsDialog() {

        val rfids = offCatalogRfidsOrdered()

        if (rfids.isEmpty()) {

            Toast.makeText(this, R.string.hgm_off_catalog_dialog_empty, Toast.LENGTH_SHORT).show()

            return

        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_hgm_off_catalog_list, null)

        val recycler = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(

            R.id.offCatalogTagsRecycler,

        )

        val rows = rfids.map { rfid ->

            HgmOffCatalogTagRow(

                rfid = rfid,

                sku = HgmSkuRfidConverter.normalizedRfidToSku(rfid)

                    ?: getString(R.string.hgm_off_catalog_sku_unknown),

            )

        }

        val adapter = HgmOffCatalogTagsAdapter()

        recycler.layoutManager = LinearLayoutManager(this)

        recycler.adapter = adapter

        adapter.submitList(rows)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)

            .setTitle(getString(R.string.hgm_off_catalog_dialog_title, rfids.size))

            .setView(dialogView)

            .setPositiveButton(android.R.string.ok, null)

            .show()

    }



    private fun refreshStats() {

        refreshSessionCountLabels()

        if (!bootOverlayFinished && binding.countBootOverlay.visibility == View.VISIBLE) {

            return

        }

        scheduleReconcile()

    }



    private fun scheduleReconcile() {

        reconcileDebounceJob?.cancel()

        reconcileDebounceJob = lifecycleScope.launch(errorHandler) {

            delay(RECONCILE_DEBOUNCE_MS)

            if (isDestroyed) return@launch

            reconcileProductsFromSession(showBootOverlay = false)

        }

    }



    /** Barra de carga decorativa; la conciliación corre en paralelo en segundo plano. */

    private fun reconcileProductsFromSession(showBootOverlay: Boolean) {

        countBootFakeProgressJob?.cancel()

        val showOverlay = showBootOverlay && !bootOverlayFinished

        if (!showOverlay) {

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



        binding.countBootOverlay.visibility = View.VISIBLE

        binding.countBootLoadingText.setText(R.string.hgm_loading_count_session)

        binding.countBootLinearProgress.isIndeterminate = false

        binding.countBootLinearProgress.setProgressCompat(0, false)

        binding.countBootProgressPercentText.text = "0%"



        countBootFakeProgressJob = lifecycleScope.launch {

            val products = coroutineScope {

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

                        durationMs = 1_400L,

                    )

                }

                rampDeferred.await()

                reconcileDeferred.await()

            }



            if (gen != reconcileGeneration) return@launch



            reconciledProducts = products

            refreshProductSearchAdapter()

            applyProductFilter()



            DeterminateLoadingProgress.ensureHeldAt(

                binding.countBootLinearProgress,

                binding.countBootProgressPercentText,

            )

            DeterminateLoadingProgress.flashFullProgress(

                binding.countBootLinearProgress,

                binding.countBootProgressPercentText,

            )

            bootOverlayFinished = true

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

        val products = reconciledProducts

        lifecycleScope.launch {

            val choices = withContext(Dispatchers.Default) {

                products

                    .map { LabeledProductRow(it, productSearchDisplayLabel(it)) }

                    .sortedWith(compareBy { HgmTextNormalize.forCompare(it.label) })

            }

            if (isDestroyed) return@launch

            productSearchChoices = choices

            binding.productSearchInput.setAdapter(

                ArrayAdapter(

                    this@HgmCountActivity,

                    R.layout.item_autocomplete_dropdown_line,

                    choices.map { it.label },

                ),

            )

        }

    }

    private fun updateProductCompletionSummary() {

        val groups = HgmProductGrouping.buildGroups(reconciledProducts)

        val total = groups.count { it.expectedQty > 0 }

        val completed = groups.count { g ->

            g.expectedQty > 0 && g.scannedQty >= g.expectedQty

        }

        binding.productCompletionSummaryText.text =

            getString(R.string.hgm_completed_codes_summary, completed, total)

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

        productFilterJob?.cancel()

        val raw = binding.productSearchInput.text?.toString()?.trim().orEmpty()

        val statusFilter = productStatusFilter

        val products = reconciledProducts

        val choices = productSearchChoices

        val hemoExpanded = expandedHemoKeys.toSet()

        val grupoExpanded = expandedGrupoKeys.toSet()

        productFilterJob = lifecycleScope.launch {

            val rows = withContext(Dispatchers.Default) {

                buildGroupedDisplayRows(products, choices, raw, statusFilter, hemoExpanded, grupoExpanded)

            }

            if (isDestroyed) return@launch

            groupedProductAdapter.submitList(rows)

        }

    }



    private fun buildGroupedDisplayRows(

        products: List<ProductCountResultDto>,

        choices: List<LabeledProductRow>,

        rawQuery: String,

        statusFilter: ProductStatusFilter,

        hemoExpanded: Set<String>,

        grupoExpanded: Set<String>,

    ): List<HgmDisplayRow> {

        val exactChoice = choices.find {

            HgmTextNormalize.forCompare(it.label) == HgmTextNormalize.forCompare(rawQuery)

        }?.dto

        val base = when {

            rawQuery.isBlank() -> products

            exactChoice != null -> listOf(exactChoice)

            else -> products.filter { p ->

                HgmTextNormalize.containsInsensitive(p.code, rawQuery) ||

                    HgmTextNormalize.containsInsensitive(p.description, rawQuery)

            }

        }

        val filtered = when (statusFilter) {

            ProductStatusFilter.NOT_READ -> base.filter { it.scannedQty <= 0 }

            ProductStatusFilter.INCOMPLETE -> base.filter {

                val exp = it.expectedQty

                val scn = it.scannedQty

                exp > 0 && scn in 1 until exp

            }

            ProductStatusFilter.COMPLETE -> base.filter {

                val exp = it.expectedQty

                val scn = it.scannedQty

                exp > 0 && scn >= exp

            }

        }

        val rows = mutableListOf<HgmDisplayRow>()

        for (g in HgmProductGrouping.buildGroups(filtered)) {

            val hemoExp = hemoExpanded.contains(g.hemocomponente)

            rows.add(HgmDisplayRow.HemocomponenteHeader(g, hemoExp))

            if (hemoExp) {

                for (grupo in g.grupos) {

                    rows.add(

                        HgmDisplayRow.GrupoSubgroup(

                            grupo,

                            grupoExpanded.contains(grupoKey(grupo)),

                        ),

                    )

                }

            }

        }

        return rows

    }



    private fun onNewScan() {

        scansPersistDirty = true

        refreshSessionCountLabels()

        if (!bootOverlayFinished && binding.countBootOverlay.visibility == View.VISIBLE) {

            return

        }

        scheduleReconcile()

    }



    private fun flushPersistToStore() {

        if (!scansPersistDirty) return

        val scannedNow = scannedUnique.toList()

        session = session.copy(scannedRfidsOrdered = scannedNow)

        scansPersistDirty = false

        lifecycleScope.launch(Dispatchers.IO) {

            kotlin.runCatching { store.updateScanned(sessionId, scannedNow) }

                .onFailure { reportError(it) }

        }

    }



    private fun startPeriodicPersist() {

        periodicPersistJob?.cancel()

        periodicPersistJob = lifecycleScope.launch {

            while (isActive && isReading) {

                delay(PERSIST_INTERVAL_MS)

                if (scansPersistDirty) flushPersistToStore()

            }

        }

    }



    private fun stopPeriodicPersist() {

        periodicPersistJob?.cancel()

        periodicPersistJob = null

    }

    private fun reportError(t: Throwable) {

        val msg = (t.message ?: t.javaClass.simpleName).take(180)

        runOnUiThread {

            Toast.makeText(

                this@HgmCountActivity,

                getString(R.string.hgm_read_error, msg),

                Toast.LENGTH_LONG,

            ).show()

            binding.countKpiPanel.statusReadingText.text =

                "${binding.countKpiPanel.statusReadingText.text}\n${getString(R.string.hgm_read_error_inline, msg)}"

        }

    }



    private fun startExportExpectedVsCountedCsv() {

        lifecycleScope.launch(Dispatchers.Default) {

            val exportSession = session.copy(scannedRfidsOrdered = scannedUnique.toList())

            val csv =

                runCatching { HgmExport.buildExpectedVsValidCountedCsv(exportSession) }.getOrNull()

            withContext(Dispatchers.Main) {

                if (csv == null || csv.isBlank()) {

                    Toast.makeText(this@HgmCountActivity, R.string.export_error, Toast.LENGTH_SHORT).show()

                } else {

                    pendingExportCsv = csv

                    val name =

                        "hgm_conteo_${System.currentTimeMillis()}.csv"

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

                Toast.makeText(this@HgmCountActivity, R.string.export_success, Toast.LENGTH_SHORT).show()

            }

        }

    }



    private fun updateReadButtons() {

        binding.countKpiPanel.startReadButton.visibility = View.VISIBLE

        if (isReading) {

            binding.countKpiPanel.statusReadingText.text = getString(R.string.hgm_reading_running)

            binding.countKpiPanel.startReadButton.setText(R.string.hgm_stop_reading)

            binding.countKpiPanel.startReadButton.backgroundTintList = ColorStateList.valueOf(

                ContextCompat.getColor(this, R.color.warning),

            )

        } else {

            binding.countKpiPanel.statusReadingText.text = getString(R.string.hgm_reading_stopped)

            binding.countKpiPanel.startReadButton.setText(R.string.hgm_start_reading)

            binding.countKpiPanel.startReadButton.backgroundTintList = ColorStateList.valueOf(

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

        if (verifyConfigSnapshot != null) {

            dismissTagVerificationSheet()

        }

        if (isVerifyReading) stopVerifyReading()

        bleCore.setOnNotifyCallback(this)

        setDeviceModeRfid()

        RfidDistancePresetHelper.syncSavedPresetToDevice(this, bleCore, serviceUuid, writeUuid)

        isReading = true

        updateReadButtons()

        startPeriodicPersist()

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

        stopPeriodicPersist()

        flushPersistToStore()

        updateReadButtons()

    }



    private fun standbyAndFinish() {

        flushPersistToStore()

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

        if (isVerifyReading) return

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

        if (isVerifyReading) {

            runOnUiThread { onVerifyTagRead(norm, epcHex) }

            return

        }

        if (!scannedUnique.add(norm)) return

        runOnUiThread {

            kotlin.runCatching { onNewScan() }.onFailure { reportError(it) }

        }

    }



    override fun onResume() {

        super.onResume()

        bleCore.setOnNotifyCallback(this)

        checkTriggerHoldMode()

        if (verifySheet != null) {

            startVerifyReading()

        } else {

            setDeviceModeRfid()

        }

    }



    override fun onPause() {

        super.onPause()

        if (verifySheet != null) stopVerifyReading()

        if (isReading) stopReading()

    }



    override fun onDestroy() {

        super.onDestroy()

        reconcileGeneration++

        countBootFakeProgressJob?.cancel()

        productFilterJob?.cancel()

        stopPeriodicPersist()

        flushPersistToStore()

        if (::binding.isInitialized) {

            binding.countBootOverlay.visibility = View.GONE

        }

        verifyBleJob?.cancel()

        if (verifyConfigSnapshot != null) exitVerifyConfigMode()

        dismissTagVerificationSheet()

        if (isReading) {

            bleCore.writeData(serviceUuid, writeUuid, CmdBuilder.buildStopInventoryCmd())

            isReading = false

        }

        bleCore.setOnNotifyCallback(null)

    }

}


