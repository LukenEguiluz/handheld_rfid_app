package com.dohealth.handheld.utils

import java.text.Normalizer
import java.util.Locale

/** Comparación sin acentos; el texto mostrado conserva tildes y eñes. */
object HgmTextNormalize {

    fun forCompare(text: String?): String {
        if (text.isNullOrBlank()) return ""
        val n = Normalizer.normalize(text.trim(), Normalizer.Form.NFD)
        return n.replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.getDefault())
    }

    fun containsInsensitive(haystack: String?, needle: String): Boolean {
        if (needle.isBlank()) return true
        return forCompare(haystack).contains(forCompare(needle))
    }
}
