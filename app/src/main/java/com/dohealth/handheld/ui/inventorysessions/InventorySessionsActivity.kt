package com.dohealth.handheld.ui.inventorysessions

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.dohealth.handheld.R
import com.dohealth.handheld.data.inventory.InventorySession
import com.dohealth.handheld.data.inventory.InventorySessionStore
import com.dohealth.handheld.databinding.ActivitySessionsListBinding
import com.dohealth.handheld.ui.inventory.InventoryActivity

class InventorySessionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySessionsListBinding
    private lateinit var store: InventorySessionStore
    private lateinit var adapter: InventorySessionsAdapter

    private var deviceName: String = ""
    private var deviceAddress: String = ""
    private var mode: Int = InventoryActivity.MODE_RFID
    private var sessionModeKey: String? = null
    private var titleOverride: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySessionsListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        deviceName = intent.getStringExtra("device_name") ?: "Dispositivo"
        deviceAddress = intent.getStringExtra("device_address") ?: ""
        mode = intent.getIntExtra(EXTRA_MODE_INT, InventoryActivity.MODE_RFID)
        sessionModeKey = intent.getStringExtra(EXTRA_SESSION_MODE_KEY)
        titleOverride = intent.getStringExtra(EXTRA_TITLE)

        store = InventorySessionStore(this)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        supportActionBar?.title = titleOverride ?: getString(
            R.string.inventory_sessions_title,
            if (mode == InventoryActivity.MODE_RFID) getString(R.string.mode_rfid) else getString(R.string.mode_barcode)
        )
        binding.emptyText.text = getString(R.string.inventory_no_sessions)
        binding.newSessionButton.text = getString(R.string.inventory_new_session)

        adapter = InventorySessionsAdapter(
            onOpen = { openSession(it) },
            onDelete = { confirmDelete(it) }
        )
        binding.sessionsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.sessionsRecyclerView.adapter = adapter

        binding.newSessionButton.setOnClickListener {
            val session = store.createSession(modeString())
            openSession(session)
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val sessions = store.listSessions(modeString())
        adapter.submitList(sessions)
        val isEmpty = sessions.isEmpty()
        binding.emptyText.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.sessionsRecyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun openSession(session: InventorySession) {
        val totalScans = session.items.size
        val uniqueCount = session.items.asSequence().map { it.data }.distinct().count()
        val msg = if (mode == InventoryActivity.MODE_RFID) {
            "Reanudando: Tags $uniqueCount | Escaneos $totalScans"
        } else {
            "Reanudando: Productos $uniqueCount | Escaneos $totalScans"
        }
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, InventoryActivity::class.java).apply {
            putExtra("mode", mode)
            putExtra("device_name", deviceName)
            putExtra("device_address", deviceAddress)
            putExtra(InventoryActivity.EXTRA_SESSION_ID, session.id)
        })
    }

    private fun confirmDelete(session: InventorySession) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.relacion_confirm_delete_title))
            .setMessage(getString(R.string.relacion_confirm_delete_message))
            .setPositiveButton(getString(R.string.relacion_delete)) { _, _ ->
                store.deleteSession(session.id)
                render()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun modeString(): String = sessionModeKey ?: if (mode == InventoryActivity.MODE_RFID) "RFID" else "BARCODE"

    companion object {
        const val EXTRA_MODE_INT = "inventory_sessions_mode"
        const val EXTRA_SESSION_MODE_KEY = "inventory_session_mode_key"
        const val EXTRA_TITLE = "inventory_sessions_title"
    }
}

