package com.dohealth.handheld.data.esferica

import com.google.gson.annotations.SerializedName

data class EsfericaClientDto(
    @SerializedName(value = "id", alternate = ["client_id", "uuid", "_id"])
    val id: String?,
    @SerializedName(value = "name", alternate = ["client_name", "business_name"])
    val name: String?,
)

data class EsfericaWarehouseDto(
    @SerializedName(value = "id", alternate = ["warehouse_id", "uuid", "_id"])
    val id: String?,
    @SerializedName(value = "name", alternate = ["warehouse_name"])
    val name: String?,
)

/** Ítem esperado según GET /inventory */
data class ExpectedInventoryItemDto(
    @SerializedName(value = "rfid", alternate = ["epc", "barcode"]) val rfid: String?,
    @SerializedName(value = "code", alternate = ["sku", "product_code"]) val code: String?,
    val description: String?,
    @SerializedName(value = "batch", alternate = ["lot"]) val batch: String?,
    val expiration: String?,
    val status: String?,
)

/** Si la API envuelve en "items"; si llega lista plana, usar [EsfericaApiClient.parseInventoryJson]. */
data class ExpectedInventoryEnvelope(
    val items: List<ExpectedInventoryItemDto>?,
)

data class ReconcileRequestDto(
    @SerializedName("client_id") val clientId: String,
    @SerializedName("warehouse_id") val warehouseId: String,
    val status: String = "DISPONIBLE",
    val rfids: List<String>,
)

data class CountSummaryDto(
    @SerializedName("expected_total") val expectedTotal: Int,
    @SerializedName("scanned_total") val scannedTotal: Int,
    @SerializedName("matched_total") val matchedTotal: Int,
    @SerializedName("missing_total") val missingTotal: Int,
    @SerializedName("extra_total") val extraTotal: Int,
)

data class InventoryItemLiteDto(
    @SerializedName(value = "rfid", alternate = ["epc", "barcode"]) val rfid: String?,
    val status: String?,
    val batch: String?,
    val expiration: String?,
    val code: String?,
    val description: String?,
    @SerializedName("business_name") val businessName: String?,
)

data class ProductCountResultDto(
    val code: String?,
    val description: String?,
    @SerializedName("expected_qty") val expectedQty: Int,
    @SerializedName("scanned_qty") val scannedQty: Int,
    @SerializedName("matched_qty") val matchedQty: Int,
    @SerializedName("missing_qty") val missingQty: Int,
    @SerializedName("extra_qty") val extraQty: Int,
    @SerializedName("expected_rfids") val expectedRfids: List<String> = emptyList(),
    @SerializedName("scanned_rfids") val scannedRfids: List<String> = emptyList(),
    @SerializedName("missing_rfids") val missingRfids: List<String> = emptyList(),
    @SerializedName("extra_rfids") val extraRfids: List<String> = emptyList(),
)

data class ReconcileResponseDto(
    val summary: CountSummaryDto,
    val products: List<ProductCountResultDto>?,
    val matched: List<InventoryItemLiteDto>?,
    val missing: List<InventoryItemLiteDto>?,
    val extra: List<String>?,
    @SerializedName("extra_identified") val extraIdentified: List<InventoryItemLiteDto>?,
    @SerializedName("extra_unknown") val extraUnknown: List<String>?,
)

/** POST /rfid/lookup — cuerpo flexible */
data class RfidLookupBody(
    val rfids: List<String>,
)

data class RfidLookupEnvelope(
    val items: List<InventoryItemLiteDto>?,
)
