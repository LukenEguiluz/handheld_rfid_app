package com.dohealth.handheld.utils

import com.dohealth.handheld.data.hgm.HgmCountPersistedSession
import com.dohealth.handheld.domain.EsfericaReconcileLocal

object HgmExport {

    fun buildExpectedVsValidCountedCsv(session: HgmCountPersistedSession): String {
        fun q(cell: String) = "\"" + cell.replace("\"", "\"\"") + "\""
        val scannedUnique =
            EsfericaReconcileLocal.distinctNormalizedPreserveOrder(session.scannedRfidsOrdered).toSet()
        val sb = StringBuilder()
        sb.appendLine(
            listOf(
                "Archivo",
                "Unidad",
                "SKU",
                "Hemocomponente",
                "Grupo ABO y Rh",
                "RFID Esperado",
                "RFID contado válido",
            ).joinToString(",") { q(it) },
        )
        for (item in session.expectedItems) {
            val expectedRfid = EsfericaRfidNormalizer.normalize(item.rfid) ?: continue
            val countedValid = if (expectedRfid in scannedUnique) expectedRfid else ""
            val sku = HgmSkuRfidConverter.normalizedRfidToSku(expectedRfid) ?: ""
            sb.appendLine(
                listOf(
                    session.catalogFileName,
                    item.batch ?: "",
                    sku,
                    item.code ?: "",
                    item.description ?: "",
                    expectedRfid,
                    countedValid,
                ).joinToString(",") { q(it) },
            )
        }

        val offCatalog = EsfericaReconcileLocal.offCatalogRfidsOrdered(
            session.expectedItems,
            session.scannedRfidsOrdered,
        )
        if (offCatalog.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine(q("--- Lecturas únicas fuera de catálogo ---"))
            sb.appendLine(
                listOf(
                    "Archivo",
                    "SKU",
                    "RFID leído",
                ).joinToString(",") { q(it) },
            )
            for (rfid in offCatalog) {
                val sku = HgmSkuRfidConverter.normalizedRfidToSku(rfid) ?: ""
                sb.appendLine(
                    listOf(
                        session.catalogFileName,
                        sku,
                        rfid,
                    ).joinToString(",") { q(it) },
                )
            }
        }

        return sb.toString()
    }
}
