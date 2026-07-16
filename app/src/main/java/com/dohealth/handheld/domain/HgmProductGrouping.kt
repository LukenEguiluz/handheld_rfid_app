package com.dohealth.handheld.domain

import com.dohealth.handheld.data.esferica.ProductCountResultDto

data class HgmHemoGroup(
    val hemocomponente: String,
    val expectedQty: Int,
    val scannedQty: Int,
    val matchedQty: Int,
    val missingQty: Int,
    val grupos: List<ProductCountResultDto>,
)

object HgmProductGrouping {

    fun buildGroups(products: List<ProductCountResultDto>): List<HgmHemoGroup> =
        products
            .groupBy { it.code?.trim().orEmpty().ifBlank { "—" } }
            .map { (hemo, grupoRows) ->
                HgmHemoGroup(
                    hemocomponente = hemo,
                    expectedQty = grupoRows.sumOf { it.expectedQty },
                    scannedQty = grupoRows.sumOf { it.scannedQty },
                    matchedQty = grupoRows.sumOf { it.matchedQty },
                    missingQty = grupoRows.sumOf { it.missingQty },
                    grupos = grupoRows.sortedWith(
                        compareBy({ it.description ?: "" }, { it.expectedQty }),
                    ),
                )
            }
            .sortedBy { it.hemocomponente.lowercase() }
}
