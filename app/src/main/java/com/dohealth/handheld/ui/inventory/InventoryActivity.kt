package com.dohealth.handheld.ui.inventory

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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
import com.dohealth.handheld.databinding.ActivityInventoryBinding
import com.dohealth.handheld.data.history.ScanHistoryStore
import com.dohealth.handheld.data.inventory.InventorySession
import com.dohealth.handheld.data.inventory.InventorySessionStore
import com.dohealth.handheld.data.model.InventoryItem
import com.dohealth.handheld.ui.history.ScanHistoryActivity
import com.dohealth.handheld.utils.Constants
import com.dohealth.handheld.utils.TextExporter
import com.dohealth.handheld.utils.ApiService
import kotlinx.coroutines.launch
import java.util.UUID

class InventoryActivity : AppCompatActivity(), IOnNotifyCallback {
    
    companion object {
        const val MODE_RFID = 0x00
        const val MODE_BARCODE = 0x01
        const val EXTRA_SESSION_ID = "inventory_session_id"
    }
    
    private lateinit var binding: ActivityInventoryBinding
    private lateinit var bleCore: BleCore
    private lateinit var inventoryAdapter: InventoryAdapter
    private var mode: Int = MODE_RFID
    private var isInventoryRunning = false
    private var isTriggerHoldMode = false // Modo trigger hold (presionar para activar, soltar para desactivar)
    private val allInventoryItems = mutableListOf<InventoryItem>() // Todas las lecturas
    private val uniqueInventoryItems = mutableMapOf<String, InventoryItem>() // Items únicos con contador
    private val serviceUuid = UUID.fromString(Constants.SERVICE_UUID)
    private val writeUuid = UUID.fromString(Constants.WRITE_UUID)
    private var pendingExportContent: String? = null
    private lateinit var historyStore: ScanHistoryStore
    private lateinit var sessionStore: InventorySessionStore
    private var session: InventorySession? = null
    // Deduplicación temporal: código -> timestamp de última lectura
    private val recentBarcodeReads = mutableMapOf<String, Long>()
    private val BARCODE_DEDUP_TIME_MS = 500L // No insertar el mismo código si se leyó hace menos de 500ms (evitar spam)
    
    // Launcher para crear documento (selector de archivos)
    private val createDocumentLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let {
            saveContentToUri(it)
        } ?: run {
            binding.progressBar.visibility = android.view.View.GONE
            Toast.makeText(this, "Exportación cancelada", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInventoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        mode = intent.getIntExtra("mode", MODE_RFID)
        
        bleCore = CfSdk.get(SdkC.BLE)
        historyStore = ScanHistoryStore(this)
        sessionStore = InventorySessionStore(this)
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
        session = sessionId?.let { sessionStore.getSession(it) }
        setupRecyclerView()
        setupClickListeners()
        setupUI()
        
        // Si venimos con sesión, restaurar lecturas
        session?.let { restoreFromSession(it) }
        
        // Inicializar contadores con el texto correcto según el modo y empezar en 0
        initializeCounts()
        
        // Inicializar estado del botón
        updateStartStopButton()
        
        // Configurar modo del dispositivo
        setDeviceMode()
        
        // Verificar si debe usar modo trigger hold basado en la configuración
        checkTriggerHoldMode()
    }

    private fun restoreFromSession(s: InventorySession) {
        allInventoryItems.clear()
        uniqueInventoryItems.clear()
        recentBarcodeReads.clear()

        allInventoryItems.addAll(s.items)
        for (it in s.items) {
            val existing = uniqueInventoryItems[it.data]
            if (existing != null) {
                uniqueInventoryItems[it.data] = existing.copy(
                    readCount = existing.readCount + it.readCount,
                    timestamp = maxOf(existing.timestamp, it.timestamp)
                )
            } else {
                uniqueInventoryItems[it.data] = it
            }
        }
        updateInventoryList()
    }

    private fun persistSessionIfAny() {
        val s = session ?: return
        session = s.copy(items = allInventoryItems.toList())
        session?.let { sessionStore.saveSession(it) }
    }
    
    private fun checkTriggerHoldMode() {
        // Verificar si el modo de trabajo es Trigger (2)
        // Si es así, NO usar modo hold manual porque el SDK maneja el toggle automáticamente
        // Solo usar modo hold si el modo de trabajo es Respuesta (0) o Activo (1)
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        val workMode = prefs.getInt(Constants.KEY_WORK_MODE, Constants.DEFAULT_WORK_MODE)
        
        // Modo hold solo funciona si NO estamos en modo Trigger nativo
        isTriggerHoldMode = (workMode != 2)
        
        android.util.Log.d("InventoryActivity", "Modo trigger hold: $isTriggerHoldMode (WorkMode: $workMode)")
        
        if (isTriggerHoldMode) {
            android.util.Log.d("InventoryActivity", "Modo hold activado: Presionar gatillo para iniciar, soltar para detener")
        } else {
            android.util.Log.d("InventoryActivity", "Modo trigger nativo: Presionar gatillo para toggle on/off")
        }
    }
    
    private fun setupUI() {
        // Configurar toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_arrow_back)
        supportActionBar?.title = if (mode == MODE_RFID) {
            getString(R.string.mode_rfid)
        } else {
            getString(R.string.mode_barcode)
        }
        
        // Configurar click listener del toolbar
        binding.toolbar.setNavigationOnClickListener {
            handleBackPress()
        }
        
        binding.modeText.text = if (mode == MODE_RFID) {
            "Modo: RFID"
        } else {
            "Modo: Código de Barras"
        }
    }
    
    private fun setupRecyclerView() {
        inventoryAdapter = InventoryAdapter()
        binding.itemsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.itemsRecyclerView.adapter = inventoryAdapter
    }
    
    private fun setupClickListeners() {
        binding.startStopButton.setOnClickListener {
            if (isInventoryRunning) {
                stopInventory()
            } else {
                startInventory()
            }
        }
        
        binding.reloadButton.setOnClickListener {
            clearData()
        }
    }
    
    private fun updateStartStopButton() {
        if (isInventoryRunning) {
            binding.startStopButton.text = getString(R.string.stop_inventory)
            binding.startStopButton.backgroundTintList = android.content.res.ColorStateList.valueOf(
                resources.getColor(R.color.primary_gray, theme)
            )
        } else {
            binding.startStopButton.text = getString(R.string.start_inventory)
            binding.startStopButton.backgroundTintList = android.content.res.ColorStateList.valueOf(
                resources.getColor(R.color.primary_red, theme)
            )
        }
    }
    
    private fun setDeviceMode() {
        val readMode = if (mode == MODE_BARCODE) {
            0x01.toByte() // Modo scanhead (código de barras)
        } else {
            0x00.toByte() // Modo RFID
        }
        val modeCmd = CmdBuilder.buildSetReadModeCmd(readMode, ByteArray(7))
        bleCore.writeData(serviceUuid, writeUuid, modeCmd)
        
        android.util.Log.d("InventoryActivity", "Modo configurado: ${if (mode == MODE_BARCODE) "Código de Barras (0x01)" else "RFID (0x00)"}")
    }
    
    private fun startInventory() {
        if (isInventoryRunning) return
        
        // Asegurar que el callback esté configurado
        bleCore.setOnNotifyCallback(this)
        
        isInventoryRunning = true
        binding.statusText.text = getString(R.string.inventory_running)
        updateStartStopButton()
        
        // Asegurar que el modo esté configurado antes de iniciar
        setDeviceMode()
        
        // Esperar un momento para que el cambio de modo se aplique
        binding.root.postDelayed({
            if (mode == MODE_RFID) {
                // Iniciar inventario RFID
                val inventoryCmd = CmdBuilder.buildInventoryISOContinueCmd(0x00, 0)
                bleCore.writeData(serviceUuid, writeUuid, inventoryCmd)
                android.util.Log.d("InventoryActivity", "Inventario RFID iniciado")
            } else {
                // Para código de barras en modo scanhead, enviar comando de inventario con parámetros específicos
                // Según el SDK: buildInventoryISOContinueCmd((byte) 0x01, 0x01) para iniciar escaneo
                // 0x01 = modo por ciclos, 0x01 = 1 ciclo (pero el dispositivo seguirá escaneando hasta que se detenga)
                android.util.Log.d("InventoryActivity", "Iniciando escaneo de código de barras...")
                val scanCmd = CmdBuilder.buildInventoryISOContinueCmd(0x01.toByte(), 0x01)
                bleCore.writeData(serviceUuid, writeUuid, scanCmd)
                android.util.Log.d("InventoryActivity", "Modo código de barras - comando de escaneo enviado (0x01, 0x01)")
            }
        }, 500) // Aumentar el delay a 500ms para asegurar que el cambio de modo se aplique completamente
    }
    
    private fun stopInventory() {
        if (!isInventoryRunning) return
        
        isInventoryRunning = false
        binding.statusText.text = getString(R.string.inventory_stopped)
        updateStartStopButton()
        
        // Detener inventario tanto para RFID como para código de barras
        val stopCmd = CmdBuilder.buildStopInventoryCmd()
        bleCore.writeData(serviceUuid, writeUuid, stopCmd)
        android.util.Log.d("InventoryActivity", "Inventario detenido")
    }
    
    private fun setDeviceToStandby() {
        // Detener cualquier inventario activo
        if (isInventoryRunning) {
            val stopCmd = CmdBuilder.buildStopInventoryCmd()
            bleCore.writeData(serviceUuid, writeUuid, stopCmd)
            isInventoryRunning = false
        }
        
        // Restaurar a modo RFID (standby)
        lifecycleScope.launch {
            kotlinx.coroutines.delay(200)
            val rfidModeCmd = CmdBuilder.buildSetReadModeCmd(0x00, ByteArray(7))
            bleCore.writeData(serviceUuid, writeUuid, rfidModeCmd)
            android.util.Log.d("InventoryActivity", "Dispositivo puesto en standby (RFID mode)")
        }
    }
    
    private fun updateCounts() {
        val uniqueCount = uniqueInventoryItems.size
        val totalCount = allInventoryItems.size
        
        // En modo código de barras mostrar "Productos", en modo RFID mostrar "Tags"
        if (mode == MODE_BARCODE) {
            binding.tagsCountText.text = getString(R.string.products_count, uniqueCount)
        } else {
            binding.tagsCountText.text = getString(R.string.tags_count, uniqueCount)
        }
        binding.scansCountText.text = getString(R.string.scans_count, totalCount)
    }
    
    private fun initializeCounts() {
        // Inicializar contadores en 0 con el texto correcto según el modo
        if (mode == MODE_BARCODE) {
            binding.tagsCountText.text = getString(R.string.products_count, 0)
        } else {
            binding.tagsCountText.text = getString(R.string.tags_count, 0)
        }
        binding.scansCountText.text = getString(R.string.scans_count, 0)
    }
    
    private fun updateInventoryList() {
        // Actualizar la lista mostrada con items únicos ordenados por timestamp
        val sortedItems = uniqueInventoryItems.values.sortedByDescending { it.timestamp }
        inventoryAdapter.submitList(sortedItems)
        updateCounts()
        
        // Mostrar/ocultar mensaje de lista vacía
        if (sortedItems.isEmpty()) {
            binding.emptyListText.visibility = android.view.View.VISIBLE
            binding.itemsRecyclerView.visibility = android.view.View.GONE
        } else {
            binding.emptyListText.visibility = android.view.View.GONE
            binding.itemsRecyclerView.visibility = android.view.View.VISIBLE
            // Scroll al principio para ver los nuevos items
            binding.itemsRecyclerView.post {
                binding.itemsRecyclerView.smoothScrollToPosition(0)
            }
        }
    }
    
    private fun clearData() {
        allInventoryItems.clear()
        uniqueInventoryItems.clear()
        recentBarcodeReads.clear() // Limpiar también el mapa de deduplicación temporal
        inventoryAdapter.submitList(emptyList())
        initializeCounts() // Reinicializar contadores en 0 con el texto correcto según el modo
        binding.emptyListText.visibility = android.view.View.VISIBLE
        binding.itemsRecyclerView.visibility = android.view.View.GONE
    }
    
    override fun onNotify(cmdType: Int, cmdData: CmdData) {
        android.util.Log.d("InventoryActivity", "onNotify: cmdType=$cmdType (0x${Integer.toHexString(cmdType)}), mode=${if (mode == MODE_RFID) "RFID" else "BARCODE"}")
        
        when (cmdType) {
            CmdType.TYPE_INVENTORY -> {
                // En modo scanhead (código de barras), los datos también vienen como TYPE_INVENTORY
                // pero el TagInfoBean.mEPCNum contiene los datos del código de barras
                if (mode == MODE_RFID) {
                    handleRfidTag(cmdData)
                } else if (mode == MODE_BARCODE) {
                    handleBarcodeFromInventory(cmdData)
                }
            }
            CmdType.TYPE_KEY_STATE -> {
                // Manejar estado del gatillo para modo "hold"
                handleTriggerKeyState(cmdData)
            }
            else -> {
                android.util.Log.d("InventoryActivity", "onNotify: Tipo de comando no manejado: $cmdType")
            }
        }
    }
    
    private fun handleTriggerKeyState(cmdData: CmdData) {
        if (!isTriggerHoldMode) return // Solo manejar si está habilitado el modo hold
        
        val keyState = cmdData.getData() as? KeyStateBean ?: return
        val keyStateValue = keyState.mKeyState.toInt()
        
        android.util.Log.d("InventoryActivity", "Trigger key state: $keyStateValue (0x01=presionado, 0x02=soltado)")
        
        runOnUiThread {
            when (keyStateValue) {
                0x01 -> { // Presionado - Iniciar inventario
                    if (!isInventoryRunning) {
                        android.util.Log.d("InventoryActivity", "Gatillo presionado - Iniciando inventario")
                        startInventory()
                    }
                }
                0x02 -> { // Soltado - Detener inventario
                    if (isInventoryRunning) {
                        android.util.Log.d("InventoryActivity", "Gatillo soltado - Deteniendo inventario")
                        stopInventory()
                    }
                }
            }
        }
    }
    
    override fun onNotify(bytes: ByteArray) {
        // Los datos raw también pueden venir aquí para códigos de barras
        android.util.Log.d("InventoryActivity", "onNotify(bytes) recibido: ${bytes.size} bytes")
        if (mode == MODE_BARCODE && bytes.isNotEmpty()) {
            // Limpiar y procesar datos raw de código de barras
            val barcodeData = cleanBarcodeData(bytes)
            
            // Validar que sea un código de barras válido
            if (barcodeData.isNotEmpty() && isValidBarcode(barcodeData)) {
                val currentTime = System.currentTimeMillis()
                
                // Deduplicación temporal: evitar spam de lecturas muy rápidas
                val lastReadTime = recentBarcodeReads[barcodeData]
                if (lastReadTime != null && (currentTime - lastReadTime) < BARCODE_DEDUP_TIME_MS) {
                    android.util.Log.d("InventoryActivity", "Código de barras duplicado reciente desde onNotify(bytes), ignorando: $barcodeData")
                    return
                }
                
                // Registrar esta lectura para deduplicación temporal
                recentBarcodeReads[barcodeData] = currentTime
                
                android.util.Log.d("InventoryActivity", "✓ Código de barras desde onNotify(bytes): $barcodeData")
                
                val newItem = InventoryItem(
                    data = barcodeData,
                    rssi = 0,
                    antenna = 0,
                    timestamp = currentTime,
                    mode = "Barcode",
                    readCount = 1
                )
                
                runOnUiThread {
                    allInventoryItems.add(newItem)
                    historyStore.add(ScanHistoryActivity.MODE_BARCODE, barcodeData)
                    persistSessionIfAny()
                    // Actualizar o agregar item único (incrementar contador si ya existe)
                    val existingItem = uniqueInventoryItems[barcodeData]
                    if (existingItem != null) {
                        uniqueInventoryItems[barcodeData] = existingItem.copy(
                            readCount = existingItem.readCount + 1,
                            timestamp = currentTime
                        )
                        android.util.Log.d("InventoryActivity", "Código de barras repetido desde onNotify(bytes): $barcodeData (${existingItem.readCount + 1} veces)")
                    } else {
                        uniqueInventoryItems[barcodeData] = newItem
                        android.util.Log.d("InventoryActivity", "Nuevo código de barras desde onNotify(bytes): $barcodeData")
                    }
                    updateInventoryList()
                }
            } else {
                android.util.Log.d("InventoryActivity", "Datos desde onNotify(bytes) ignorados (inválidos o vacíos): '$barcodeData'")
            }
        }
    }
    
    private fun handleBarcodeFromInventory(cmdData: CmdData) {
        val tagInfo = cmdData.getData() as? TagInfoBean ?: run {
            android.util.Log.w("InventoryActivity", "handleBarcodeFromInventory: tagInfo es null")
            return
        }
        
        // Solo procesar si el estado es éxito (0x00)
        // 0x12 = no se encontró tag o comando completado (ignorar)
        // Otros = errores (ignorar)
        if (tagInfo.mStatus != 0x00) {
            android.util.Log.d("InventoryActivity", "handleBarcodeFromInventory: mStatus=${tagInfo.mStatus} (no es éxito), ignorando")
            return
        }
        
        // Validar que haya datos
        if (tagInfo.mEPCNum == null || tagInfo.mEPCNum.isEmpty()) {
            android.util.Log.w("InventoryActivity", "handleBarcodeFromInventory: mEPCNum es null o vacío")
            return
        }
        
        // Limpiar y convertir bytes a String, filtrando caracteres inválidos
        val barcodeData = cleanBarcodeData(tagInfo.mEPCNum)
        
        // Validar que el código de barras no esté vacío
        if (barcodeData.isEmpty()) {
            android.util.Log.w("InventoryActivity", "Código de barras vacío después de limpieza, ignorando")
            return
        }
        
        // Validar que sea un código de barras válido (filtrar mensajes de error)
        if (!isValidBarcode(barcodeData)) {
            android.util.Log.w("InventoryActivity", "Código de barras inválido o mensaje de error: '$barcodeData', ignorando")
            return
        }
        
        // Para códigos de barras: registrar primera lectura y contar repeticiones
        val currentTime = System.currentTimeMillis()
        
        // Deduplicación temporal: evitar spam de lecturas muy rápidas del mismo código
        val lastReadTime = recentBarcodeReads[barcodeData]
        if (lastReadTime != null && (currentTime - lastReadTime) < BARCODE_DEDUP_TIME_MS) {
            android.util.Log.d("InventoryActivity", "Código de barras duplicado reciente (${currentTime - lastReadTime}ms), ignorando: $barcodeData")
            return
        }
        
        // Registrar esta lectura para deduplicación temporal
        recentBarcodeReads[barcodeData] = currentTime
        
        // Limpiar entradas antiguas del mapa de deduplicación temporal (más de 5 segundos)
        val fiveSecondsAgo = currentTime - 5000L
        recentBarcodeReads.entries.removeAll { it.value < fiveSecondsAgo }
        
        android.util.Log.d("InventoryActivity", "✓ Código de barras válido leído: $barcodeData")
        
        val newItem = InventoryItem(
            data = barcodeData,
            rssi = tagInfo.mRSSI,
            antenna = tagInfo.mAntenna,
            timestamp = currentTime,
            mode = "Barcode",
            readCount = 1
        )
        
        runOnUiThread {
            // Agregar a todas las lecturas
            allInventoryItems.add(newItem)
            historyStore.add(ScanHistoryActivity.MODE_BARCODE, barcodeData)
            persistSessionIfAny()
            
            // Actualizar o agregar item único (incrementar contador si ya existe)
            val existingItem = uniqueInventoryItems[barcodeData]
            if (existingItem != null) {
                // Incrementar contador de repeticiones
                uniqueInventoryItems[barcodeData] = existingItem.copy(
                    readCount = existingItem.readCount + 1,
                    timestamp = currentTime
                )
                android.util.Log.d("InventoryActivity", "Código de barras repetido: $barcodeData (${existingItem.readCount + 1} veces)")
            } else {
                // Nuevo item único (primera lectura)
                uniqueInventoryItems[barcodeData] = newItem
                android.util.Log.d("InventoryActivity", "Nuevo código de barras: $barcodeData")
            }
            
            updateInventoryList()
        }
    }
    
    /**
     * Convierte los bytes del código de barras a String sin limpieza
     * Se registra exactamente como viene del escáner
     */
    private fun cleanBarcodeData(bytes: ByteArray): String {
        return com.dohealth.handheld.utils.decodeBarcodeWithHexFallback(bytes)
    }
    
    /**
     * Valida que el código de barras sea válido y no sea un mensaje de error
     */
    private fun isValidBarcode(barcode: String): Boolean {
        // Filtrar mensajes de error comunes
        val errorKeywords = listOf(
            "error", "fail", "failed", "error en", "error de", "fallo", "falló",
            "no se", "no se pudo", "no se encontró", "sin", "vacío", "empty",
            "invalid", "inválido", "desconocido", "unknown", "执行成功", "没有盘点到",
            "参数值错误", "内部错误", "超时", "认证失败", "口令错误"
        )
        
        val barcodeLower = barcode.lowercase()
        if (errorKeywords.any { barcodeLower.contains(it) }) {
            return false
        }
        
        // Filtrar códigos muy cortos (menos de 2 caracteres probablemente no son códigos de barras válidos)
        // Algunos códigos QR pueden ser muy cortos, pero generalmente tienen al menos 2 caracteres
        if (barcode.length < 2) {
            return false
        }
        
        // Filtrar códigos que solo contengan caracteres de control o espacios
        if (barcode.all { it.isWhitespace() || it.isISOControl() }) {
            return false
        }
        
        // Filtrar códigos que contengan caracteres de reemplazo Unicode
        if (barcode.contains('\uFFFD')) {
            return false
        }
        
        // Filtrar códigos que sean solo números hexadecimales muy cortos (probablemente datos de protocolo)
        // Pero permitir códigos hexadecimales más largos que podrían ser códigos válidos
        if (barcode.matches(Regex("^[0-9a-fA-F]{1,4}$"))) {
            return false
        }
        
        // Filtrar códigos que tengan demasiados caracteres no imprimibles o inválidos
        val invalidCharCount = barcode.count { 
            it.isISOControl() || 
            it == '\uFFFD' || 
            it.isSurrogate() ||
            !Character.isDefined(it.code)
        }
        if (invalidCharCount > barcode.length / 2) {
            return false
        }
        
        return true
    }
    
    private fun handleRfidTag(cmdData: CmdData) {
        val tagInfo = cmdData.getData() as? TagInfoBean ?: return
        
        if (tagInfo.mStatus == 0x00 && tagInfo.mEPCNum != null) {
            val epcHex = FormatUtil.bytesToHexStr(tagInfo.mEPCNum).replace(" ", "")
            val rssi = tagInfo.mRSSI
            val antenna = tagInfo.mAntenna
            
            val newItem = InventoryItem(
                data = epcHex,
                rssi = rssi,
                antenna = antenna,
                timestamp = System.currentTimeMillis(),
                mode = "RFID",
                readCount = 1
            )
            
            runOnUiThread {
                // Agregar a todas las lecturas
                allInventoryItems.add(newItem)
                historyStore.add(ScanHistoryActivity.MODE_RFID, epcHex)
                persistSessionIfAny()
                
                // Actualizar o agregar item único
                val existingItem = uniqueInventoryItems[epcHex]
                if (existingItem != null) {
                    // Incrementar contador de repeticiones
                    uniqueInventoryItems[epcHex] = existingItem.copy(
                        readCount = existingItem.readCount + 1,
                        timestamp = System.currentTimeMillis() // Actualizar timestamp a la última lectura
                    )
                } else {
                    // Nuevo item único
                    uniqueInventoryItems[epcHex] = newItem
                }
                
                updateInventoryList()
            }
        }
    }
    
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.inventory_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                // Poner dispositivo en standby antes de salir
                setDeviceToStandby()
                finish()
                true
            }
            R.id.export_data -> {
                showExportOptions()
                true
            }
            R.id.history -> {
                startActivity(Intent(this, ScanHistoryActivity::class.java).apply {
                    putExtra(
                        ScanHistoryActivity.EXTRA_MODE,
                        if (mode == MODE_RFID) ScanHistoryActivity.MODE_RFID else ScanHistoryActivity.MODE_BARCODE
                    )
                })
                true
            }
            R.id.send_server -> {
                sendToServer()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun showExportOptions() {
        if (allInventoryItems.isEmpty()) {
            Toast.makeText(this, R.string.no_data, Toast.LENGTH_SHORT).show()
            return
        }
        
        val options = arrayOf("Exportar a Archivo TXT", "Enviar al Servidor")
        AlertDialog.Builder(this)
            .setTitle("Exportar Inventario")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> exportToTxt()
                    1 -> sendToServer()
                }
            }
            .show()
    }
    
    private fun exportToTxt() {
        // Verificar que haya items únicos para exportar
        val uniqueItems = uniqueInventoryItems.values.toList()
        if (uniqueItems.isEmpty()) {
            Toast.makeText(this, "No hay items únicos para exportar", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Detener inventario antes de exportar para evitar problemas
        val wasRunning = isInventoryRunning
        if (wasRunning) {
            stopInventory()
        }
        
        binding.progressBar.visibility = android.view.View.VISIBLE
        
        // Generar el contenido del archivo en background
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                android.util.Log.d("InventoryActivity", "Generando contenido TXT para ${uniqueItems.size} items únicos...")
                val content = TextExporter.generateInventoryContent(uniqueItems, mode)
                
                runOnUiThread {
                    pendingExportContent = content
                    
                    // Crear nombre de archivo por defecto
                    val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                    val defaultFileName = "rfid_$timestamp.txt"
                    
                    // Abrir selector de archivos
                    createDocumentLauncher.launch(defaultFileName)
                }
            } catch (e: Exception) {
                android.util.Log.e("InventoryActivity", "Error al generar contenido TXT: ${e.message}", e)
                e.printStackTrace()
                
                runOnUiThread {
                    binding.progressBar.visibility = android.view.View.GONE
                    
                    Toast.makeText(
                        this@InventoryActivity,
                        "✗ Error al preparar exportación:\n${e.message ?: "Error desconocido"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    private fun saveContentToUri(uri: Uri) {
        if (pendingExportContent == null) {
            binding.progressBar.visibility = android.view.View.GONE
            Toast.makeText(this, "Error: No hay contenido para guardar", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(pendingExportContent!!.toByteArray(Charsets.UTF_8))
                    outputStream.flush()
                }
                
                runOnUiThread {
                    binding.progressBar.visibility = android.view.View.GONE
                    pendingExportContent = null
                    
                    Toast.makeText(
                        this@InventoryActivity,
                        "✓ Archivo guardado exitosamente",
                        Toast.LENGTH_SHORT
                    ).show()
                    
                    android.util.Log.d("InventoryActivity", "Archivo guardado en: $uri")
                }
            } catch (e: Exception) {
                android.util.Log.e("InventoryActivity", "Error al guardar archivo: ${e.message}", e)
                e.printStackTrace()
                
                runOnUiThread {
                    binding.progressBar.visibility = android.view.View.GONE
                    pendingExportContent = null
                    
                    Toast.makeText(
                        this@InventoryActivity,
                        "✗ Error al guardar:\n${e.message ?: "Error desconocido"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    private fun sendToServer() {
        if (allInventoryItems.isEmpty()) {
            Toast.makeText(this, R.string.no_data, Toast.LENGTH_SHORT).show()
            return
        }
        
        // Detener inventario antes de enviar
        val wasRunning = isInventoryRunning
        if (wasRunning) {
            stopInventory()
        }
        
        binding.progressBar.visibility = android.view.View.VISIBLE
        
        lifecycleScope.launch {
            try {
                val success = ApiService.sendInventory(this@InventoryActivity, uniqueInventoryItems.values.toList())
                runOnUiThread {
                    if (success) {
                        Toast.makeText(this@InventoryActivity, "✓ Datos enviados exitosamente", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@InventoryActivity, "✗ Error al enviar datos", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("InventoryActivity", "Error al enviar: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(this@InventoryActivity, "Error: ${e.message ?: "No se pudo enviar"}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                runOnUiThread {
                    binding.progressBar.visibility = android.view.View.GONE
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        bleCore.setOnNotifyCallback(this)
        // Asegurar que el modo esté configurado correctamente al volver
        setDeviceMode()
        // Verificar modo trigger hold
        checkTriggerHoldMode()
    }
    
    override fun onPause() {
        super.onPause()
        persistSessionIfAny()
        // Poner dispositivo en standby al salir
        setDeviceToStandby()
    }
    
    private fun handleBackPress() {
        // Poner dispositivo en standby antes de volver
        setDeviceToStandby()
        finish()
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        handleBackPress()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Asegurar que el dispositivo esté en standby
        setDeviceToStandby()
        bleCore.setOnNotifyCallback(null)
    }
}

