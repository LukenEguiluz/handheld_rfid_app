package com.dohealth.handheld.data.hgm

/**
 * Fila del catálogo HGM (columnas del CSV/Excel).
 */
data class HgmCatalogRow(
    val unidad: String,
    val sku: String,
    val hemocomponente: String,
    val grupoAboRh: String,
)

data class HgmCatalogImportResult(
    val rows: List<HgmCatalogRow>,
    val skippedRows: Int,
    val warnings: List<String>,
)
