package com.dohealth.handheld.data.relacion

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class RelacionSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun listSessions(): List<RelacionSession> {
        return loadAll().values
            .sortedByDescending { it.updatedAt }
    }

    fun getSession(id: String): RelacionSession? = loadAll()[id]

    fun createSession(name: String? = null): RelacionSession {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val safeName = name ?: defaultName(now)
        val session = RelacionSession(
            id = id,
            name = safeName,
            createdAt = now,
            updatedAt = now
        )
        saveSession(session)
        setLastSessionId(id)
        return session
    }

    fun saveSession(session: RelacionSession) {
        val all = loadAll().toMutableMap()
        all[session.id] = session.copy(updatedAt = System.currentTimeMillis())
        prefs.edit()
            .putString(KEY_SESSIONS, gson.toJson(all))
            .apply()
        setLastSessionId(session.id)
    }

    fun deleteSession(id: String) {
        val all = loadAll().toMutableMap()
        all.remove(id)
        prefs.edit()
            .putString(KEY_SESSIONS, gson.toJson(all))
            .apply()
        if (getLastSessionId() == id) {
            prefs.edit().remove(KEY_LAST_SESSION_ID).apply()
        }
    }

    fun getLastSessionId(): String? = prefs.getString(KEY_LAST_SESSION_ID, null)

    fun setLastSessionId(id: String) {
        prefs.edit().putString(KEY_LAST_SESSION_ID, id).apply()
    }

    private fun loadAll(): Map<String, RelacionSession> {
        val raw = prefs.getString(KEY_SESSIONS, null) ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, RelacionSession>>() {}.type
            gson.fromJson<Map<String, RelacionSession>>(raw, type) ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun defaultName(now: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return "Lectura ${sdf.format(Date(now))}"
    }

    companion object {
        private const val PREFS_NAME = "relacion_sessions_prefs"
        private const val KEY_SESSIONS = "sessions_json"
        private const val KEY_LAST_SESSION_ID = "last_session_id"
    }
}

