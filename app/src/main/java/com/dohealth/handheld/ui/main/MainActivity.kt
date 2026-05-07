package com.dohealth.handheld.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cf.zsdk.BleCore
import com.cf.zsdk.CfSdk
import com.cf.zsdk.SdkC
import com.cf.zsdk.cmd.CmdBuilder
import com.dohealth.handheld.R
import com.dohealth.handheld.databinding.ActivityMainBinding
import com.dohealth.handheld.ui.config.RfidConfigActivity
import com.dohealth.handheld.ui.inventory.InventoryActivity
import com.dohealth.handheld.ui.inventorysessions.InventorySessionsActivity
import com.dohealth.handheld.ui.relacion.RelacionSessionsActivity
import com.dohealth.handheld.utils.Constants
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var bleCore: BleCore
    private var deviceName: String = ""
    private var deviceAddress: String = ""
    private val serviceUuid = UUID.fromString(Constants.SERVICE_UUID)
    private val writeUuid = UUID.fromString(Constants.WRITE_UUID)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        deviceName = intent.getStringExtra("device_name") ?: "Dispositivo"
        deviceAddress = intent.getStringExtra("device_address") ?: ""
        
        bleCore = CfSdk.get(SdkC.BLE)
        
        setupUI()
        setupClickListeners()
    }
    
    override fun onResume() {
        super.onResume()
        // Asegurar que el dispositivo esté en standby (modo RFID, inventario detenido)
        setDeviceToStandby()
    }
    
    private fun setDeviceToStandby() {
        lifecycleScope.launch {
            kotlinx.coroutines.delay(100)
            // Detener cualquier inventario activo
            val stopCmd = CmdBuilder.buildStopInventoryCmd()
            bleCore.writeData(serviceUuid, writeUuid, stopCmd)
            
            kotlinx.coroutines.delay(200)
            // Establecer modo RFID (standby)
            val rfidModeCmd = CmdBuilder.buildSetReadModeCmd(0x00, ByteArray(7))
            bleCore.writeData(serviceUuid, writeUuid, rfidModeCmd)
            android.util.Log.d("MainActivity", "Dispositivo puesto en standby")
        }
    }
    
    private fun setupUI() {
        binding.deviceNameText.text = "Dispositivo: $deviceName"
        binding.deviceAddressText.text = "Dirección: $deviceAddress"
    }
    
    private fun setupClickListeners() {
        // Botón RFID
        binding.rfidCard.setOnClickListener {
            startInventorySessionsActivity(InventoryActivity.MODE_RFID)
        }
        
        // Botón Código de Barras
        binding.barcodeCard.setOnClickListener {
            startInventorySessionsActivity(InventoryActivity.MODE_BARCODE)
        }

        // Módulo ESFERICA (RFID separado)
        binding.esfericaCard.setOnClickListener {
            startActivity(Intent(this, InventorySessionsActivity::class.java).apply {
                putExtra(InventorySessionsActivity.EXTRA_MODE_INT, InventoryActivity.MODE_RFID)
                putExtra(InventorySessionsActivity.EXTRA_SESSION_MODE_KEY, "ESFERICA")
                putExtra(InventorySessionsActivity.EXTRA_TITLE, getString(R.string.mode_esferica))
                putExtra("device_name", deviceName)
                putExtra("device_address", deviceAddress)
            })
        }
        
        // Botón Configuración
        binding.configButton.setOnClickListener {
            startActivity(Intent(this, RfidConfigActivity::class.java))
        }

        // Módulo Relación por Códigos
        binding.relacionCodigosCard.setOnClickListener {
            startActivity(Intent(this, RelacionSessionsActivity::class.java).apply {
                putExtra("device_name", deviceName)
                putExtra("device_address", deviceAddress)
            })
        }
    }
    
    private fun startInventoryActivity(mode: Int) {
        val intent = Intent(this, InventoryActivity::class.java).apply {
            putExtra("mode", mode)
            putExtra("device_name", deviceName)
            putExtra("device_address", deviceAddress)
        }
        startActivity(intent)
    }

    private fun startInventorySessionsActivity(mode: Int) {
        startActivity(Intent(this, InventorySessionsActivity::class.java).apply {
            putExtra(InventorySessionsActivity.EXTRA_MODE_INT, mode)
            putExtra("device_name", deviceName)
            putExtra("device_address", deviceAddress)
        })
    }
}

