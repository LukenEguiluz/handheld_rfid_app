package com.dohealth.handheld.ui.history

import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.dohealth.handheld.R
import com.dohealth.handheld.data.history.ScanHistoryStore
import com.dohealth.handheld.databinding.ActivityScanHistoryBinding

class ScanHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanHistoryBinding
    private lateinit var store: ScanHistoryStore
    private lateinit var adapter: ScanHistoryAdapter

    private var mode: String = MODE_RFID
    private var pendingExport: String? = null

    private val createDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri == null) return@registerForActivityResult
            try {
                val text = pendingExport ?: return@registerForActivityResult
                contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(text.toByteArray(Charsets.UTF_8))
                }
                pendingExport = null
                Toast.makeText(this, R.string.history_export_success, Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(this, R.string.history_export_error, Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_RFID
        store = ScanHistoryStore(this)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        supportActionBar?.title = getString(R.string.history_title, mode)

        adapter = ScanHistoryAdapter()
        binding.itemsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.itemsRecyclerView.adapter = adapter

        binding.clearButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.history_clear_title)
                .setMessage(R.string.history_clear_message)
                .setPositiveButton(R.string.relacion_delete) { d, _ ->
                    store.clear(mode)
                    render()
                    d.dismiss()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        binding.exportButton.setOnClickListener {
            val list = store.list(mode).sortedByDescending { it.timestamp }
            if (list.isEmpty()) {
                Toast.makeText(this, R.string.no_data, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            pendingExport = buildTxt(list)
            createDocumentLauncher.launch("historial_${mode.lowercase()}_${System.currentTimeMillis()}.txt")
        }

        render()
    }

    private fun render() {
        val list = store.list(mode).sortedByDescending { it.timestamp }
        adapter.submitList(list)
        binding.countText.text = getString(R.string.history_count, list.size)
        binding.emptyText.visibility = if (list.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        binding.itemsRecyclerView.visibility = if (list.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun buildTxt(list: List<com.dohealth.handheld.data.history.ScanHistoryEntry>): String {
        val sb = StringBuilder()
        sb.append("MODO: ").append(mode).append('\n')
        sb.append("TOTAL: ").append(list.size).append('\n')
        sb.append('\n')
        sb.append("TIMESTAMP\tCODIGO\n")
        for (e in list.sortedBy { it.timestamp }) {
            sb.append(e.timestamp).append('\t').append(e.code).append('\n')
        }
        return sb.toString()
    }

    companion object {
        const val EXTRA_MODE = "scan_history_mode"
        const val MODE_RFID = "RFID"
        const val MODE_BARCODE = "BARCODE"
    }
}

