package com.dohealth.handheld.utils

import com.cf.zsdk.uitl.FormatUtil

/**
 * Los escáneres suelen enviar payload en bytes 7-bit imprimibles; interpretar como UTF-8
 * introduce caracteres erróneos. Se decodifica como ASCII imprimible (32–126).
 */
fun decodeBarcodeAscii(bytes: ByteArray): String {
    if (bytes.isEmpty()) return ""
    val sb = StringBuilder(bytes.size)
    for (b in bytes) {
        val c = b.toInt() and 0xFF
        if (c in 32..126) sb.append(c.toChar())
    }
    return sb.toString().trim()
}

fun decodeBarcodeWithHexFallback(bytes: ByteArray): String {
    val ascii = decodeBarcodeAscii(bytes)
    if (ascii.isNotEmpty()) return ascii
    return try {
        FormatUtil.bytesToHexStr(bytes).replace(" ", "")
    } catch (_: Exception) {
        ""
    }
}
