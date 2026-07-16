package com.dohealth.handheld.ui.continuous

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
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
import com.dohealth.handheld.data.model.ContinuousTag
import com.dohealth.handheld.databinding.ActivityContinuousReadingBinding
import com.dohealth.handheld.utils.Constants
import com.dohealth.handheld.utils.InventoryWebhookService
import com.dohealth.handheld.utils.RfidDistanceDialog
import com.dohealth.handheld.utils.RfidDistancePreset
import com.dohealth.handheld.utils.RfidDistancePresetHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

class ContinuousReadingActivity : AppCompatActivity(), IOnNotifyCallback {

    private lateinit var binding: ActivityContinuousReadingBinding
    private lateinit var bleCore: BleCore
    private lateinit var adapter: ContinuousReadingAdapter

    private val activeTags = linkedMapOf<String, ContinuousTag>()
    private val serviceUuid = UUID.fromString(Constants.SERVICE_UUID)
    private val writeUuid = UUID.fromString(Constants.WRITE_UUID)

    private var isInventoryRunning = false
    private var isTriggerHoldMode = false
    private var expirationJob: Job? = null
    private var totalReadCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityContinuousReadingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bleCore = CfSdk.get(SdkC.BLE)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.mode_continuous)
        binding.toolbar.setNavigationOnClickListener { handleBackPress() }

        setupRecyclerView()
        setupClickListeners()
        checkTriggerHoldMode()
        updateWebhookStatus()
        updateCounts()
        updateStartStopButton()
        setDeviceMode()
        ensureExpirationCheckerRunning()
    }

    private fun setupRecyclerView() {
        adapter = ContinuousReadingAdapter()
        binding.itemsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.itemsRecyclerView.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.startStopButton.setOnClickListener {
            if (isInventoryRunning) stopInventory() else startInventory()
        }
        binding.reloadButton.setOnClickListener { clearData() }
    }

    private fun checkTriggerHoldMode() {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        val workMode = prefs.getInt(Constants.KEY_WORK_MODE, Constants.DEFAULT_WORK_MODE)
        isTriggerHoldMode = workMode != 2
    }

    private fun updateWebhookStatus() {
        val config = InventoryWebhookService.resolveDefaults(
            this,
            InventoryWebhookService.readConfig(this),
        )
        binding.webhookStatusText.text = if (config.endpoint.isBlank()) {
            getString(R.string.continuous_webhook_disabled)
        } else {
            getString(R.string.continuous_webhook_enabled, config.endpoint)
        }
    }

    private fun setDeviceMode() {
        val modeCmd = CmdBuilder.buildSetReadModeCmd(0x00, ByteArray(7))
        bleCore.writeData(serviceUuid, writeUuid, modeCmd)
    }

    private fun startInventory() {
        if (isInventoryRunning) return

        bleCore.setOnNotifyCallback(this)
        isInventoryRunning = true
        binding.statusText.text = getString(R.string.inventory_running)
        updateStartStopButton()

        setDeviceMode()
        RfidDistancePresetHelper.syncSavedPresetToDevice(this, bleCore, serviceUuid, writeUuid)

        binding.root.postDelayed({
            val inventoryCmd = CmdBuilder.buildInventoryISOContinueCmd(0x00, 0)
            bleCore.writeData(serviceUuid, writeUuid, inventoryCmd)
        }, 500)

        ensureExpirationCheckerRunning()
    }

    private fun stopInventory() {
        if (!isInventoryRunning) return

        isInventoryRunning = false
        binding.statusText.text = getString(R.string.inventory_stopped)
        updateStartStopButton()

        val stopCmd = CmdBuilder.buildStopInventoryCmd()
        bleCore.writeData(serviceUuid, writeUuid, stopCmd)
    }

    private fun ensureExpirationCheckerRunning() {
        if (expirationJob?.isActive == true) return
        expirationJob = lifecycleScope.launch {
            while (isActive) {
                delay(1000)
                expireStaleTags()
            }
        }
    }

    private fun expireStaleTags() {
        val now = System.currentTimeMillis()
        val stale = activeTags.filter { now - it.value.lastSeen > Constants.CONTINUOUS_TAG_TIMEOUT_MS }
            .keys
            .toList()
        if (stale.isEmpty()) return

        stale.forEach { activeTags.remove(it) }
        if (activeTags.isEmpty()) {
            totalReadCount = 0
        }
        updateTagList()
        sendWebhookUpdate(added = emptyList(), removed = stale)
    }

    private fun updateStartStopButton() {
        if (isInventoryRunning) {
            binding.startStopButton.text = getString(R.string.stop_inventory)
            binding.startStopButton.backgroundTintList = android.content.res.ColorStateList.valueOf(
                resources.getColor(R.color.primary_gray, theme),
            )
        } else {
            binding.startStopButton.text = getString(R.string.start_inventory)
            binding.startStopButton.backgroundTintList = android.content.res.ColorStateList.valueOf(
                resources.getColor(R.color.primary_red, theme),
            )
        }
    }

    private fun updateCounts() {
        val uniqueCount = activeTags.size
        binding.tagsCountText.text = getString(R.string.tags_count, uniqueCount)
        binding.scansCountText.text = getString(R.string.continuous_reads_count, totalReadCount)
    }

    private fun updateTagList() {
        val sorted = activeTags.values.sortedByDescending { it.lastSeen }
        adapter.submitList(sorted)
        updateCounts()

        if (sorted.isEmpty()) {
            binding.emptyListText.visibility = android.view.View.VISIBLE
            binding.itemsRecyclerView.visibility = android.view.View.GONE
        } else {
            binding.emptyListText.visibility = android.view.View.GONE
            binding.itemsRecyclerView.visibility = android.view.View.VISIBLE
            binding.itemsRecyclerView.post {
                binding.itemsRecyclerView.smoothScrollToPosition(0)
            }
        }
    }

    private fun clearData() {
        activeTags.clear()
        totalReadCount = 0
        adapter.submitList(emptyList())
        updateCounts()
        binding.emptyListText.visibility = android.view.View.VISIBLE
        binding.itemsRecyclerView.visibility = android.view.View.GONE
    }

    private fun handleRfidTag(cmdData: CmdData) {
        val tagInfo = cmdData.getData() as? TagInfoBean ?: return
        if (tagInfo.mStatus != 0x00 || tagInfo.mEPCNum == null) return

        val epcHex = FormatUtil.bytesToHexStr(tagInfo.mEPCNum).replace(" ", "")
        val rssi = tagInfo.mRSSI
        val antenna = tagInfo.mAntenna
        val now = System.currentTimeMillis()

        runOnUiThread {
            totalReadCount++
            val existing = activeTags[epcHex]
            if (existing == null) {
                activeTags[epcHex] = ContinuousTag(
                    epc = epcHex,
                    rssi = rssi,
                    antenna = antenna,
                    readCount = 1,
                    lastSeen = now,
                )
                updateTagList()
                sendWebhookUpdate(added = listOf(epcHex), removed = emptyList())
            } else {
                activeTags[epcHex] = existing.copy(
                    rssi = rssi,
                    antenna = antenna,
                    readCount = existing.readCount + 1,
                    lastSeen = now,
                    missedCycles = 0,
                )
                updateTagList()
            }
        }
    }

    private fun sendWebhookUpdate(added: List<String>, removed: List<String>) {
        val config = InventoryWebhookService.readConfig(this)
        if (config.endpoint.isBlank()) return

        lifecycleScope.launch {
            val result = InventoryWebhookService.sendInventoryUpdate(
                context = this@ContinuousReadingActivity,
                activeTags = activeTags,
                added = added,
                removed = removed,
            )
            if (!result.success) {
                android.util.Log.w("ContinuousReading", "Webhook falló: ${result.message}")
            }
        }
    }

    private fun setDeviceToStandby() {
        if (isInventoryRunning) {
            val stopCmd = CmdBuilder.buildStopInventoryCmd()
            bleCore.writeData(serviceUuid, writeUuid, stopCmd)
            isInventoryRunning = false
        }
        lifecycleScope.launch {
            delay(200)
            val rfidModeCmd = CmdBuilder.buildSetReadModeCmd(0x00, ByteArray(7))
            bleCore.writeData(serviceUuid, writeUuid, rfidModeCmd)
        }
    }

    override fun onNotify(cmdType: Int, cmdData: CmdData) {
        when (cmdType) {
            CmdType.TYPE_INVENTORY -> handleRfidTag(cmdData)
            CmdType.TYPE_KEY_STATE -> handleTriggerKeyState(cmdData)
        }
    }

    private fun handleTriggerKeyState(cmdData: CmdData) {
        if (!isTriggerHoldMode) return
        val keyState = cmdData.getData() as? KeyStateBean ?: return
        val keyStateValue = keyState.mKeyState.toInt()

        runOnUiThread {
            when (keyStateValue) {
                0x01 -> if (!isInventoryRunning) startInventory()
                0x02 -> if (isInventoryRunning) stopInventory()
            }
        }
    }

    override fun onNotify(bytes: ByteArray) {
        // Solo RFID en este modo
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.continuous_reading_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                handleBackPress()
                true
            }
            R.id.menu_rfid_distance -> {
                showRfidDistanceDialog()
                true
            }
            R.id.menu_webhook_config -> {
                startActivity(Intent(this, ContinuousConfigActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showRfidDistanceDialog() {
        RfidDistanceDialog.show(this) { preset ->
            applyRfidDistancePreset(preset)
        }
    }

    private fun applyRfidDistancePreset(preset: RfidDistancePreset) {
        val wasRunning = isInventoryRunning
        RfidDistancePresetHelper.apply(
            context = this,
            bleCore = bleCore,
            serviceUuid = serviceUuid,
            writeUuid = writeUuid,
            preset = preset,
            wasReading = wasRunning,
        ) {
            binding.root.postDelayed({
                val inventoryCmd = CmdBuilder.buildInventoryISOContinueCmd(0x00, 0)
                bleCore.writeData(serviceUuid, writeUuid, inventoryCmd)
            }, 450)
        }
    }

    override fun onResume() {
        super.onResume()
        bleCore.setOnNotifyCallback(this)
        setDeviceMode()
        checkTriggerHoldMode()
        updateWebhookStatus()
        ensureExpirationCheckerRunning()
    }

    override fun onPause() {
        super.onPause()
        setDeviceToStandby()
    }

    private fun handleBackPress() {
        setDeviceToStandby()
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        handleBackPress()
    }

    override fun onDestroy() {
        super.onDestroy()
        expirationJob?.cancel()
        setDeviceToStandby()
        bleCore.setOnNotifyCallback(null)
    }
}
