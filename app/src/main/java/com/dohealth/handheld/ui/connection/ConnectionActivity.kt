package com.dohealth.handheld.ui.connection

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanRecord
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.cf.beans.CmdData
import com.cf.ble.BleUtil
import com.cf.ble.interfaces.IConnectDoneCallback
import com.cf.ble.interfaces.IOnNotifyCallback
import com.cf.zsdk.BleCore
import com.cf.zsdk.CfSdk
import com.cf.zsdk.SdkC
import com.cf.zsdk.cmd.CmdBuilder
import com.cf.zsdk.cmd.CmdType
import com.cf.zsdk.uitl.FormatUtil
import com.dohealth.handheld.R
import com.dohealth.handheld.databinding.ActivityConnectionBinding
import com.dohealth.handheld.ui.main.MainActivity
import com.dohealth.handheld.utils.Constants
import com.dohealth.handheld.utils.WebhookDeviceIdentity
import java.util.UUID

class ConnectionActivity : AppCompatActivity(), IOnNotifyCallback, IConnectDoneCallback {
    
    companion object {
        private const val TAG = "ConnectionActivity"
    }
    
    private lateinit var binding: ActivityConnectionBinding
    private lateinit var bleCore: BleCore
    private lateinit var deviceAdapter: DeviceAdapter
    private var bluetoothGatt: android.bluetooth.BluetoothGatt? = null
    private var connectedDevice: BluetoothDevice? = null
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            startScanning()
        } else {
            showPermissionDialog()
        }
    }
    
    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (bleCore.isEnabled) {
            startScanning()
        } else {
            showBluetoothDialog()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "=== ConnectionActivity onCreate ===")
        binding = ActivityConnectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        try {
            bleCore = CfSdk.get(SdkC.BLE)
            Log.d(TAG, "BleCore obtenido del SDK")
            bleCore.init(this)
            Log.d(TAG, "BleCore inicializado")
            
            // Verificar soporte de Bluetooth
            if (!bleCore.isSupportBt()) {
                Log.e(TAG, "ERROR: El dispositivo no soporta Bluetooth")
                Toast.makeText(this, "El dispositivo no soporta Bluetooth", Toast.LENGTH_LONG).show()
                finish()
                return
            }
            
            Log.d(TAG, "Bluetooth soportado: ${bleCore.isSupportBt()}")
            Log.d(TAG, "Bluetooth habilitado: ${bleCore.isEnabled}")
        } catch (e: Exception) {
            Log.e(TAG, "ERROR al inicializar BleCore: ${e.message}", e)
            Toast.makeText(this, "Error al inicializar Bluetooth: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        
        setupRecyclerView()
        setupClickListeners()
        checkPermissions()
    }
    
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "=== onResume ===")
        // Iniciar escaneo automáticamente si Bluetooth está habilitado y permisos otorgados
        binding.root.post {
            val hasPermissions = checkPermissionsSilently()
            if (hasPermissions && bleCore.isEnabled) {
                Log.d(TAG, "Iniciando escaneo automático...")
                startScanning()
            } else if (!hasPermissions) {
                Log.d(TAG, "Esperando permisos...")
            } else if (!bleCore.isEnabled) {
                Log.d(TAG, "Esperando que Bluetooth se active...")
            }
        }
    }
    
    private fun checkPermissionsSilently(): Boolean {
        val permissions = mutableListOf<String>().apply {
            add(Manifest.permission.BLUETOOTH)
            add(Manifest.permission.BLUETOOTH_ADMIN)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            }
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun setupRecyclerView() {
        deviceAdapter = DeviceAdapter { device ->
            connectToDevice(device)
        }
        binding.devicesRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.devicesRecyclerView.adapter = deviceAdapter
    }
    
    private fun setupClickListeners() {
        binding.scanButton.setOnClickListener {
            if (checkPermissions()) {
                startScanning()
            }
        }
    }
    
    private fun checkPermissions(): Boolean {
        val permissions = mutableListOf<String>().apply {
            add(Manifest.permission.BLUETOOTH)
            add(Manifest.permission.BLUETOOTH_ADMIN)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            }
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        
        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        return if (missingPermissions.isEmpty()) {
            checkBluetooth()
            true
        } else {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
            false
        }
    }
    
    private fun checkBluetooth() {
        if (!bleCore.isSupportBt()) {
            Toast.makeText(this, "El dispositivo no soporta Bluetooth", Toast.LENGTH_LONG).show()
            return
        }
        
        if (!bleCore.isEnabled) {
            showBluetoothDialog()
        } else {
            startScanning()
        }
    }
    
    private fun showBluetoothDialog() {
        AlertDialog.Builder(this)
            .setTitle("Bluetooth Requerido")
            .setMessage("Por favor, activa el Bluetooth para continuar")
            .setPositiveButton("Activar") { _, _ ->
                val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                enableBluetoothLauncher.launch(intent)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    private fun showPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permisos Requeridos")
            .setMessage("La aplicación necesita permisos de Bluetooth y ubicación para funcionar")
            .setPositiveButton("Otorgar") { _, _ ->
                checkPermissions()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    private fun startScanning() {
        Log.d(TAG, "=== INICIANDO ESCANEO BLUETOOTH ===")
        
        // Desconectar si hay una conexión activa
        if (bleCore.isConnect) {
            Log.d(TAG, "Desconectando dispositivo anterior...")
            bleCore.disconnectedDevice()
        }
        
        binding.scanButton.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE
        binding.statusText.text = getString(R.string.scanning_devices)
        deviceAdapter.clearDevices()
        
        // Verificar que Bluetooth esté habilitado
        if (!bleCore.isEnabled) {
            Log.e(TAG, "ERROR: Bluetooth no está habilitado")
            Toast.makeText(this, "Por favor, activa el Bluetooth", Toast.LENGTH_LONG).show()
            binding.scanButton.isEnabled = true
            binding.progressBar.visibility = View.GONE
            return
        }
        
        Log.d(TAG, "Bluetooth habilitado, iniciando escaneo...")
        var totalDevicesScanned = 0
        var cfDevicesFound = 0
        var otherDevicesFound = 0
        
        bleCore.startScan { result ->
            val device = result.device
            val scanRecord = result.scanRecord
            
            totalDevicesScanned++
            Log.d(TAG, "Dispositivo #$totalDevicesScanned detectado: ${device.name ?: "Sin nombre"} (${device.address})")
            
            runOnUiThread {
                // Filtrar dispositivos sin nombre (excepto dispositivos CF/RFID)
                val deviceName = device.name
                val hasName = !deviceName.isNullOrBlank()
                
                if (scanRecord != null) {
                    try {
                        val isCfDevice = BleUtil.isCfDevice(scanRecord.bytes)
                        
                        if (isCfDevice) {
                            // Dispositivos RFID siempre se muestran, aunque no tengan nombre
                            cfDevicesFound++
                            Log.d(TAG, "  >>> DISPOSITIVO RFID ENCONTRADO! <<<")
                            deviceAdapter.addDeviceForCf(device) // Agregar al principio
                        } else if (hasName) {
                            // Solo mostrar dispositivos Bluetooth normales que tengan nombre
                            otherDevicesFound++
                            Log.d(TAG, "  - Dispositivo Bluetooth normal con nombre")
                            deviceAdapter.addDevice(device) // Agregar al final
                        } else {
                            Log.d(TAG, "  - Dispositivo sin nombre ignorado (no es RFID)")
                        }
                        
                        val companyId = BleUtil.getCompanyId(scanRecord.bytes)
                        val companyIdStr = FormatUtil.bytesToHexStr(companyId)
                        Log.d(TAG, "  - Company ID: $companyIdStr, RSSI: ${result.rssi}, Nombre: ${deviceName ?: "Sin nombre"}")
                        
                        // Actualizar estado
                        val totalFound = deviceAdapter.getDeviceCount()
                        binding.statusText.text = "$totalFound dispositivo(s) encontrado(s)"
                    } catch (e: Exception) {
                        Log.e(TAG, "Error al procesar scanRecord: ${e.message}", e)
                        // Solo agregar si tiene nombre
                        if (hasName) {
                            deviceAdapter.addDevice(device)
                        }
                    }
                } else {
                    // Sin scanRecord, solo agregar si tiene nombre
                    if (hasName) {
                        otherDevicesFound++
                        Log.d(TAG, "  - Sin scanRecord pero con nombre, agregando")
                        deviceAdapter.addDevice(device)
                        val totalFound = deviceAdapter.getDeviceCount()
                        binding.statusText.text = "$totalFound dispositivo(s) encontrado(s)"
                    } else {
                        Log.d(TAG, "  - Sin scanRecord y sin nombre, ignorado")
                    }
                }
            }
        }
        
        Log.d(TAG, "Escaneo iniciado, se detendrá en 5 segundos...")
        
        // Detener escaneo después de 5 segundos (como en la app original)
        binding.root.postDelayed({
            Log.d(TAG, "=== FINALIZANDO ESCANEO ===")
            Log.d(TAG, "Total dispositivos escaneados: $totalDevicesScanned")
            Log.d(TAG, "Dispositivos RFID encontrados: $cfDevicesFound")
            Log.d(TAG, "Otros dispositivos encontrados: $otherDevicesFound")
            stopScanning()
        }, 5000)
    }
    
    private fun stopScanning() {
        Log.d(TAG, "Deteniendo escaneo...")
        bleCore.stopScan()
        binding.scanButton.isEnabled = true
        binding.progressBar.visibility = View.GONE
        
        val deviceCount = deviceAdapter.getDeviceCount()
        Log.d(TAG, "Dispositivos en la lista: $deviceCount")
        
        if (deviceCount == 0) {
            binding.statusText.text = getString(R.string.no_devices_found)
            Log.w(TAG, "No se encontraron dispositivos")
        } else {
            binding.statusText.text = "$deviceCount dispositivo(s) encontrado(s)"
            Log.d(TAG, "Se encontraron $deviceCount dispositivo(s)")
        }
    }
    
    private fun connectToDevice(device: BluetoothDevice) {
        Log.d(TAG, "=== CONECTANDO A DISPOSITIVO ===")
        Log.d(TAG, "Nombre: ${device.name ?: "Sin nombre"}")
        Log.d(TAG, "Dirección: ${device.address}")
        
        binding.statusText.text = getString(R.string.connecting)
        binding.progressBar.visibility = View.VISIBLE
        
        try {
            bleCore.setIConnectDoneCallback(this)
            bluetoothGatt = bleCore.connectDevice(device, this, false)
            connectedDevice = device
            Log.d(TAG, "Comando de conexión enviado")
            
            // Timeout de conexión
            binding.root.postDelayed({
                if (!bleCore.isConnect) {
                    Log.e(TAG, "ERROR: Timeout de conexión")
                    binding.statusText.text = getString(R.string.connection_failed)
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, "Error: No se pudo conectar al dispositivo", Toast.LENGTH_LONG).show()
                }
            }, 10000)
        } catch (e: Exception) {
            Log.e(TAG, "ERROR al conectar: ${e.message}", e)
            Toast.makeText(this, "Error al conectar: ${e.message}", Toast.LENGTH_LONG).show()
            binding.progressBar.visibility = View.GONE
        }
    }
    
    override fun onConnectDone(success: Boolean) {
        Log.d(TAG, "=== onConnectDone: $success ===")
        runOnUiThread {
            if (success) {
                Log.d(TAG, "Conexión exitosa!")
                binding.statusText.text = getString(R.string.connected)
                binding.progressBar.visibility = View.GONE
                
                try {
                    // Abrir notificaciones
                    val serviceUuid = UUID.fromString(Constants.SERVICE_UUID)
                    val notifyUuid = UUID.fromString(Constants.NOTIFY_UUID)
                    Log.d(TAG, "Configurando notificaciones...")
                    bleCore.setNotifyState(serviceUuid, notifyUuid, true, this)
                    
                    // Inicializar módulo RFID
                    Log.d(TAG, "Inicializando módulo RFID...")
                    val initCmd = CmdBuilder.buildModuleInitCmd()
                    val writeUuid = UUID.fromString(Constants.WRITE_UUID)
                    bleCore.writeData(serviceUuid, writeUuid, initCmd)
                    Log.d(TAG, "Comando de inicialización enviado")
                    
                    // Esperar un momento y luego ir a la pantalla principal
                    binding.root.postDelayed({
                        Log.d(TAG, "Navegando a MainActivity...")
                        val intent = Intent(this, MainActivity::class.java)
                        val readerName = connectedDevice?.name ?: "Dispositivo"
                        val readerAddress = connectedDevice?.address ?: ""
                        WebhookDeviceIdentity.saveConnectedReader(this, readerName, readerAddress)
                        intent.putExtra("device_name", readerName)
                        intent.putExtra("device_address", readerAddress)
                        startActivity(intent)
                        finish()
                    }, 1000)
                } catch (e: Exception) {
                    Log.e(TAG, "ERROR al configurar dispositivo: ${e.message}", e)
                    Toast.makeText(this, "Error al configurar dispositivo: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } else {
                Log.e(TAG, "ERROR: Conexión fallida")
                binding.statusText.text = getString(R.string.connection_failed)
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "No se pudo conectar al dispositivo", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    override fun onNotify(cmdType: Int, cmdData: CmdData) {
        // Procesar respuestas del dispositivo
    }
    
    override fun onNotify(bytes: ByteArray) {
        // Datos raw (opcional)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        bleCore.stopScan()
        if (!bleCore.isConnect) {
            bleCore.disconnectedDevice()
        }
    }
}

