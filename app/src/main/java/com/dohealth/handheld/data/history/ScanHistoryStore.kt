package com.dohealth.handheld.data.history

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

class ScanHistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun add(mode: String, code: String) {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return

        val list = list(mode).toMutableList()
        list.add(
            ScanHistoryEntry(
                id = UUID.randomUUID().toString(),
                code = trimmed,
                timestamp = System.currentTimeMillis(),
                mode = mode
            )
        )
        // limitar tamaño para no crecer infinito
        val capped = if (list.size > MAX_ENTRIES) list.takeLast(MAX_ENTRIES) else list
        prefs.edit().putString(keyFor(mode), gson.toJson(capped)).apply()
    }

    fun list(mode: String): List<ScanHistoryEntry> {
        val raw = prefs.getString(keyFor(mode), null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<ScanHistoryEntry>>() {}.type
            gson.fromJson<List<ScanHistoryEntry>>(raw, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clear(mode: String) {
        prefs.edit().remove(keyFor(mode)).apply()
    }

    private fun keyFor(mode: String): String = "history_$mode"

    companion object {
        private const val PREFS_NAME = "scan_history_prefs"
        private const val MAX_ENTRIES = 2000
    }
}

