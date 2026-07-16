package com.dohealth.handheld.ui.hgm

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.dohealth.handheld.R
import com.dohealth.handheld.data.hgm.HgmCountPersistedSession
import com.dohealth.handheld.data.hgm.HgmCountSessionStore
import com.dohealth.handheld.databinding.ActivitySessionsListBinding

class HgmSessionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySessionsListBinding
    private lateinit var store: HgmCountSessionStore
    private lateinit var adapter: HgmSessionsAdapter

    private var deviceName: String = ""
    private var deviceAddress: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySessionsListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        deviceName = intent.getStringExtra("device_name") ?: "Dispositivo"
        deviceAddress = intent.getStringExtra("device_address") ?: ""

        store = HgmCountSessionStore(this)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        supportActionBar?.title = getString(R.string.hgm_sessions_title)
        binding.emptyText.text = getString(R.string.hgm_sessions_empty)
        binding.newSessionButton.text = getString(R.string.hgm_new_count_session)

        adapter = HgmSessionsAdapter(
            onOpen = { openSession(it) },
            onDelete = { confirmDelete(it) },
        )
        binding.sessionsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.sessionsRecyclerView.adapter = adapter

        binding.newSessionButton.setOnClickListener {
            startActivity(Intent(this, HgmImportActivity::class.java).apply {
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

    private fun openSession(s: HgmCountPersistedSession) {
        store.setActiveSessionId(s.sessionId)
        startActivity(Intent(this, HgmCountActivity::class.java).apply {
            putExtra(HgmCountActivity.EXTRA_SESSION_ID, s.sessionId)
            putExtra("device_name", deviceName)
            putExtra("device_address", deviceAddress)
        })
    }

    private fun confirmDelete(s: HgmCountPersistedSession) {
        AlertDialog.Builder(this)
            .setTitle(R.string.hgm_session_delete_confirm_title)
            .setMessage(R.string.hgm_session_delete_confirm_message)
            .setPositiveButton(R.string.relacion_delete) { _, _ ->
                store.delete(s.sessionId)
                render()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
