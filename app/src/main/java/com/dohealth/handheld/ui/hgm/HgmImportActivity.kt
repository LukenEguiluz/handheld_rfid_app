package com.dohealth.handheld.ui.hgm

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dohealth.handheld.R
import com.dohealth.handheld.data.esferica.ExpectedInventoryItemDto
import com.dohealth.handheld.data.hgm.HgmCountPersistedSession
import com.dohealth.handheld.data.hgm.HgmCountSessionStore
import com.dohealth.handheld.databinding.ActivityHgmImportBinding
import com.dohealth.handheld.domain.HgmCatalogMapper
import com.dohealth.handheld.utils.DeterminateLoadingProgress
import com.dohealth.handheld.utils.HgmCatalogImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HgmImportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHgmImportBinding
    private lateinit var store: HgmCountSessionStore

    private var pickedUri: Uri? = null
    private var pickedDisplayName: String? = null
    private var expectedItems: List<ExpectedInventoryItemDto> = emptyList()
    private var importJob: Job? = null

    private val pickCatalogLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        pickedUri = uri
        kotlin.runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val name = contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) c.getString(idx) else null
            } else null
        }
        pickedDisplayName = name ?: uri.lastPathSegment ?: "catalogo"
        binding.selectedFileText.text = pickedDisplayName
        parseCatalog(uri, pickedDisplayName)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHgmImportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = HgmCountSessionStore(this)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        supportActionBar?.title = getString(R.string.hgm_import_title)

        binding.pickCatalogButton.setOnClickListener {
            pickCatalogLauncher.launch(
                arrayOf(
                    "text/csv",
                    "text/comma-separated-values",
                    "application/vnd.ms-excel",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/*",
                ),
            )
        }

        binding.startCountButton.setOnClickListener { startCount() }
    }

    private fun parseCatalog(uri: Uri, displayName: String?) {
        importJob?.cancel()
        binding.startCountButton.isEnabled = false
        binding.importStatusText.text = getString(R.string.hgm_import_parsing)
        showImportOverlay(true)

        importJob = lifecycleScope.launch {
            val outcome = coroutineScope {
                val workDeferred = async(Dispatchers.Default) {
                    val result = HgmCatalogImporter.importFromUri(
                        this@HgmImportActivity,
                        uri,
                        displayName,
                    )
                    val (items, mapWarnings) = HgmCatalogMapper.toExpectedItems(result.rows)
                    Triple(result, items, mapWarnings)
                }
                val rampDeferred = async {
                    DeterminateLoadingProgress.rampSegmentEaseOut(
                        binding.importBootLinearProgress,
                        binding.importBootProgressPercentText,
                        from = 0,
                        to = 95,
                        durationMs = 1_200L,
                    )
                }
                rampDeferred.await()
                workDeferred.await()
            }

            val (result, items, mapWarnings) = outcome
            val warnings = result.warnings + mapWarnings
            expectedItems = items
            val dupSku = result.rows
                .map { it.sku.trim() }
                .filter { it.isNotEmpty() }
                .groupingBy { it }
                .eachCount()
                .count { it.value > 1 }
            val status = buildString {
                append(getString(R.string.hgm_import_rows_ok, items.size))
                append("\n")
                append(getString(R.string.hgm_import_source_rows, result.rows.size))
                if (result.skippedRows > 0) {
                    append("\n")
                    append(getString(R.string.hgm_import_rows_skipped, result.skippedRows))
                }
                if (dupSku > 0) {
                    append("\n")
                    append(getString(R.string.hgm_import_duplicate_sku_warning, dupSku))
                }
                if (warnings.isNotEmpty()) {
                    append("\n")
                    append(warnings.take(5).joinToString("\n"))
                    if (warnings.size > 5) append("\n…")
                }
            }

            DeterminateLoadingProgress.ensureHeldAt(
                binding.importBootLinearProgress,
                binding.importBootProgressPercentText,
            )
            DeterminateLoadingProgress.flashFullProgress(
                binding.importBootLinearProgress,
                binding.importBootProgressPercentText,
            )
            showImportOverlay(false)

            binding.importStatusText.text = status
            binding.startCountButton.isEnabled = items.isNotEmpty()
            if (items.isEmpty()) {
                Toast.makeText(this@HgmImportActivity, R.string.hgm_import_no_valid_rows, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showImportOverlay(visible: Boolean) {
        binding.importBootOverlay.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible) {
            binding.importBootLinearProgress.setProgressCompat(0, false)
            binding.importBootProgressPercentText.text = "0%"
        }
    }

    private fun startCount() {
        val items = expectedItems
        if (items.isEmpty()) return
        val label = binding.sessionLabelEdit.text?.toString()?.trim().orEmpty().ifBlank { null }
        val fileName = pickedDisplayName ?: "catalogo.csv"
        val session = HgmCountPersistedSession(
            catalogFileName = fileName,
            sessionLabel = label,
            expectedItems = items,
            scannedRfidsOrdered = emptyList(),
        )
        binding.startCountButton.isEnabled = false
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { store.upsert(session) }
            startActivity(Intent(this@HgmImportActivity, HgmCountActivity::class.java).apply {
                putExtra(HgmCountActivity.EXTRA_SESSION_ID, session.sessionId)
                putExtra("device_name", intent.getStringExtra("device_name"))
                putExtra("device_address", intent.getStringExtra("device_address"))
            })
            finish()
        }
    }

    override fun onDestroy() {
        importJob?.cancel()
        super.onDestroy()
    }
}
