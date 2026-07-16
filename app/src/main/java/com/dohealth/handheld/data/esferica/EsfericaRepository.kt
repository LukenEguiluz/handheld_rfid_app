package com.dohealth.handheld.data.esferica

import com.dohealth.handheld.domain.EsfericaReconcileLocal
import com.dohealth.handheld.domain.EsfericaReconcileMerger
import com.dohealth.handheld.network.EsfericaApiClient
import com.dohealth.handheld.network.EsfericaApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EsfericaRepository(private val api: EsfericaApiService) {

    suspend fun getClients(): Result<List<EsfericaClientDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val res = api.getClients()
            if (!res.isSuccessful) error("Clientes: HTTP ${res.code()}")
            (res.body() ?: emptyList()).filterNot { it.id.isNullOrBlank() }
        }
    }

    suspend fun getWarehouses(): Result<List<EsfericaWarehouseDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val res = api.getWarehouses()
            if (!res.isSuccessful) error("Almacenes: HTTP ${res.code()}")
            (res.body() ?: emptyList()).filterNot { it.id.isNullOrBlank() }
        }
    }

    suspend fun getInventory(
        clientId: String,
        warehouseId: String,
        status: String = "DISPONIBLE",
    ): Result<List<ExpectedInventoryItemDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val res = api.getInventory(clientId, warehouseId, status)
            val raw = res.body()?.string() ?: ""
            if (!res.isSuccessful) error("Inventario: HTTP ${res.code()}")
            EsfericaApiClient.parseInventoryJson(raw)
        }
    }

    suspend fun rfidLookup(rfids: List<String>): List<InventoryItemLiteDto> = withContext(Dispatchers.IO) {
        if (rfids.isEmpty()) return@withContext emptyList()
        runCatching {
            val res = api.rfidLookup(RfidLookupBody(rfids))
            val raw = res.body()?.string() ?: return@withContext emptyList()
            if (!res.isSuccessful) return@withContext emptyList()
            EsfericaApiClient.parseLookupJson(raw)
        }.getOrElse { emptyList() }
    }

    /**
     * Intenta POST /inventory/reconcile; si falla, concilia en local.
     * Si hay sobrantes y el backend no devolvió [ReconcileResponseDto.extraIdentified], llama a /rfid/lookup.
     */
    suspend fun reconcileWithFallback(
        clientId: String,
        warehouseId: String,
        status: String,
        rfids: List<String>,
        expectedItems: List<ExpectedInventoryItemDto>,
        enrichExtrasWithLookup: Boolean = true,
    ): ReconcileResponseDto = withContext(Dispatchers.IO) {
        val req = ReconcileRequestDto(clientId, warehouseId, status, rfids)
        val remote = runCatching { api.reconcile(req) }.getOrNull()

        var result: ReconcileResponseDto? =
            remote?.takeIf { it.isSuccessful }?.body()
        if (result == null) {
            result = EsfericaReconcileLocal.reconcile(expectedItems, rfids)
        }

        val extras = result.extra.orEmpty()
        val needLookup = enrichExtrasWithLookup &&
            extras.isNotEmpty() &&
            result.extraIdentified.isNullOrEmpty()

        if (needLookup) {
            val looked = rfidLookup(extras)
            result = EsfericaReconcileMerger.enrichWithLookup(result, looked)
        }

        result
    }
}
