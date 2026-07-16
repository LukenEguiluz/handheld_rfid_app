package com.dohealth.handheld.utils

import java.nio.charset.StandardCharsets

/**
 * En el catálogo HGM el **SKU** es la representación ASCII del EPC RFID.
 * El lector entrega el EPC en hexadecimal; el match se hace en ese mismo espacio normalizado.
 */
object HgmSkuRfidConverter {

    /** SKU (texto ASCII del archivo) → EPC hex normalizado para [EsfericaRfidNormalizer]. */
    fun skuToNormalizedRfid(sku: String): String? {
        val trimmed = sku.trim()
        if (trimmed.isEmpty()) return null
        val hex = if (looksLikeHexEpc(trimmed)) {
            trimmed
        } else {
            trimmed.toByteArray(StandardCharsets.US_ASCII).joinToString("") { b ->
                "%02X".format(b)
            }
        }
        return EsfericaRfidNormalizer.normalize(hex)
    }

    /** EPC hex leído → SKU ASCII (para mostrar / depurar). */
    fun normalizedRfidToSku(normalizedHex: String): String? {
        val clean = normalizedHex.trim()
        if (clean.length < 2 || clean.length % 2 != 0) return null
        return runCatching {
            val bytes = ByteArray(clean.length / 2) { i ->
                clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            String(bytes, StandardCharsets.US_ASCII).trim()
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    private fun looksLikeHexEpc(value: String): Boolean {
        val compact = value.replace("\\s".toRegex(), "")
        if (compact.length < 4 || compact.length % 2 != 0) return false
        return compact.all { it in '0'..'9' || it in 'A'..'F' || it in 'a'..'f' }
    }
}
