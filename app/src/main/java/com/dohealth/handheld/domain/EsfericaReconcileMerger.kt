package com.dohealth.handheld.domain

import com.dohealth.handheld.data.esferica.InventoryItemLiteDto
import com.dohealth.handheld.data.esferica.ProductCountResultDto
import com.dohealth.handheld.data.esferica.ReconcileResponseDto
import com.dohealth.handheld.utils.EsfericaRfidNormalizer

object EsfericaReconcileMerger {

    private fun nullableEq(a: String?, b: String?) = (a ?: "") == (b ?: "")

    /** Ajusta extras con POST /rfid/lookup y desglosa sobrantes identificados por producto cuando hay código/descripción. */
    fun enrichWithLookup(
        response: ReconcileResponseDto,
        lookupItems: List<InventoryItemLiteDto>,
    ): ReconcileResponseDto {
        val byRfid = LinkedHashMap<String, InventoryItemLiteDto>()
        for (it in lookupItems) {
            val k = EsfericaRfidNormalizer.normalize(it.rfid) ?: continue
            byRfid[k] = it.copy(rfid = k)
        }

        val extras = response.extra.orEmpty()
        if (extras.isEmpty()) return response

        val identified = LinkedHashMap<String, InventoryItemLiteDto>()
        val unknown = mutableListOf<String>()
        for (e in extras) {
            val lit = byRfid[e]
            if (lit != null) identified[e] = lit
            else unknown += e
        }

        val productsIn = response.products ?: emptyList()
        val mutable = productsIn.map { it.copy() }.toMutableList()

        for (lite in identified.values.distinctBy { it.rfid ?: "" }) {
            val rfidNorm = lite.rfid ?: continue
            val idx = mutable.indexOfFirst {
                nullableEq(it.code, lite.code) && nullableEq(it.description, lite.description)
            }
            if (idx >= 0) {
                val p = mutable[idx]
                mutable[idx] = p.copy(
                    extraQty = p.extraQty + 1,
                    extraRfids = (p.extraRfids + rfidNorm).distinct(),
                )
            } else if (!lite.code.isNullOrBlank() || !lite.description.isNullOrBlank()) {
                mutable += ProductCountResultDto(
                    code = lite.code,
                    description = lite.description,
                    expectedQty = 0,
                    scannedQty = 0,
                    matchedQty = 0,
                    missingQty = 0,
                    extraQty = 1,
                    expectedRfids = emptyList(),
                    scannedRfids = emptyList(),
                    missingRfids = emptyList(),
                    extraRfids = listOf(rfidNorm),
                )
            }
        }

        return response.copy(
            products = mutable.sortedWith(compareBy({ it.code ?: "" }, { it.description ?: "" })),
            extraIdentified = identified.values.toList().sortedBy { it.rfid },
            extraUnknown = unknown.distinct(),
        )
    }
}
