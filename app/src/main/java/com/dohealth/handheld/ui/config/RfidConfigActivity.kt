package com.dohealth.handheld.ui.config

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.cf.beans.AllParamBean
import com.cf.beans.CmdData
import com.cf.ble.interfaces.IOnNotifyCallback
import com.cf.zsdk.BleCore
import com.cf.zsdk.CfSdk
import com.cf.zsdk.SdkC
import com.cf.zsdk.cmd.CmdBuilder
import com.cf.zsdk.cmd.CmdType
import com.dohealth.handheld.R
import com.dohealth.handheld.databinding.ActivityRfidConfigBinding
import com.dohealth.handheld.utils.Constants
import java.util.UUID

class RfidConfigActivity : AppCompatActivity(), IOnNotifyCallback {
    
    private lateinit var binding: ActivityRfidConfigBinding
    private lateinit var bleCore: BleCore
    private lateinit var prefs: SharedPreferences
    private var allParamBean: AllParamBean? = null
    private val serviceUuid = UUID.fromString(Constants.SERVICE_UUID)
    private val writeUuid = UUID.fromString(Constants.WRITE_UUID)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRfidConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.config_title)
        
        bleCore = CfSdk.get(SdkC.BLE)
        prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        
        setupSpinners()
        loadSavedConfig()
        setupClickListeners()
    }
    
    private fun setupSpinners() {
        // Modo de Trabajo
        val workModes = arrayOf("Respuesta (0)", "Activo (1)", "Trigger (2)")
        val workModeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, workModes)
        workModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.workModeSpinner.adapter = workModeAdapter
        
        // Área de Consulta
        val inquiryAreas = arrayOf(
            "EPC (0x01)",
            "TID (0x02)",
            "USER (0x03)",
            "EPC+TID (0x04)",
            "EPC+USER (0x05)",
            "EPC+TID+USER (0x06)"
        )
        val inquiryAreaAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, inquiryAreas)
        inquiryAreaAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.inquiryAreaSpinner.adapter = inquiryAreaAdapter
    }
    
    private fun loadSavedConfig() {
        val powerLevel = prefs.getInt(Constants.KEY_POWER_LEVEL, Constants.DEFAULT_POWER_LEVEL)
        val qValue = prefs.getInt(Constants.KEY_Q_VALUE, Constants.DEFAULT_Q_VALUE)
        val session = prefs.getInt(Constants.KEY_SESSION, Constants.DEFAULT_SESSION)
        val antenna = prefs.getInt(Constants.KEY_ANTENNA, Constants.DEFAULT_ANTENNA)
        val filterTime = prefs.getInt(Constants.KEY_FILTER_TIME, Constants.DEFAULT_FILTER_TIME)
        val buzzerTime = prefs.getInt(Constants.KEY_BUZZER_TIME, Constants.DEFAULT_BUZZER_TIME)
        val pollingInterval = prefs.getInt(Constants.KEY_POLLING_INTERVAL, Constants.DEFAULT_POLLING_INTERVAL)
        val workMode = prefs.getInt(Constants.KEY_WORK_MODE, Constants.DEFAULT_WORK_MODE)
        val inquiryArea = prefs.getInt(Constants.KEY_INQUIRY_AREA, Constants.DEFAULT_INQUIRY_AREA)
        
        binding.powerSeekBar.progress = powerLevel
        binding.powerValueText.text = "$powerLevel dBm"
        binding.qValueSeekBar.progress = qValue
        binding.qValueText.text = qValue.toString()
        binding.sessionSeekBar.progress = session
        binding.sessionText.text = session.toString()
        binding.antennaSeekBar.progress = antenna - 1
        binding.antennaText.text = antenna.toString()
        binding.filterTimeSeekBar.progress = filterTime
        binding.filterTimeText.text = "$filterTime s"
        binding.buzzerTimeSeekBar.progress = buzzerTime
        binding.buzzerTimeText.text = buzzerTime.toString()
        binding.pollingIntervalSeekBar.progress = pollingInterval
        binding.pollingIntervalText.text = pollingInterval.toString()
        
        binding.workModeSpinner.setSelection(workMode.coerceIn(0, 2))
        binding.inquiryAreaSpinner.setSelection((inquiryArea - 1).coerceIn(0, 5))
    }
    
    private fun setupClickListeners() {
        binding.powerSeekBar.setOnSeekBarChangeListener(
            object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    binding.powerValueText.text = "$progress dBm"
                }
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            }
        )
        
        binding.qValueSeekBar.setOnSeekBarChangeListener(
            object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    binding.qValueText.text = progress.toString()
                }
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            }
        )
        
        binding.sessionSeekBar.setOnSeekBarChangeListener(
            object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    binding.sessionText.text = progress.toString()
                }
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            }
        )
        
        binding.antennaSeekBar.setOnSeekBarChangeListener(
            object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    binding.antennaText.text = (progress + 1).toString()
                }
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            }
        )
        
        binding.filterTimeSeekBar.setOnSeekBarChangeListener(
            object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    binding.filterTimeText.text = "$progress s"
                }
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            }
        )
        
        binding.buzzerTimeSeekBar.setOnSeekBarChangeListener(
            object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    binding.buzzerTimeText.text = progress.toString()
                }
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            }
        )
        
        binding.pollingIntervalSeekBar.setOnSeekBarChangeListener(
            object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    binding.pollingIntervalText.text = progress.toString()
                }
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            }
        )
        
        binding.saveButton.setOnClickListener {
            saveConfig()
        }
        
        binding.loadButton.setOnClickListener {
            loadDeviceConfig()
        }
        
        // Configurar listeners para iconos de información
        setupInfoIcons()
    }
    
    private fun setupInfoIcons() {
        binding.powerInfoIcon.setOnClickListener {
            showInfoDialog(getString(R.string.power_level), getString(R.string.power_level_desc))
        }
        
        binding.qValueInfoIcon.setOnClickListener {
            showInfoDialog(getString(R.string.q_value), getString(R.string.q_value_desc))
        }
        
        binding.sessionInfoIcon.setOnClickListener {
            showInfoDialog(getString(R.string.session), getString(R.string.session_desc))
        }
        
        binding.antennaInfoIcon.setOnClickListener {
            showInfoDialog(getString(R.string.antenna), getString(R.string.antenna_desc))
        }
        
        binding.filterTimeInfoIcon.setOnClickListener {
            showInfoDialog("Tiempo de Filtro", getString(R.string.filter_time_desc))
        }
        
        binding.buzzerTimeInfoIcon.setOnClickListener {
            showInfoDialog("Tiempo de Buzzer", getString(R.string.buzzer_time_desc))
        }
        
        binding.pollingIntervalInfoIcon.setOnClickListener {
            showInfoDialog("Intervalo de Polling", getString(R.string.polling_interval_desc))
        }
        
        binding.workModeInfoIcon.setOnClickListener {
            showInfoDialog("Modo de Trabajo", getString(R.string.work_mode_desc))
        }
        
        binding.inquiryAreaInfoIcon.setOnClickListener {
            showInfoDialog("Área de Consulta", getString(R.string.inquiry_area_desc))
        }
    }
    
    private fun showInfoDialog(title: String, message: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
    
    private fun saveConfig() {
        val powerLevel = binding.powerSeekBar.progress
        val qValue = binding.qValueSeekBar.progress
        val session = binding.sessionSeekBar.progress
        val antenna = binding.antennaSeekBar.progress + 1
        val filterTime = binding.filterTimeSeekBar.progress
        val buzzerTime = binding.buzzerTimeSeekBar.progress
        val pollingInterval = binding.pollingIntervalSeekBar.progress
        val workMode = binding.workModeSpinner.selectedItemPosition
        val inquiryArea = binding.inquiryAreaSpinner.selectedItemPosition + 1
        
        // Guardar en preferencias
        prefs.edit().apply {
            putInt(Constants.KEY_POWER_LEVEL, powerLevel)
            putInt(Constants.KEY_Q_VALUE, qValue)
            putInt(Constants.KEY_SESSION, session)
            putInt(Constants.KEY_ANTENNA, antenna)
            putInt(Constants.KEY_FILTER_TIME, filterTime)
            putInt(Constants.KEY_BUZZER_TIME, buzzerTime)
            putInt(Constants.KEY_POLLING_INTERVAL, pollingInterval)
            putInt(Constants.KEY_WORK_MODE, workMode)
            putInt(Constants.KEY_INQUIRY_AREA, inquiryArea)
            apply()
        }
        
        // Crear o actualizar AllParamBean
        if (allParamBean == null) {
            allParamBean = AllParamBean().apply {
                mAddr = 0x00
                mRFIDPRO = 0x00 // ISO 18000-6C
                mInterface = 0x00.toByte()
                mBaudrate = 0x04
                mWGSet = 0x00
                
                // Inicializar RfidFreq con valores por defecto (US)
                mRfidFreq = AllParamBean.RfidFreq().apply {
                    mREGION = 0x01.toByte() // US
                    mSTRATFREI = byteArrayOf(0x03, 0x86.toByte())
                    mSTRATFRED = byteArrayOf(0x02, 0xEE.toByte())
                    mSTEPFRE = byteArrayOf(0x01, 0xF4.toByte())
                    mCN = 0x32
                }
                
                mAcsAddr = 0x00
                mAcsDataLen = 0x00
                mTriggerTime = 1
            }
        }
        
        // Actualizar parámetros
        allParamBean?.apply {
            mRfidPower = powerLevel.toByte()
            mQValue = qValue.toByte()
            mSession = session.toByte()
            mAnt = (1 shl (antenna - 1)).toByte() // Bitmask para antena
            mFilterTime = filterTime.toByte()
            mBuzzerTime = buzzerTime.toByte()
            mPollingInterval = pollingInterval.toByte()
            mWorkMode = workMode.toByte()
            mInquiryArea = inquiryArea.toByte()
        }
        
        // Enviar comando completo al dispositivo
        allParamBean?.let { bean ->
            val allParamCmd = CmdBuilder.buildSetAllParamCmd(bean)
            bleCore.writeData(serviceUuid, writeUuid, allParamCmd)
            android.util.Log.d("RfidConfigActivity", "Configuración enviada: Power=$powerLevel, Q=$qValue, Session=$session")
        }
        
        Toast.makeText(this, R.string.config_saved, Toast.LENGTH_SHORT).show()
    }
    
    private fun loadDeviceConfig() {
        // Obtener configuración del dispositivo
        val getAllParamCmd = CmdBuilder.buildGetAllParamCmd()
        bleCore.writeData(serviceUuid, writeUuid, getAllParamCmd)
        
        Toast.makeText(this, "Cargando configuración del dispositivo...", Toast.LENGTH_SHORT).show()
    }
    
    override fun onNotify(cmdType: Int, cmdData: CmdData) {
        when (cmdType) {
            CmdType.TYPE_GET_ALL_PARAM -> {
                val allParam = cmdData.getData() as? AllParamBean ?: return
                if (allParam.mStatus == 0x00) {
                    allParamBean = allParam
                    runOnUiThread {
                        // Actualizar UI con los valores del dispositivo
                        allParam.mRfidPower.let { power ->
                            if (power.toInt() in 0..33) {
                                binding.powerSeekBar.progress = power.toInt().coerceAtMost(26)
                                binding.powerValueText.text = "${power.toInt()} dBm"
                            }
                        }
                        allParam.mQValue.let { q ->
                            if (q.toInt() in 0..15) {
                                binding.qValueSeekBar.progress = q.toInt()
                                binding.qValueText.text = q.toString()
                            }
                        }
                        allParam.mSession.let { s ->
                            if (s.toInt() in 0..3) {
                                binding.sessionSeekBar.progress = s.toInt()
                                binding.sessionText.text = s.toString()
                            }
                        }
                        // Determinar antena desde bitmask
                        val antValue = allParam.mAnt.toInt()
                        var antennaNum = 1
                        for (i in 0..7) {
                            if ((antValue and (1 shl i)) != 0) {
                                antennaNum = i + 1
                                break
                            }
                        }
                        binding.antennaSeekBar.progress = (antennaNum - 1).coerceIn(0, 3)
                        binding.antennaText.text = antennaNum.toString()
                        
                        allParam.mFilterTime.let { filter ->
                            binding.filterTimeSeekBar.progress = filter.toInt().and(0xFF)
                            binding.filterTimeText.text = "${filter.toInt().and(0xFF)} s"
                        }
                        allParam.mBuzzerTime.let { buzzer ->
                            binding.buzzerTimeSeekBar.progress = buzzer.toInt().and(0xFF)
                            binding.buzzerTimeText.text = buzzer.toString()
                        }
                        allParam.mPollingInterval.let { polling ->
                            binding.pollingIntervalSeekBar.progress = polling.toInt().and(0xFF)
                            binding.pollingIntervalText.text = polling.toString()
                        }
                        allParam.mWorkMode.let { mode ->
                            if (mode.toInt() in 0..2) {
                                binding.workModeSpinner.setSelection(mode.toInt())
                            }
                        }
                        allParam.mInquiryArea.let { area ->
                            val areaValue = area.toInt()
                            if (areaValue in 1..6) {
                                binding.inquiryAreaSpinner.setSelection(areaValue - 1)
                            }
                        }
                        
                        Toast.makeText(this, "Configuración cargada del dispositivo", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    
    override fun onNotify(bytes: ByteArray) {
        // No usado
    }
    
    override fun onResume() {
        super.onResume()
        bleCore.setOnNotifyCallback(this)
    }
    
    override fun onPause() {
        super.onPause()
        bleCore.setOnNotifyCallback(null)
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
