package com.dohealth.handheld.ui.relacion

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.dohealth.handheld.R
import com.dohealth.handheld.data.relacion.RelacionSession
import com.dohealth.handheld.data.relacion.RelacionSessionStore
import com.dohealth.handheld.databinding.ActivityRelacionSessionsBinding

class RelacionSessionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRelacionSessionsBinding
    private lateinit var store: RelacionSessionStore
    private lateinit var adapter: RelacionSessionsAdapter

    private var deviceName: String = ""
    private var deviceAddress: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRelacionSessionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        deviceName = intent.getStringExtra("device_name") ?: "Dispositivo"
        deviceAddress = intent.getStringExtra("device_address") ?: ""

        store = RelacionSessionStore(this)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        supportActionBar?.title = getString(R.string.relacion_sessions_title)

        adapter = RelacionSessionsAdapter(
            onOpen = { openSession(it) },
            onDelete = { confirmDelete(it) }
        )

        binding.sessionsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.sessionsRecyclerView.adapter = adapter

        binding.newSessionButton.setOnClickListener {
            val session = store.createSession()
            openSession(session)
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val sessions = store.listSessions()
        adapter.submitList(sessions)

        val isEmpty = sessions.isEmpty()
        binding.emptyText.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.sessionsRecyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun openSession(session: RelacionSession) {
        startActivity(Intent(this, RelacionPorCodigosActivity::class.java).apply {
            putExtra(RelacionPorCodigosActivity.EXTRA_SESSION_ID, session.id)
            putExtra("device_name", deviceName)
            putExtra("device_address", deviceAddress)
        })
    }

    private fun confirmDelete(session: RelacionSession) {
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
}

