package com.dohealth.handheld.ui.relacion

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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
import com.dohealth.handheld.data.relacion.RelacionItem
import com.dohealth.handheld.data.relacion.RelacionSession
import com.dohealth.handheld.data.relacion.RelacionSessionStore
import com.dohealth.handheld.databinding.ActivityRelacionPorCodigosBinding
import com.dohealth.handheld.data.relacion.RelacionSecondReadMode
import com.dohealth.handheld.utils.Constants
import com.dohealth.handheld.utils.buildAllParamBeanFromPrefs
import com.dohealth.handheld.utils.decodeBarcodeAscii
import java.util.Locale
import java.util.UUID

class RelacionPorCodigosActivity : AppCompatActivity(), IOnNotifyCallback {

    private lateinit var binding: ActivityRelacionPorCodigosBinding
    private lateinit var store: RelacionSessionStore
    private lateinit var session: RelacionSession

    private lateinit var bleCore: BleCore
    private val serviceUuid = UUID.fromString(Constants.SERVICE_UUID)
    private val writeUuid = UUID.fromString(Constants.WRITE_UUID)

    private lateinit var adapter: RelacionItemsAdapter

    private var isRunning = false
    /** Igual que InventoryActivity: con work_mode distinto de 2, gatillo presionado = iniciar, soltar = detener. */
    private var isTriggerHoldMode = false
    private val recentReads = mutableMapOf<String, Long>()
    private val DEDUP_MS = 500L

    /** Potencia RF mínima (dBm) mientras el paso 2 es RFID en este módulo. */
    private val RELACION_MIN_RFID_POWER_DBM = 0
    /** Separación mínima entre aceptar dos lecturas RFID consecutivas. */
    private val RFID_READ_GAP_MS = 2200L
    /** Tras poner modo código de barras, espera antes de ISO continue (ms). */
    private val MODE_SETTLE_BARCODE_MS = 550L
    /**
     * Tras lectura RFID el hardware a veces vuelve al scanhead; más tiempo estabiliza antes del siguiente inventario UHF.
     */
    private val MODE_SETTLE_RFID_MS = 900L

    private var rfidPowerRestoreLevel: Int? = null
    private var lastRfidAcceptedAt = 0L

    private val exportTxtLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri == null) return@registerForActivityResult
            try {
                val text = buildExportTxt()
                contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(text.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(this, R.string.relacion_export_success, Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(this, R.string.relacion_export_error, Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRelacionPorCodigosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = RelacionSessionStore(this)
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
        session = sessionId?.let { store.getSession(it) } ?: store.createSession()

        bleCore = CfSdk.get(SdkC.BLE)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        supportActionBar?.title = getString(R.string.module_relacion_codigos)

        adapter = RelacionItemsAdapter { item -> showRelationRowMenu(item) }
        binding.relationsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.relationsRecyclerView.adapter = adapter

        binding.keepProductSwitch.isChecked = session.keepProduct
        binding.keepProductSwitch.setOnCheckedChangeListener { _, isChecked ->
            session = session.copy(keepProduct = isChecked)
            persist()
            render()
        }

        binding.secondReadModeGroup.setOnCheckedChangeListener(null)
        when (session.secondReadMode) {
            RelacionSecondReadMode.BARCODE -> binding.radioSecondBarcode.isChecked = true
            else -> binding.radioSecondRfid.isChecked = true
        }
        binding.secondReadModeGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.radioSecondBarcode -> RelacionSecondReadMode.BARCODE
                else -> RelacionSecondReadMode.RFID
            }
            if (session.secondReadMode == mode) return@setOnCheckedChangeListener
            session = session.copy(secondReadMode = mode)
            persist()
            render()
            applyReadModeForCurrentStep()
        }

        binding.clearProductButton.setOnClickListener {
            session = session.copy(currentProductCode = null)
            persist()
            render()
            applyReadModeForCurrentStep()
        }

        binding.startStopButton.setOnClickListener {
            if (isRunning) stop() else start()
        }

        binding.exportTxtButton.setOnClickListener {
            val fileName = "relacion_${session.id}.txt"
            exportTxtLauncher.launch(fileName)
        }

        checkTriggerHoldMode()
        render()
    }

    override fun onResume() {
        super.onResume()
        bleCore.setOnNotifyCallback(this)
        checkTriggerHoldMode()
        // Igual que InventoryActivity en modo código de barras: al volver, el modo del lector
        // debe coincidir con lo que vamos a leer (producto = scanhead 0x01), no dejarlo en RFID.
        applyReadModeForCurrentStep()
    }

    override fun onPause() {
        super.onPause()
        stop()
        setDeviceToStandby()
        persist()
    }

    override fun onDestroy() {
        leaveRfidLowPowerIfNeeded()
        super.onDestroy()
        bleCore.setOnNotifyCallback(null)
    }

    private fun render() {
        binding.sessionNameText.text = session.name
        binding.relationsCountText.text = getString(R.string.relacion_relations_count, session.relations.size)

        val product = session.currentProductCode
        if (product.isNullOrBlank()) {
            binding.stepText.text = getString(R.string.relacion_step_product)
            binding.currentProductLabel.visibility = View.GONE
            binding.currentProductValue.visibility = View.GONE
            binding.clearProductButton.visibility = View.GONE
        } else {
            binding.stepText.text = when (session.secondReadMode) {
                RelacionSecondReadMode.BARCODE -> getString(R.string.relacion_step_second_barcode)
                else -> getString(R.string.relacion_step_rfid)
            }
            binding.currentProductLabel.visibility = View.VISIBLE
            binding.currentProductValue.visibility = View.VISIBLE
            binding.clearProductButton.visibility = View.VISIBLE
            binding.currentProductValue.text = product
        }

        binding.startStopButton.text = if (isRunning) getString(R.string.relacion_stop) else getString(R.string.relacion_start)

        adapter.submitList(session.relations.sortedByDescending { it.timestamp })
        binding.emptyListText.visibility = if (session.relations.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun persist() {
        store.saveSession(session)
    }

    private fun checkTriggerHoldMode() {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        val workMode = prefs.getInt(Constants.KEY_WORK_MODE, Constants.DEFAULT_WORK_MODE)
        isTriggerHoldMode = workMode != 2
    }

    /**
     * En modo Trigger nativo (work_mode=2) el equipo suele escanear con el gatillo sin depender del flag
     * [isRunning]; InventoryActivity tampoco filtra lecturas por "running". Aquí aceptamos datos en ese caso.
     */
    private fun canReceiveReads(): Boolean {
        if (isRunning) return true
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        val workMode = prefs.getInt(Constants.KEY_WORK_MODE, Constants.DEFAULT_WORK_MODE)
        return workMode == 2
    }

    private fun isNativeTriggerWorkMode(): Boolean {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        return prefs.getInt(Constants.KEY_WORK_MODE, Constants.DEFAULT_WORK_MODE) == 2
    }

    private fun start() {
        if (isRunning) return
        isRunning = true
        bleCore.setOnNotifyCallback(this)
        render()
        continueScan()
    }

    private fun stop() {
        if (!isRunning) return
        isRunning = false
        val stopCmd = CmdBuilder.buildStopInventoryCmd()
        bleCore.writeData(serviceUuid, writeUuid, stopCmd)
        render()
    }

    private fun continueScan() {
        if (!isRunning && !isNativeTriggerWorkMode()) return

        val product = session.currentProductCode
        if (product.isNullOrBlank()) {
            leaveRfidLowPowerIfNeeded()
            setReadModeBarcode()
            binding.root.postDelayed({
                if (!isRunning && !isNativeTriggerWorkMode()) return@postDelayed
                val scanCmd = CmdBuilder.buildInventoryISOContinueCmd(0x01.toByte(), 0x01)
                bleCore.writeData(serviceUuid, writeUuid, scanCmd)
            }, MODE_SETTLE_BARCODE_MS)
        } else if (session.secondReadMode == RelacionSecondReadMode.BARCODE) {
            leaveRfidLowPowerIfNeeded()
            setReadModeBarcode()
            binding.root.postDelayed({
                if (!isRunning && !isNativeTriggerWorkMode()) return@postDelayed
                val scanCmd = CmdBuilder.buildInventoryISOContinueCmd(0x01.toByte(), 0x01)
                bleCore.writeData(serviceUuid, writeUuid, scanCmd)
            }, MODE_SETTLE_BARCODE_MS)
        } else {
            enterRfidLowPowerIfNeeded()
            setReadModeRfid()
            binding.root.postDelayed({
                if (!isRunning && !isNativeTriggerWorkMode()) return@postDelayed
                setReadModeRfid()
                binding.root.postDelayed({
                    if (!isRunning && !isNativeTriggerWorkMode()) return@postDelayed
                    val inventoryCmd = CmdBuilder.buildInventoryISOContinueCmd(0x00, 0)
                    bleCore.writeData(serviceUuid, writeUuid, inventoryCmd)
                }, 200)
            }, MODE_SETTLE_RFID_MS)
        }
    }

    private fun setReadModeBarcode() {
        val cmd = CmdBuilder.buildSetReadModeCmd(0x01.toByte(), ByteArray(7))
        bleCore.writeData(serviceUuid, writeUuid, cmd)
    }

    private fun setReadModeRfid() {
        val cmd = CmdBuilder.buildSetReadModeCmd(0x00.toByte(), ByteArray(7))
        bleCore.writeData(serviceUuid, writeUuid, cmd)
    }

    /** Detiene inventario y deja el lector en el modo adecuado al paso actual (como InventoryActivity.setDeviceMode). */
    private fun applyReadModeForCurrentStep() {
        val stopCmd = CmdBuilder.buildStopInventoryCmd()
        bleCore.writeData(serviceUuid, writeUuid, stopCmd)
        applyHardwareReadModeForCurrentStepWithoutStop()
    }

    /** Sin enviar stop: reaplica scanhead / UHF según sesión (útil justo tras una lectura RFID). */
    private fun applyHardwareReadModeForCurrentStepWithoutStop() {
        val product = session.currentProductCode
        when {
            product.isNullOrBlank() -> {
                leaveRfidLowPowerIfNeeded()
                setReadModeBarcode()
            }
            session.secondReadMode == RelacionSecondReadMode.BARCODE -> {
                leaveRfidLowPowerIfNeeded()
                setReadModeBarcode()
            }
            else -> {
                enterRfidLowPowerIfNeeded()
                setReadModeRfid()
            }
        }
    }

    private fun enterRfidLowPowerIfNeeded() {
        if (session.secondReadMode != RelacionSecondReadMode.RFID) return
        if (session.currentProductCode.isNullOrBlank()) return
        if (rfidPowerRestoreLevel != null) return
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        rfidPowerRestoreLevel = prefs.getInt(Constants.KEY_POWER_LEVEL, Constants.DEFAULT_POWER_LEVEL)
        val bean = buildAllParamBeanFromPrefs(prefs, RELACION_MIN_RFID_POWER_DBM)
        val cmd = CmdBuilder.buildSetAllParamCmd(bean)
        bleCore.writeData(serviceUuid, writeUuid, cmd)
    }

    private fun leaveRfidLowPowerIfNeeded() {
        val restore = rfidPowerRestoreLevel ?: return
        rfidPowerRestoreLevel = null
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        val bean = buildAllParamBeanFromPrefs(prefs, restore)
        val cmd = CmdBuilder.buildSetAllParamCmd(bean)
        bleCore.writeData(serviceUuid, writeUuid, cmd)
    }

    /** Al salir de la pantalla: mismo standby que inventario (RFID + detenido). */
    private fun setDeviceToStandby() {
        leaveRfidLowPowerIfNeeded()
        val stopCmd = CmdBuilder.buildStopInventoryCmd()
        bleCore.writeData(serviceUuid, writeUuid, stopCmd)
        val rfidModeCmd = CmdBuilder.buildSetReadModeCmd(0x00, ByteArray(7))
        bleCore.writeData(serviceUuid, writeUuid, rfidModeCmd)
    }

    override fun onNotify(cmdType: Int, cmdData: CmdData) {
        when (cmdType) {
            CmdType.TYPE_KEY_STATE -> {
                handleTriggerKeyState(cmdData)
                return
            }
            CmdType.TYPE_INVENTORY -> {
                if (!canReceiveReads()) return
                val product = session.currentProductCode
                if (product.isNullOrBlank()) {
                    handleBarcodeFromInventory(cmdData)
                } else if (session.secondReadMode == RelacionSecondReadMode.BARCODE) {
                    handleSecondBarcodeFromInventory(cmdData)
                } else if (session.secondReadMode == RelacionSecondReadMode.RFID) {
                    handleRfid(cmdData)
                }
            }
        }
    }

    override fun onNotify(bytes: ByteArray) {
        if (!canReceiveReads()) return
        val product = session.currentProductCode
        val barcodeData = cleanBarcodeData(bytes)

        when {
            product.isNullOrBlank() -> {
                if (barcodeData.isEmpty() || !isValidBarcode(barcodeData)) return
                val currentTime = System.currentTimeMillis()
                val lastReadTime = recentReads[barcodeData]
                if (lastReadTime != null && (currentTime - lastReadTime) < DEDUP_MS) return
                recentReads[barcodeData] = currentTime
                recentReads.entries.removeAll { it.value < currentTime - 5000L }
                runOnUiThread { onProductRead(barcodeData) }
            }
            session.secondReadMode == RelacionSecondReadMode.BARCODE -> {
                val hexOnly = extractHexDigitsOnly(barcodeData)
                if (hexOnly.length < MIN_SECOND_HEX_LEN) return
                val currentTime = System.currentTimeMillis()
                val lastReadTime = recentReads[hexOnly]
                if (lastReadTime != null && (currentTime - lastReadTime) < DEDUP_MS) return
                recentReads[hexOnly] = currentTime
                recentReads.entries.removeAll { it.value < currentTime - 5000L }
                runOnUiThread { commitRelation(hexOnly, secondAsBarcode = true) }
            }
            else -> { /* paso 2 solo RFID vía TYPE_INVENTORY */ }
        }
    }

    /** Misma lógica que InventoryActivity.handleBarcodeFromInventory (modo código de barras). */
    private fun handleBarcodeFromInventory(cmdData: CmdData) {
        val tagInfo = cmdData.getData() as? TagInfoBean ?: return
        if (tagInfo.mStatus != 0x00) return
        if (tagInfo.mEPCNum == null || tagInfo.mEPCNum.isEmpty()) return

        val barcodeData = cleanBarcodeData(tagInfo.mEPCNum)
        if (barcodeData.isEmpty() || !isValidBarcode(barcodeData)) return

        val currentTime = System.currentTimeMillis()
        val lastReadTime = recentReads[barcodeData]
        if (lastReadTime != null && (currentTime - lastReadTime) < DEDUP_MS) return
        recentReads[barcodeData] = currentTime
        val fiveSecondsAgo = currentTime - 5000L
        recentReads.entries.removeAll { it.value < fiveSecondsAgo }

        runOnUiThread { onProductRead(barcodeData) }
    }

    private fun handleSecondBarcodeFromInventory(cmdData: CmdData) {
        if (session.secondReadMode != RelacionSecondReadMode.BARCODE) return
        val tagInfo = cmdData.getData() as? TagInfoBean ?: return
        if (tagInfo.mStatus != 0x00) return
        if (tagInfo.mEPCNum == null || tagInfo.mEPCNum.isEmpty()) return

        val raw = cleanBarcodeData(tagInfo.mEPCNum)
        val hexOnly = extractHexDigitsOnly(raw)
        if (hexOnly.length < MIN_SECOND_HEX_LEN) return

        val currentTime = System.currentTimeMillis()
        val lastReadTime = recentReads[hexOnly]
        if (lastReadTime != null && (currentTime - lastReadTime) < DEDUP_MS) return
        recentReads[hexOnly] = currentTime
        val fiveSecondsAgo = currentTime - 5000L
        recentReads.entries.removeAll { it.value < fiveSecondsAgo }

        runOnUiThread { commitRelation(hexOnly, secondAsBarcode = true) }
    }

    private fun handleTriggerKeyState(cmdData: CmdData) {
        if (!isTriggerHoldMode) return
        val keyState = cmdData.getData() as? KeyStateBean ?: return
        val keyStateValue = keyState.mKeyState.toInt()
        runOnUiThread {
            when (keyStateValue) {
                0x01 -> {
                    if (!isRunning) {
                        start()
                    }
                }
                0x02 -> {
                    if (isRunning) {
                        stop()
                    }
                }
            }
        }
    }

    private fun onProductRead(code: String) {
        if (!canReceiveReads()) return

        session = session.copy(currentProductCode = code)
        persist()
        render()
        continueScan()
    }

    private fun normalizeRfidKey(hex: String): String =
        hex.replace(" ", "").uppercase(Locale.ROOT)

    /** True si otro ítem ya usa este RFID (EPC hex), ignorando el ítem con [excludeItemId]. */
    private fun isRfidCodeDuplicate(secondCode: String, excludeItemId: String? = null): Boolean {
        val key = normalizeRfidKey(secondCode)
        if (key.isEmpty()) return false
        return session.relations.any { rel ->
            rel.id != excludeItemId &&
                normalizeRfidKey(rel.rfidCode) == key
        }
    }

    /** Solo dígitos 0-9A-F; mayúsculas. Cualquier otro carácter se descarta. */
    private fun extractHexDigitsOnly(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            when (c) {
                in '0'..'9', in 'a'..'f' -> sb.append(c.uppercaseChar())
                in 'A'..'F' -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun showEditRelationDialog(item: RelacionItem) {
        val pad = (resources.displayMetrics.density * 16).toInt()
        val edit = EditText(this).apply {
            setText(item.rfidCode)
            hint = getString(R.string.relacion_edit_second_hint)
            setPadding(pad, pad, pad, pad)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.relacion_edit_second_title))
            .setView(edit)
            .setPositiveButton(R.string.ok) { d, _ ->
                val raw = edit.text.toString()
                val newVal = extractHexDigitsOnly(raw)
                when {
                    newVal.isEmpty() ->
                        Toast.makeText(
                            this,
                            R.string.relacion_second_hex_invalid,
                            Toast.LENGTH_SHORT
                        ).show()
                    newVal.length < MIN_SECOND_HEX_LEN ->
                        Toast.makeText(this, R.string.relacion_second_hex_invalid, Toast.LENGTH_SHORT).show()
                    isRfidCodeDuplicate(newVal, excludeItemId = item.id) ->
                        Toast.makeText(this, R.string.relacion_rfid_duplicate, Toast.LENGTH_SHORT).show()
                    else -> {
                        session = session.copy(
                            relations = session.relations.map { rel ->
                                if (rel.id == item.id) {
                                    rel.copy(
                                        rfidCode = newVal,
                                        timestamp = System.currentTimeMillis(),
                                        secondAsBarcode = false
                                    )
                                } else {
                                    rel
                                }
                            }
                        )
                        persist()
                        render()
                        if (isRunning) continueScan()
                    }
                }
                d.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showRelationRowMenu(item: RelacionItem) {
        val options = arrayOf(
            getString(R.string.relacion_edit),
            getString(R.string.relacion_delete)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.relacion_row_menu_title)
            .setItems(options) { d, which ->
                when (which) {
                    0 -> showEditRelationDialog(item)
                    1 -> confirmDeleteSingleRelation(item)
                }
                d.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteSingleRelation(item: RelacionItem) {
        AlertDialog.Builder(this)
            .setMessage(R.string.relacion_delete_one_confirm)
            .setPositiveButton(R.string.relacion_delete) { d, _ ->
                session = session.copy(relations = session.relations.filter { it.id != item.id })
                persist()
                render()
                if (isRunning) continueScan()
                d.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun handleRfid(cmdData: CmdData) {
        if (session.secondReadMode != RelacionSecondReadMode.RFID) return
        val tagInfo = cmdData.getData() as? TagInfoBean ?: return
        if (tagInfo.mStatus != 0x00) return
        val epcBytes = tagInfo.mEPCNum ?: return
        val epcHex = FormatUtil.bytesToHexStr(epcBytes).replace(" ", "")
        if (epcHex.isBlank()) {
            runOnUiThread {
                Toast.makeText(this, getString(R.string.relacion_invalid_rfid), Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (session.currentProductCode.isNullOrBlank()) return

        runOnUiThread { commitRelation(epcHex, secondAsBarcode = false) }
    }

    private fun commitRelation(secondCode: String, secondAsBarcode: Boolean) {
        if (!canReceiveReads()) return
        val product = session.currentProductCode ?: return
        val secondStored = if (secondAsBarcode) {
            val h = extractHexDigitsOnly(secondCode)
            if (h.length < MIN_SECOND_HEX_LEN) return
            h
        } else {
            if (secondCode.isBlank()) return
            secondCode
        }
        if (!secondAsBarcode) {
            val now = System.currentTimeMillis()
            if (now - lastRfidAcceptedAt < RFID_READ_GAP_MS) return
        }
        if (isRfidCodeDuplicate(secondStored)) {
            Toast.makeText(this, R.string.relacion_rfid_duplicate, Toast.LENGTH_SHORT).show()
            val stopCmd = CmdBuilder.buildStopInventoryCmd()
            bleCore.writeData(serviceUuid, writeUuid, stopCmd)
            applyHardwareReadModeForCurrentStepWithoutStop()
            binding.root.postDelayed({ continueScan() }, RFID_READ_GAP_MS)
            return
        }

        val item = RelacionItem(
            id = UUID.randomUUID().toString(),
            productCode = product,
            rfidCode = secondStored,
            timestamp = System.currentTimeMillis(),
            secondAsBarcode = false
        )
        session = session.copy(
            relations = session.relations + item,
            currentProductCode = if (session.keepProduct) product else null
        )
        persist()
        render()

        val stopCmd = CmdBuilder.buildStopInventoryCmd()
        bleCore.writeData(serviceUuid, writeUuid, stopCmd)
        applyHardwareReadModeForCurrentStepWithoutStop()
        if (!secondAsBarcode) {
            lastRfidAcceptedAt = System.currentTimeMillis()
        }
        val gapMs = if (secondAsBarcode) 400L else RFID_READ_GAP_MS
        binding.root.postDelayed({ continueScan() }, gapMs)
    }

    private fun cleanBarcodeData(bytes: ByteArray): String = decodeBarcodeAscii(bytes)

    /** Copiado de InventoryActivity: filtra basura del protocolo que no es código de barras real. */
    private fun isValidBarcode(barcode: String): Boolean {
        val errorKeywords = listOf(
            "error", "fail", "failed", "error en", "error de", "fallo", "falló",
            "no se", "no se pudo", "no se encontró", "sin", "vacío", "empty",
            "invalid", "inválido", "desconocido", "unknown", "执行成功", "没有盘点到",
            "参数值错误", "内部错误", "超时", "认证失败", "口令错误"
        )
        val barcodeLower = barcode.lowercase()
        if (errorKeywords.any { barcodeLower.contains(it) }) return false
        if (barcode.length < 2) return false
        if (barcode.all { it.isWhitespace() || it.isISOControl() }) return false
        if (barcode.contains('\uFFFD')) return false
        if (barcode.matches(Regex("^[0-9a-fA-F]{1,4}$"))) return false
        val invalidCharCount = barcode.count {
            it.isISOControl() ||
                it == '\uFFFD' ||
                it.isSurrogate() ||
                !Character.isDefined(it.code)
        }
        if (invalidCharCount > barcode.length / 2) return false
        return true
    }

    companion object {
        const val EXTRA_SESSION_ID = "relacion_session_id"
        /** Mínimo de dígitos hex tras filtrar el segundo código (etiqueta impresa). */
        private const val MIN_SECOND_HEX_LEN = 4
    }

    private fun buildExportTxt(): String {
        val sb = StringBuilder()
        sb.append("PRODUCTO\tRFID\n")
        session.relations
            .sortedBy { it.timestamp }
            .forEach { rel ->
                sb.append(rel.productCode)
                sb.append('\t')
                sb.append(rel.rfidCode)
                sb.append('\n')
            }
        return sb.toString()
    }
}

