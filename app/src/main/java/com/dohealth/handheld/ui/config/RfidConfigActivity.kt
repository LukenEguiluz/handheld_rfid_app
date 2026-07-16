package com.dohealth.handheld.ui.config

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Spinner
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
import com.dohealth.handheld.ui.continuous.ContinuousConfigActivity
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

    private val powerLabels = (0..26).map { "$it dBm" }
    private val qLabels = (0..15).map { "Q = $it" }
    private val sessionLabels = (0..3).map { "S$it" }
    private val antennaLabels = (1..4).map { "Antena $it" }
    private val filterTimeLabels = (0..255).map { "$it s" }
    private val buzzerLabels = (0..255).map { "$it (×10 ms)" }
    private val pollingLabels = (0..255).map { "$it (×10 ms)" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRfidConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.config_title)
        binding.toolbar.setNavigationOnClickListener { finish() }

        bleCore = CfSdk.get(SdkC.BLE)
        prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)

        setupSpinners()
        loadSavedConfig()
        setupClickListeners()
    }

    private fun setupSpinners() {
        bindSpinner(binding.powerSpinner, powerLabels)
        bindSpinner(binding.qValueSpinner, qLabels)
        bindSpinner(binding.sessionSpinner, sessionLabels)
        bindSpinner(binding.antennaSpinner, antennaLabels)
        bindSpinner(binding.filterTimeSpinner, filterTimeLabels)
        bindSpinner(binding.buzzerTimeSpinner, buzzerLabels)
        bindSpinner(binding.pollingIntervalSpinner, pollingLabels)

        bindSpinner(
            binding.workModeSpinner,
            arrayOf("Respuesta (0)", "Activo (1)", "Trigger (2)"),
        )
        bindSpinner(
            binding.inquiryAreaSpinner,
            arrayOf(
                "EPC (0x01)",
                "TID (0x02)",
                "USER (0x03)",
                "EPC+TID (0x04)",
                "EPC+USER (0x05)",
                "EPC+TID+USER (0x06)",
            ),
        )
    }

    private fun bindSpinner(spinner: Spinner, labels: List<String>) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
    }

    private fun bindSpinner(spinner: Spinner, labels: Array<String>) {
        bindSpinner(spinner, labels.toList())
    }

    private fun loadSavedConfig() {
        binding.powerSpinner.setSelection(
            prefs.getInt(Constants.KEY_POWER_LEVEL, Constants.DEFAULT_POWER_LEVEL).coerceIn(0, 26),
        )
        binding.qValueSpinner.setSelection(
            prefs.getInt(Constants.KEY_Q_VALUE, Constants.DEFAULT_Q_VALUE).coerceIn(0, 15),
        )
        binding.sessionSpinner.setSelection(
            prefs.getInt(Constants.KEY_SESSION, Constants.DEFAULT_SESSION).coerceIn(0, 3),
        )
        val antenna = prefs.getInt(Constants.KEY_ANTENNA, Constants.DEFAULT_ANTENNA)
        binding.antennaSpinner.setSelection((antenna - 1).coerceIn(0, 3))
        binding.filterTimeSpinner.setSelection(
            prefs.getInt(Constants.KEY_FILTER_TIME, Constants.DEFAULT_FILTER_TIME).coerceIn(0, 255),
        )
        binding.buzzerTimeSpinner.setSelection(
            prefs.getInt(Constants.KEY_BUZZER_TIME, Constants.DEFAULT_BUZZER_TIME).coerceIn(0, 255),
        )
        binding.pollingIntervalSpinner.setSelection(
            prefs.getInt(Constants.KEY_POLLING_INTERVAL, Constants.DEFAULT_POLLING_INTERVAL).coerceIn(0, 255),
        )
        binding.workModeSpinner.setSelection(
            prefs.getInt(Constants.KEY_WORK_MODE, Constants.DEFAULT_WORK_MODE).coerceIn(0, 2),
        )
        binding.inquiryAreaSpinner.setSelection(
            (prefs.getInt(Constants.KEY_INQUIRY_AREA, Constants.DEFAULT_INQUIRY_AREA) - 1).coerceIn(0, 5),
        )
    }

    private fun setupClickListeners() {
        binding.saveButton.setOnClickListener { saveConfig() }
        binding.loadButton.setOnClickListener { loadDeviceConfig() }
        binding.webhookConfigButton.setOnClickListener {
            startActivity(android.content.Intent(this, ContinuousConfigActivity::class.java))
        }
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
        val powerLevel = binding.powerSpinner.selectedItemPosition
        val qValue = binding.qValueSpinner.selectedItemPosition
        val session = binding.sessionSpinner.selectedItemPosition
        val antenna = binding.antennaSpinner.selectedItemPosition + 1
        val filterTime = binding.filterTimeSpinner.selectedItemPosition
        val buzzerTime = binding.buzzerTimeSpinner.selectedItemPosition
        val pollingInterval = binding.pollingIntervalSpinner.selectedItemPosition
        val workMode = binding.workModeSpinner.selectedItemPosition
        val inquiryArea = binding.inquiryAreaSpinner.selectedItemPosition + 1

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

        if (allParamBean == null) {
            allParamBean = AllParamBean().apply {
                mAddr = 0x00
                mRFIDPRO = 0x00
                mInterface = 0x00.toByte()
                mBaudrate = 0x04
                mWGSet = 0x00
                mRfidFreq = AllParamBean.RfidFreq().apply {
                    mREGION = 0x01.toByte()
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

        allParamBean?.apply {
            mRfidPower = powerLevel.toByte()
            mQValue = qValue.toByte()
            mSession = session.toByte()
            mAnt = (1 shl (antenna - 1)).toByte()
            mFilterTime = filterTime.toByte()
            mBuzzerTime = buzzerTime.toByte()
            mPollingInterval = pollingInterval.toByte()
            mWorkMode = workMode.toByte()
            mInquiryArea = inquiryArea.toByte()
        }

        allParamBean?.let { bean ->
            val allParamCmd = CmdBuilder.buildSetAllParamCmd(bean)
            bleCore.writeData(serviceUuid, writeUuid, allParamCmd)
            android.util.Log.d(
                "RfidConfigActivity",
                "Configuración enviada: Power=$powerLevel, Q=$qValue, Session=$session",
            )
        }

        Toast.makeText(this, R.string.config_saved, Toast.LENGTH_SHORT).show()
    }

    private fun loadDeviceConfig() {
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
                        allParam.mRfidPower.let { power ->
                            if (power.toInt() in 0..26) {
                                binding.powerSpinner.setSelection(power.toInt())
                            }
                        }
                        allParam.mQValue.let { q ->
                            if (q.toInt() in 0..15) {
                                binding.qValueSpinner.setSelection(q.toInt())
                            }
                        }
                        allParam.mSession.let { s ->
                            if (s.toInt() in 0..3) {
                                binding.sessionSpinner.setSelection(s.toInt())
                            }
                        }
                        val antValue = allParam.mAnt.toInt()
                        var antennaNum = 1
                        for (i in 0..7) {
                            if ((antValue and (1 shl i)) != 0) {
                                antennaNum = i + 1
                                break
                            }
                        }
                        binding.antennaSpinner.setSelection((antennaNum - 1).coerceIn(0, 3))

                        binding.filterTimeSpinner.setSelection(allParam.mFilterTime.toInt().and(0xFF))
                        binding.buzzerTimeSpinner.setSelection(allParam.mBuzzerTime.toInt().and(0xFF))
                        binding.pollingIntervalSpinner.setSelection(allParam.mPollingInterval.toInt().and(0xFF))

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
