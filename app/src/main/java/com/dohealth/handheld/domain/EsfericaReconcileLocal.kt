package com.dohealth.handheld.domain

import com.dohealth.handheld.data.esferica.CountSummaryDto
import com.dohealth.handheld.data.esferica.ExpectedInventoryItemDto
import com.dohealth.handheld.data.esferica.InventoryItemLiteDto
import com.dohealth.handheld.data.esferica.ProductCountResultDto
import com.dohealth.handheld.data.esferica.ReconcileResponseDto
import com.dohealth.handheld.utils.EsfericaRfidNormalizer
import java.util.LinkedHashSet

object EsfericaReconcileLocal {

    private data class Row(val rfid: String, val item: ExpectedInventoryItemDto)

    private fun prodKey(code: String?, description: String?) =
        "${code ?: ""}\u0000${description ?: ""}"

    fun normalizedExpectedRfidSet(expectedItems: List<ExpectedInventoryItemDto>): Set<String> =
        expectedItems.mapNotNull { EsfericaRfidNormalizer.normalize(it.rfid) }.toSet()

    /** RFIDs normalizados únicos, en el orden de primera aparición en [scannedRfidsOrdered]. */
    fun distinctNormalizedPreserveOrder(scannedRfidsOrdered: List<String>): List<String> {
        val seen = LinkedHashSet<String>()
        for (raw in scannedRfidsOrdered) {
            val n = EsfericaRfidNormalizer.normalize(raw) ?: continue
            seen.add(n)
        }
        return seen.toList()
    }

    fun countMatchedUnique(
        expectedItems: List<ExpectedInventoryItemDto>,
        scannedRfidsOrdered: List<String>,
    ): Int {
        val expected = normalizedExpectedRfidSet(expectedItems)
        return distinctNormalizedPreserveOrder(scannedRfidsOrdered).count { it in expected }
    }

    fun countOffCatalogUnique(
        expectedItems: List<ExpectedInventoryItemDto>,
        scannedRfidsOrdered: List<String>,
    ): Int {
        val expected = normalizedExpectedRfidSet(expectedItems)
        return distinctNormalizedPreserveOrder(scannedRfidsOrdered).count { it !in expected }
    }

    fun reconcile(
        expectedItems: List<ExpectedInventoryItemDto>,
        scannedRfidsOrdered: List<String>,
    ): ReconcileResponseDto {
        val rows = expectedItems.mapNotNull {
            val r = EsfericaRfidNormalizer.normalize(it.rfid) ?: return@mapNotNull null
            Row(r, it.copy(rfid = r))
        }
        val scannedUnique = scannedRfidsOrdered.mapNotNull { EsfericaRfidNormalizer.normalize(it) }
            .distinct()

        val expectedRfidSet = rows.map { it.rfid }.toSet()
        val scannedSet = scannedUnique.toSet()

        val matchedRows = rows.filter { it.rfid in scannedSet }
        val missingRows = rows.filter { it.rfid !in scannedSet }

        val extraRfidsOrdered = scannedUnique.filter { it !in expectedRfidSet }

        val summary = CountSummaryDto(
            expectedTotal = rows.size,
            scannedTotal = scannedUnique.size,
            matchedTotal = matchedRows.size,
            missingTotal = missingRows.size,
            extraTotal = extraRfidsOrdered.size,
        )

        val matchedItems = matchedRows.map { row ->
            InventoryItemLiteDto(
                rfid = row.rfid,
                status = row.item.status,
                batch = row.item.batch,
                expiration = row.item.expiration,
                code = row.item.code,
                description = row.item.description,
                businessName = null,
            )
        }.distinctBy { it.rfidKey() }

        val missingItems = missingRows.map { row ->
            InventoryItemLiteDto(
                rfid = row.rfid,
                status = row.item.status,
                batch = row.item.batch,
                expiration = row.item.expiration,
                code = row.item.code,
                description = row.item.description,
                businessName = null,
            )
        }.distinctBy { it.rfidKey() }

        val products = rows.groupBy { prodKey(it.item.code, it.item.description) }.values.map { prodRows ->
            val fi = prodRows.first().item
            val matchedForProdRows = prodRows.filter { it.rfid in scannedSet }
            val missingForProdRows = prodRows.filter { it.rfid !in scannedSet }

            ProductCountResultDto(
                code = fi.code,
                description = fi.description,
                expectedQty = prodRows.size,
                scannedQty = matchedForProdRows.size,
                matchedQty = matchedForProdRows.size,
                missingQty = missingForProdRows.size,
                extraQty = 0,
                expectedRfids = prodRows.map { it.rfid }.distinct(),
                scannedRfids = matchedForProdRows.map { it.rfid }.distinct(),
                missingRfids = missingForProdRows.map { it.rfid }.distinct(),
                extraRfids = emptyList(),
            )
        }.sortedWith(compareBy({ it.code ?: "" }, { it.description ?: "" }))

        return ReconcileResponseDto(
            summary = summary,
            products = products,
            matched = matchedItems.sortedBy { it.rfid },
            missing = missingItems.sortedBy { it.rfid },
            extra = extraRfidsOrdered,
            extraIdentified = emptyList(),
            extraUnknown = extraRfidsOrdered,
        )
    }

    private fun InventoryItemLiteDto.rfidKey() = rfid ?: ""
}
