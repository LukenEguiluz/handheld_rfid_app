package com.dohealth.handheld.domain

import com.dohealth.handheld.data.esferica.ExpectedInventoryItemDto
import com.dohealth.handheld.data.hgm.HgmCatalogRow
import com.dohealth.handheld.utils.HgmSkuRfidConverter

object HgmCatalogMapper {

    fun toExpectedItems(rows: List<HgmCatalogRow>): Pair<List<ExpectedInventoryItemDto>, List<String>> {
        val items = mutableListOf<ExpectedInventoryItemDto>()
        val warnings = mutableListOf<String>()
        rows.forEachIndexed { index, row ->
            val rfid = HgmSkuRfidConverter.skuToNormalizedRfid(row.sku)
            if (rfid == null) {
                warnings.add("Fila ${index + 2}: SKU inválido «${row.sku.take(40)}»")
                return@forEachIndexed
            }
            val hemo = row.hemocomponente.trim()
            val grupo = row.grupoAboRh.trim()
            val unidad = row.unidad.trim()
            items.add(
                ExpectedInventoryItemDto(
                    rfid = rfid,
                    code = hemo.ifBlank { null },
                    description = grupo.ifBlank { null },
                    batch = unidad.ifBlank { null },
                    expiration = null,
                    status = "HGM",
                ),
            )
        }
        return items to warnings
    }
}
