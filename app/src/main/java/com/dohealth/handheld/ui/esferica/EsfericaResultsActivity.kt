package com.dohealth.handheld.ui.esferica

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.dohealth.handheld.R
import com.dohealth.handheld.data.esferica.EsfericaCountSessionStore
import com.dohealth.handheld.data.esferica.ProductCountResultDto
import com.dohealth.handheld.databinding.ActivityEsfericaResultsBinding
import com.dohealth.handheld.ui.main.MainActivity
import com.dohealth.handheld.utils.EsfericaExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EsfericaResultsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEsfericaResultsBinding
    private lateinit var store: EsfericaCountSessionStore
    private val expandedKeys = mutableSetOf<String>()

    private var pendingCsv: String? = null
    private var pendingXlsx: ByteArray? = null

    private val createCsvLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> uri?.let { saveText(it, pendingCsv) } ?: run { pendingCsv = null } }

    private val createXlsxLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    ) { uri -> uri?.let { saveBytes(it, pendingXlsx) } ?: run { pendingXlsx = null } }

    private lateinit var adapter: EsfericaProductResultsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEsfericaResultsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = EsfericaCountSessionStore(this)
        val sid =
            intent.getStringExtra(EsfericaCountActivity.EXTRA_SESSION_ID) ?: store.getActiveSessionId()
        val session =
            sid?.let { store.getSession(it) } ?: store.load()
        val reconcile = session?.lastReconcile
        if (session == null || reconcile == null) {
            Toast.makeText(this, R.string.send_error, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.esferica_results_title)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val s = reconcile.summary
        binding.summaryExpected.text = getString(R.string.esferica_sum_expected_fmt, s.expectedTotal)
        binding.summaryRead.text = getString(R.string.esferica_sum_read_fmt, s.scannedTotal)
        binding.summaryMatch.text = getString(R.string.esferica_sum_match_fmt, s.matchedTotal)
        binding.summaryMissing.text = getString(R.string.esferica_sum_miss_fmt, s.missingTotal)
        binding.summaryExtra.text = getString(R.string.esferica_sum_extra_fmt, s.extraTotal)

        val identified = reconcile.extraIdentified.orEmpty()
        binding.identifiedExtrasText.text = if (identified.isEmpty()) {
            "—"
        } else {
            identified.joinToString("\n") { lit ->
                listOfNotNull(lit.rfid, lit.code, lit.description, lit.batch, lit.expiration)
                    .joinToString(" | ")
            }
        }
        val unknown = reconcile.extraUnknown.orEmpty()
        binding.unknownRfidsText.text = if (unknown.isEmpty()) "—" else unknown.joinToString("\n")

        adapter = EsfericaProductResultsAdapter { row ->
            val k = productKey(row.dto)
            if (expandedKeys.contains(k)) expandedKeys.remove(k) else expandedKeys.add(k)
            submitProductRows(reconcile.products.orEmpty())
        }
        binding.productsRecycler.layoutManager = LinearLayoutManager(this)
        binding.productsRecycler.adapter = adapter
        submitProductRows(reconcile.products.orEmpty())

        binding.exportCsvButton.setOnClickListener { exportCsv(session) }
        binding.exportXlsxButton.setOnClickListener { exportXlsx(session) }
        binding.backMenuButton.setOnClickListener { goMain() }
    }

    private fun productKey(p: ProductCountResultDto) =
        "${p.code ?: ""}\u0000${p.description ?: ""}"

    private fun submitProductRows(products: List<ProductCountResultDto>) {
        adapter.submitList(
            products.map { EsfericaProductRow(it, expandedKeys.contains(productKey(it))) },
        )
    }

    private fun exportCsv(session: com.dohealth.handheld.data.esferica.EsfericaCountPersistedSession) {
        lifecycleScope.launch {
            val csv = withContext(Dispatchers.Default) {
                runCatching { EsfericaExport.buildCsv(session) }.getOrNull()
            }
            if (csv == null) {
                Toast.makeText(this@EsfericaResultsActivity, R.string.export_error, Toast.LENGTH_SHORT).show()
                return@launch
            }
            pendingCsv = csv
            val name = "esferica_conteo_${System.currentTimeMillis()}.csv"
            createCsvLauncher.launch(name)
        }
    }

    private fun exportXlsx(session: com.dohealth.handheld.data.esferica.EsfericaCountPersistedSession) {
        lifecycleScope.launch {
            val bytes = withContext(Dispatchers.Default) {
                runCatching { EsfericaExport.buildXlsx(session) }.getOrNull()
            }
            if (bytes == null) {
                Toast.makeText(this@EsfericaResultsActivity, R.string.export_error, Toast.LENGTH_SHORT).show()
                return@launch
            }
            pendingXlsx = bytes
            val name = "esferica_conteo_${System.currentTimeMillis()}.xlsx"
            createXlsxLauncher.launch(name)
        }
    }

    private fun saveText(uri: Uri, text: String?) {
        if (text == null) return
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            }
            withContext(Dispatchers.Main) {
                pendingCsv = null
                Toast.makeText(this@EsfericaResultsActivity, R.string.export_success, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveBytes(uri: Uri, bytes: ByteArray?) {
        if (bytes == null) return
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            }
            withContext(Dispatchers.Main) {
                pendingXlsx = null
                Toast.makeText(this@EsfericaResultsActivity, R.string.export_success, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun goMain() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("device_name", intent.getStringExtra("device_name"))
            putExtra("device_address", intent.getStringExtra("device_address"))
        })
        finish()
    }
}
