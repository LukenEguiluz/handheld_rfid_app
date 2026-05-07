package com.dohealth.handheld.utils

object EsfericaRfidNormalizer {

    /** trim, uppercase, rechaza vacío o sólo caracteres no imprimibles. */
    fun normalize(raw: String?): String? {
        if (raw == null) return null
        val t = raw.trim().uppercase()
        if (t.isEmpty()) return null
        if (t.none { !it.isWhitespace() && !it.isISOControl() }) return null
        return t.replace("\\s+".toRegex(), "")
    }
}
