package com.dohealth.handheld.ui.esferica

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.dohealth.handheld.R
import com.dohealth.handheld.data.esferica.EsfericaCountPersistedSession
import com.dohealth.handheld.data.esferica.EsfericaCountSessionStore
import com.dohealth.handheld.databinding.ActivityEsfericaSessionsBinding

class EsfericaSessionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEsfericaSessionsBinding
    private lateinit var store: EsfericaCountSessionStore
    private lateinit var adapter: EsfericaSessionsAdapter

    private var deviceName: String = ""
    private var deviceAddress: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEsfericaSessionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        deviceName = intent.getStringExtra("device_name") ?: "Dispositivo"
        deviceAddress = intent.getStringExtra("device_address") ?: ""

        store = EsfericaCountSessionStore(this)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        supportActionBar?.title = getString(R.string.esferica_sessions_title)

        adapter = EsfericaSessionsAdapter(
            onOpen = { openSession(it) },
            onDelete = { confirmDelete(it) },
        )
        binding.sessionsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.sessionsRecyclerView.adapter = adapter

        binding.newSessionButton.setOnClickListener {
            startActivity(Intent(this, EsfericaSelectionActivity::class.java).apply {
                putExtra("device_name", deviceName)
                putExtra("device_address", deviceAddress)
            })
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val sessions = store.listSessions()
        adapter.submitList(sessions)
        val empty = sessions.isEmpty()
        binding.emptyText.visibility = if (empty) View.VISIBLE else View.GONE
        binding.sessionsRecyclerView.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun openSession(s: EsfericaCountPersistedSession) {
        store.setActiveSessionId(s.sessionId)
        startActivity(Intent(this, EsfericaCountActivity::class.java).apply {
            putExtra(EsfericaCountActivity.EXTRA_SESSION_ID, s.sessionId)
            putExtra("device_name", deviceName)
            putExtra("device_address", deviceAddress)
        })
    }

    private fun confirmDelete(s: EsfericaCountPersistedSession) {
        AlertDialog.Builder(this)
            .setTitle(R.string.esferica_session_delete_confirm_title)
            .setMessage(R.string.esferica_session_delete_confirm_message)
            .setPositiveButton(R.string.relacion_delete) { _, _ ->
                store.delete(s.sessionId)
                render()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
