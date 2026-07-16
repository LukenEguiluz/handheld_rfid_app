package com.dohealth.handheld.data.relacion

object RelacionSecondReadMode {
    /** Segundo paso por RFID (potencia mínima + separación entre lecturas en la actividad). */
    const val RFID = 0
    /** Segundo paso por código de barras (mismo flujo que el producto). */
    const val BARCODE = 1
}

data class RelacionItem(
    val id: String,
    val productCode: String,
    val rfidCode: String,
    val timestamp: Long,
    /** Si true, [rfidCode] guarda un código de barras del segundo paso. */
    val secondAsBarcode: Boolean = false
)

data class RelacionSession(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val currentProductCode: String? = null,
    val keepProduct: Boolean = false,
    /** [RelacionSecondReadMode.RFID] o [RelacionSecondReadMode.BARCODE]. */
    val secondReadMode: Int = RelacionSecondReadMode.RFID,
    val relations: List<RelacionItem> = emptyList()
)

