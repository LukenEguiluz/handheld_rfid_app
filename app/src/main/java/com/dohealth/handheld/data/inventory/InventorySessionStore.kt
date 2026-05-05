package com.dohealth.handheld.data.inventory

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class InventorySessionStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun listSessions(mode: String): List<InventorySession> {
        return loadAll().values
            .filter { it.mode == mode }
            .sortedByDescending { it.updatedAt }
    }

    fun getSession(id: String): InventorySession? = loadAll()[id]

    fun createSession(mode: String, name: String? = null): InventorySession {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val safeName = name ?: defaultName(mode, now)
        val session = InventorySession(
            id = id,
            name = safeName,
            createdAt = now,
            updatedAt = now,
            mode = mode
        )
        saveSession(session)
        setLastSessionId(mode, id)
        return session
    }

    fun saveSession(session: InventorySession) {
        val all = loadAll().toMutableMap()
        all[session.id] = session.copy(updatedAt = System.currentTimeMillis())
        prefs.edit()
            .putString(KEY_SESSIONS, gson.toJson(all))
            .apply()
        setLastSessionId(session.mode, session.id)
    }

    fun deleteSession(id: String) {
        val all = loadAll().toMutableMap()
        val removed = all.remove(id)
        prefs.edit()
            .putString(KEY_SESSIONS, gson.toJson(all))
            .apply()
        removed?.let {
            val key = lastSessionKeyFor(it.mode)
            if (prefs.getString(key, null) == id) {
                prefs.edit().remove(key).apply()
            }
        }
    }

    fun getLastSessionId(mode: String): String? = prefs.getString(lastSessionKeyFor(mode), null)

    private fun setLastSessionId(mode: String, id: String) {
        prefs.edit().putString(lastSessionKeyFor(mode), id).apply()
    }

    private fun lastSessionKeyFor(mode: String): String = "last_session_id_$mode"

    private fun loadAll(): Map<String, InventorySession> {
        val raw = prefs.getString(KEY_SESSIONS, null) ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, InventorySession>>() {}.type
            gson.fromJson<Map<String, InventorySession>>(raw, type) ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun defaultName(mode: String, now: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return "${mode} ${sdf.format(Date(now))}"
    }

    companion object {
        private const val PREFS_NAME = "inventory_sessions_prefs"
        private const val KEY_SESSIONS = "sessions_json"
    }
}

