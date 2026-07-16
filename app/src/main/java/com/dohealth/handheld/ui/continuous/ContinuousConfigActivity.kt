package com.dohealth.handheld.ui.continuous

import android.os.Bundle
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dohealth.handheld.R
import com.dohealth.handheld.databinding.ActivityContinuousConfigBinding
import com.dohealth.handheld.utils.InventoryWebhookService
import com.dohealth.handheld.utils.WebhookDeviceIdentity
import kotlinx.coroutines.launch

class ContinuousConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityContinuousConfigBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityContinuousConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.continuous_webhook_config)
        binding.toolbar.setNavigationOnClickListener { finish() }

        loadConfig()
        setupClickListeners()
    }

    private fun loadConfig() {
        val config = InventoryWebhookService.resolveDefaults(
            this,
            InventoryWebhookService.readConfig(this),
        )
        binding.webhookUrlEdit.setText(config.endpoint)
        binding.apiKeyEdit.setText(config.apiKey)
        binding.systemIdEdit.setText(config.systemId)
        binding.systemNameEdit.setText(config.systemName)
        binding.readerIdEdit.setText(config.readerId)
        binding.eventIdEdit.setText(config.eventId)
    }

    private fun setupClickListeners() {
        binding.saveButton.setOnClickListener { saveConfig() }
        binding.testWebhookButton.setOnClickListener { testWebhook() }
        binding.sendMockButton.setOnClickListener { sendMock() }
        binding.verifySendReceiveButton.setOnClickListener { verifySendAndReceive() }
        binding.restoreDeviceIdsButton.setOnClickListener { restoreDeviceDefaults() }
    }

    private fun restoreDeviceDefaults() {
        binding.systemIdEdit.setText(WebhookDeviceIdentity.defaultSystemId(this))
        binding.eventIdEdit.setText(WebhookDeviceIdentity.defaultEventId(this))
        binding.systemNameEdit.setText(WebhookDeviceIdentity.defaultSystemName(this))
        binding.readerIdEdit.setText(WebhookDeviceIdentity.defaultReaderId(this))
        Toast.makeText(this, R.string.continuous_webhook_ids_restored, Toast.LENGTH_SHORT).show()
    }

    private fun currentConfig(): InventoryWebhookService.WebhookConfig {
        return InventoryWebhookService.WebhookConfig(
            endpoint = binding.webhookUrlEdit.text?.toString().orEmpty(),
            apiKey = binding.apiKeyEdit.text?.toString().orEmpty(),
            systemId = binding.systemIdEdit.text?.toString().orEmpty(),
            systemName = binding.systemNameEdit.text?.toString().orEmpty(),
            readerId = binding.readerIdEdit.text?.toString().orEmpty(),
            eventId = binding.eventIdEdit.text?.toString().orEmpty(),
        )
    }

    private fun saveConfig() {
        val config = InventoryWebhookService.resolveDefaults(this, currentConfig())
        if (config.endpoint.isBlank()) {
            Toast.makeText(this, R.string.continuous_webhook_url_required, Toast.LENGTH_SHORT).show()
            return
        }
        InventoryWebhookService.saveConfig(this, config)
        Toast.makeText(this, R.string.config_saved, Toast.LENGTH_SHORT).show()
    }

    private fun testWebhook() {
        val config = prepareConfigForAction() ?: return
        runWebhookAction(
            label = getString(R.string.continuous_webhook_test),
            action = {
                InventoryWebhookService.saveConfig(this@ContinuousConfigActivity, config)
                InventoryWebhookService.sendTestPing(this@ContinuousConfigActivity)
            },
        )
    }

    private fun sendMock() {
        val config = prepareConfigForAction() ?: return
        runWebhookAction(
            label = getString(R.string.continuous_webhook_send_mock),
            action = {
                InventoryWebhookService.saveConfig(this@ContinuousConfigActivity, config)
                InventoryWebhookService.sendMockPayload(this@ContinuousConfigActivity)
            },
        )
    }

    private fun verifySendAndReceive() {
        val config = prepareConfigForAction() ?: return
        setButtonsEnabled(false)
        binding.resultText.visibility = View.VISIBLE
        binding.resultText.text = getString(R.string.continuous_webhook_verifying)

        lifecycleScope.launch {
            InventoryWebhookService.saveConfig(this@ContinuousConfigActivity, config)
            val result = InventoryWebhookService.verifySendAndReceive(this@ContinuousConfigActivity, config)
            setButtonsEnabled(true)
            showVerificationResult(result)
        }
    }

    private fun prepareConfigForAction(): InventoryWebhookService.WebhookConfig? {
        val config = InventoryWebhookService.resolveDefaults(this, currentConfig())
        if (config.endpoint.isBlank()) {
            Toast.makeText(this, R.string.continuous_webhook_url_required, Toast.LENGTH_SHORT).show()
            return null
        }
        return config
    }

    private fun showVerificationResult(result: InventoryWebhookService.VerificationResult) {
        val summary = buildString {
            append(result.message)
            result.eventId?.let { append("\nEvent ID: $it") }
            result.postHttpCode?.let { append("\nPOST HTTP $it") }
            result.getHttpCode?.let { append("\nGET HTTP $it") }
            if (result.differences.isNotEmpty()) {
                append("\n\nDiferencias:")
                result.differences.forEach { append("\n• $it") }
            }
        }
        binding.resultText.text = summary

        val dialogMessage = buildString {
            append(summary)
            if (!result.sentJson.isNullOrBlank()) {
                append("\n\n--- ENVIADO ---\n")
                append(result.sentJson)
            }
            if (!result.receivedJson.isNullOrBlank()) {
                append("\n\n--- RECIBIDO (GET) ---\n")
                append(result.receivedJson)
            }
        }

        val scroll = ScrollView(this).apply {
            val textView = TextView(this@ContinuousConfigActivity).apply {
                text = dialogMessage
                textSize = 11f
                setTextIsSelectable(true)
                setPadding(48, 24, 48, 24)
                typeface = android.graphics.Typeface.MONOSPACE
            }
            addView(textView)
        }

        AlertDialog.Builder(this)
            .setTitle(
                if (result.success && result.matches) {
                    R.string.continuous_webhook_verify_ok
                } else {
                    R.string.continuous_webhook_verify_fail
                },
            )
            .setView(scroll)
            .setPositiveButton(android.R.string.ok, null)
            .show()

        Toast.makeText(this, summary, Toast.LENGTH_LONG).show()
    }

    private fun runWebhookAction(
        label: String,
        action: suspend () -> InventoryWebhookService.WebhookResult,
    ) {
        setButtonsEnabled(false)
        binding.resultText.visibility = View.VISIBLE
        binding.resultText.text = getString(R.string.continuous_webhook_sending)

        lifecycleScope.launch {
            val result = action()
            setButtonsEnabled(true)

            val summary = buildString {
                append(label)
                append(": ")
                if (result.success) {
                    append("✓ ")
                    append(getString(R.string.continuous_webhook_ok))
                } else {
                    append("✗ ")
                    append(result.message ?: getString(R.string.send_error))
                }
                result.httpCode?.let { append(" (HTTP $it)") }
            }
            binding.resultText.text = summary
            Toast.makeText(this@ContinuousConfigActivity, summary, Toast.LENGTH_LONG).show()
        }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        binding.testWebhookButton.isEnabled = enabled
        binding.sendMockButton.isEnabled = enabled
        binding.verifySendReceiveButton.isEnabled = enabled
        binding.restoreDeviceIdsButton.isEnabled = enabled
        binding.saveButton.isEnabled = enabled
    }
}
