package com.dohealth.handheld.ui.esferica

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.Toast
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.dohealth.handheld.R
import com.dohealth.handheld.data.esferica.EsfericaClientDto
import com.dohealth.handheld.data.esferica.EsfericaCountPersistedSession
import com.dohealth.handheld.data.esferica.EsfericaCountSessionStore
import com.dohealth.handheld.data.esferica.EsfericaRepository
import com.dohealth.handheld.data.esferica.EsfericaWarehouseDto
import com.dohealth.handheld.databinding.ActivityEsfericaSelectionBinding
import com.dohealth.handheld.network.EsfericaApiClient
import com.dohealth.handheld.utils.Constants
import com.dohealth.handheld.utils.DeterminateLoadingProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID

private data class LabeledClient(val dto: EsfericaClientDto, val label: String)

private data class LabeledWarehouse(val dto: EsfericaWarehouseDto, val label: String)

class EsfericaSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEsfericaSelectionBinding
    private lateinit var store: EsfericaCountSessionStore

    private var clients: List<EsfericaClientDto> = emptyList()
    private var warehouses: List<EsfericaWarehouseDto> = emptyList()
    private var clientChoices: List<LabeledClient> = emptyList()
    private var warehouseChoices: List<LabeledWarehouse> = emptyList()

    private var catalogJob: Job? = null
    private var inventoryJob: Job? = null

    private var selectionFakeProgressJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEsfericaSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = EsfericaCountSessionStore(this)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.esferica_physical_count_title)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        val savedBase =
            prefs.getString(Constants.KEY_ESFERICA_API_BASE, Constants.DEFAULT_ESFERICA_API_BASE)
                ?: Constants.DEFAULT_ESFERICA_API_BASE
        binding.esfericaBaseUrlEdit.setText(EsfericaApiClient.normalizeBaseUrl(savedBase))

        binding.retryLoadButton.setOnClickListener { loadCatalog() }
        binding.startCountButton.setOnClickListener { startPhysicalCount() }

        ContextCompat.getDrawable(this, R.drawable.spinner_dropdown_popup_light)?.let { ddBg ->
            binding.clientAutoComplete.setDropDownBackgroundDrawable(ddBg)
            binding.warehouseAutoComplete.setDropDownBackgroundDrawable(
                ddBg.constantState?.newDrawable()?.mutate() ?: ddBg,
            )
        }

        configureCatalogDropdownField(binding.clientAutoComplete)
        configureCatalogDropdownField(binding.warehouseAutoComplete)

        binding.root.post { loadCatalog() }
    }

    /**
     * Al tocar / enfocar: lista completa en el desplegable; al escribir, filtrado estándar del ArrayAdapter.
     */
    private fun configureCatalogDropdownField(actv: MaterialAutoCompleteTextView) {
        actv.threshold = 0
        actv.setOnClickListener { openDropdownShowingAll(actv) }
        actv.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) openDropdownShowingAll(actv)
        }
    }

    private fun startSelectionLoadingOverlay(@StringRes messageResId: Int) {
        if (isDestroyed) return
        binding.loadingOverlay.visibility = View.VISIBLE
        binding.loadingMessageText.setText(messageResId)
        selectionFakeProgressJob?.cancel()
        selectionFakeProgressJob = DeterminateLoadingProgress.startQuickRampThenHold(
            lifecycleScope,
            binding.loadingLinearProgress,
            binding.loadingProgressPercentText,
        )
    }

    private fun finishSelectionLoadingOverlay() {
        if (isDestroyed) return
        selectionFakeProgressJob?.cancel()
        lifecycleScope.launch {
            if (isDestroyed) return@launch
            DeterminateLoadingProgress.ensureHeldAt(
                binding.loadingLinearProgress,
                binding.loadingProgressPercentText,
            )
            DeterminateLoadingProgress.flashFullProgress(
                binding.loadingLinearProgress,
                binding.loadingProgressPercentText,
            )
            binding.loadingOverlay.visibility = View.GONE
        }
    }

    private fun openDropdownShowingAll(actv: MaterialAutoCompleteTextView) {
        @Suppress("UNCHECKED_CAST")
        val adapter = actv.adapter as? ArrayAdapter<String> ?: return
        if (adapter.count == 0) return
        actv.threshold = 0
        adapter.filter.filter(
            "",
            object : Filter.FilterListener {
                override fun onFilterComplete(count: Int) {
                    binding.root.post {
                        if (isDestroyed || count == 0) return@post
                        actv.showDropDown()
                    }
                }
            },
        )
    }

    private inline fun bindingUi(crossinline block: ActivityEsfericaSelectionBinding.() -> Unit) {
        try {
            if (isDestroyed) return
            binding.block()
        } catch (e: Exception) {
            Log.w("EsfericaSelection", "Ignorando actualización UI: ${e.message}")
        }
    }

    private fun saveBaseUrl() {
        val raw = binding.esfericaBaseUrlEdit.text?.toString()?.trim().orEmpty()
        if (raw.isBlank()) return
        val norm = EsfericaApiClient.normalizeBaseUrl(raw)
        getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE).edit()
            .putString(Constants.KEY_ESFERICA_API_BASE, norm)
            .apply()
        bindingUi { esfericaBaseUrlEdit.setText(norm) }
    }

    private fun currentApiBase(): String =
        EsfericaApiClient.normalizeBaseUrl(
            binding.esfericaBaseUrlEdit.text?.toString()?.trim().orEmpty(),
        )

    private fun repo(): EsfericaRepository =
        EsfericaRepository(EsfericaApiClient.createService(currentApiBase()))

    private fun primaryName(name: String?, id: String?): String =
        name?.trim()?.takeIf { it.isNotEmpty() } ?: id?.trim()?.takeIf { it.isNotEmpty() } ?: "—"

    private fun buildClientChoices(sorted: List<EsfericaClientDto>): List<LabeledClient> {
        val byName = sorted.groupBy { primaryName(it.name, it.id) }
        return sorted.map { dto ->
            val base = primaryName(dto.name, dto.id)
            val label =
                if (byName[base].orEmpty().size > 1) "$base (${dto.id})" else base
            LabeledClient(dto, label)
        }
    }

    private fun buildWarehouseChoices(sorted: List<EsfericaWarehouseDto>): List<LabeledWarehouse> {
        val byName = sorted.groupBy { primaryName(it.name, it.id) }
        return sorted.map { dto ->
            val base = primaryName(dto.name, dto.id)
            val label =
                if (byName[base].orEmpty().size > 1) "$base (${dto.id})" else base
            LabeledWarehouse(dto, label)
        }
    }

    private fun refreshClientAutocomplete() {
        val labels = clientChoices.map { it.label }
        val adapter = ArrayAdapter(this, R.layout.item_autocomplete_dropdown_line, labels)
        binding.clientAutoComplete.setAdapter(adapter)
    }

    private fun refreshWarehouseAutocomplete() {
        val labels = warehouseChoices.map { it.label }
        val adapter = ArrayAdapter(this, R.layout.item_autocomplete_dropdown_line, labels)
        binding.warehouseAutoComplete.setAdapter(adapter)
    }

    private fun resolveSelectedClient(): EsfericaClientDto? {
        val t = binding.clientAutoComplete.text?.toString()?.trim() ?: ""
        return clientChoices.find { it.label.equals(t, ignoreCase = true) }?.dto
    }

    private fun resolveSelectedWarehouse(): EsfericaWarehouseDto? {
        val t = binding.warehouseAutoComplete.text?.toString()?.trim() ?: ""
        return warehouseChoices.find { it.label.equals(t, ignoreCase = true) }?.dto
    }

    private fun loadCatalog() {
        catalogJob?.cancel()
        catalogJob = lifecycleScope.launch {
            bindingUi {
                retryLoadButton.visibility = View.GONE
                errorText.visibility = View.GONE
                startCountButton.isEnabled = false
            }
            startSelectionLoadingOverlay(R.string.esferica_loading_catalog)
            try {
                val repository = repo()
                val clientsResult = repository.getClients()
                val warehousesResult = repository.getWarehouses()

                clientsResult.onFailure { err ->
                    showCatalogError(err.message ?: err.javaClass.simpleName)
                }.onSuccess { list ->
                    clients = list.sortedWith(compareBy({ it.name ?: "" }, { it.id ?: "" }))
                    clientChoices = buildClientChoices(clients)
                    bindingUi { refreshClientAutocomplete() }
                }
                warehousesResult.onFailure { err ->
                    showCatalogError(err.message ?: err.javaClass.simpleName)
                }.onSuccess { list ->
                    warehouses = list.sortedWith(compareBy({ it.name ?: "" }, { it.id ?: "" }))
                    warehouseChoices = buildWarehouseChoices(warehouses)
                    bindingUi { refreshWarehouseAutocomplete() }
                }
                bindingUi {
                    if (clientsResult.isSuccess && warehousesResult.isSuccess) {
                        startCountButton.isEnabled = true
                    }
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                Log.e("EsfericaSelection", "Error al cargar catálogo", t)
                showCatalogError(
                    t.message ?: getString(R.string.esferica_catalog_load_error),
                )
            } finally {
                finishSelectionLoadingOverlay()
            }
        }
    }

    private fun showCatalogError(msg: String) {
        bindingUi {
            errorText.text = msg
            errorText.visibility = View.VISIBLE
            retryLoadButton.visibility = View.VISIBLE
        }
    }

    private fun startPhysicalCount() {
        val client = resolveSelectedClient()
        val warehouse = resolveSelectedWarehouse()
        if (client == null || warehouse == null) {
            Toast.makeText(this, R.string.esferica_must_select_client_warehouse, Toast.LENGTH_SHORT).show()
            return
        }
        val clientId = client.id?.trim()?.takeIf { it.isNotEmpty() }
        val warehouseId = warehouse.id?.trim()?.takeIf { it.isNotEmpty() }
        if (clientId == null || warehouseId == null) {
            Toast.makeText(this, R.string.esferica_must_select_client_warehouse, Toast.LENGTH_SHORT).show()
            return
        }
        saveBaseUrl()

        inventoryJob?.cancel()
        inventoryJob = lifecycleScope.launch {
            bindingUi {
                errorText.visibility = View.GONE
                startCountButton.isEnabled = false
            }
            startSelectionLoadingOverlay(R.string.esferica_loading_inventory)
            try {
                val inv = repo().getInventory(clientId, warehouseId, "DISPONIBLE")
                inv.onFailure { e ->
                    bindingUi {
                        errorText.text = e.message ?: getString(R.string.send_error)
                        errorText.visibility = View.VISIBLE
                    }
                    Toast.makeText(this@EsfericaSelectionActivity, e.message ?: "", Toast.LENGTH_LONG).show()
                }.onSuccess { items ->
                    if (items.isEmpty()) {
                        Toast.makeText(
                            this@EsfericaSelectionActivity,
                            R.string.esferica_inventory_empty,
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    val newSession = EsfericaCountPersistedSession(
                        sessionId = UUID.randomUUID().toString(),
                        clientId = clientId,
                        clientName = client.name ?: clientId,
                        warehouseId = warehouseId,
                        warehouseName = warehouse.name ?: warehouseId,
                        inventoryStatus = "DISPONIBLE",
                        expectedItems = items,
                        scannedRfidsOrdered = emptyList(),
                        lastReconcile = null,
                        updatedAt = System.currentTimeMillis(),
                    )
                    store.upsert(newSession)
                    if (isDestroyed) return@launch
                    startActivity(
                        Intent(this@EsfericaSelectionActivity, EsfericaCountActivity::class.java).apply {
                            putExtra(EsfericaCountActivity.EXTRA_SESSION_ID, newSession.sessionId)
                            putExtra("device_name", intent.getStringExtra("device_name"))
                            putExtra("device_address", intent.getStringExtra("device_address"))
                        },
                    )
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                Log.e("EsfericaSelection", "Error al obtener inventario", t)
                bindingUi {
                    errorText.text = t.message ?: getString(R.string.esferica_catalog_load_error)
                    errorText.visibility = View.VISIBLE
                }
                Toast.makeText(this@EsfericaSelectionActivity, t.message ?: "", Toast.LENGTH_LONG).show()
            } finally {
                finishSelectionLoadingOverlay()
                bindingUi {
                    startCountButton.isEnabled =
                        clients.isNotEmpty() && warehouses.isNotEmpty()
                }
            }
        }
    }
}
