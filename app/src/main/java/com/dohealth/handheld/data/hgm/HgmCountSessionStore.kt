package com.dohealth.handheld.data.hgm

import android.content.Context
import com.dohealth.handheld.data.esferica.ExpectedInventoryItemDto
import com.dohealth.handheld.data.esferica.ReconcileResponseDto
import com.dohealth.handheld.utils.Constants
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

private const val KEY_SESSIONS_MAP = "hgm_sessions_map_v1"
private const val KEY_ACTIVE_SESSION_ID = "hgm_active_session_id_v1"
private const val KEY_SCANNED_PREFIX = "hgm_session_scanned_v1__"
private const val KEY_UPDATED_AT_PREFIX = "hgm_session_updatedAt_v1__"

data class HgmCountPersistedSession(
    val sessionId: String = UUID.randomUUID().toString(),
    val catalogFileName: String,
    val sessionLabel: String? = null,
    val expectedItems: List<ExpectedInventoryItemDto>,
    val scannedRfidsOrdered: List<String> = emptyList(),
    val lastReconcile: ReconcileResponseDto? = null,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    fun displayTitle(): String = sessionLabel?.takeIf { it.isNotBlank() } ?: catalogFileName
}

class HgmCountSessionStore(context: Context) {

    private val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val mapTypeToken = object : TypeToken<Map<String, HgmCountPersistedSession>>() {}.type
    private val stringListTypeToken = object : TypeToken<List<String>>() {}.type

    init {
        if (!prefs.contains(KEY_SESSIONS_MAP)) {
            prefs.edit().putString(KEY_SESSIONS_MAP, "{}").apply()
        }
    }

    private fun loadMap(): MutableMap<String, HgmCountPersistedSession> {
        val raw = prefs.getString(KEY_SESSIONS_MAP, null) ?: return mutableMapOf()
        return kotlin.runCatching {
            gson.fromJson<Map<String, HgmCountPersistedSession>>(raw, mapTypeToken)
                ?.toMutableMap() ?: mutableMapOf()
        }.getOrElse { mutableMapOf() }
    }

    private fun persistMap(map: Map<String, HgmCountPersistedSession>) {
        prefs.edit().putString(KEY_SESSIONS_MAP, gson.toJson(map)).apply()
    }

    private fun scannedKey(sessionId: String) = "$KEY_SCANNED_PREFIX$sessionId"

    private fun updatedAtKey(sessionId: String) = "$KEY_UPDATED_AT_PREFIX$sessionId"

    private fun loadScanned(sessionId: String): List<String>? {
        val raw = prefs.getString(scannedKey(sessionId), null) ?: return null
        return kotlin.runCatching {
            gson.fromJson<List<String>>(raw, stringListTypeToken)
        }.getOrNull()
    }

    private fun persistScanned(sessionId: String, scannedRfids: List<String>, nowMs: Long) {
        prefs.edit()
            .putString(scannedKey(sessionId), gson.toJson(scannedRfids))
            .putLong(updatedAtKey(sessionId), nowMs)
            .apply()
    }

    private fun loadUpdatedAt(sessionId: String): Long? =
        if (prefs.contains(updatedAtKey(sessionId))) prefs.getLong(updatedAtKey(sessionId), 0L)
        else null

    private fun resolveSession(base: HgmCountPersistedSession): HgmCountPersistedSession {
        val scanned = loadScanned(base.sessionId) ?: base.scannedRfidsOrdered
        val updated = loadUpdatedAt(base.sessionId) ?: base.updatedAt
        return base.copy(scannedRfidsOrdered = scanned, updatedAt = updated)
    }

    fun listSessions(): List<HgmCountPersistedSession> =
        loadMap().values.map { resolveSession(it) }.sortedByDescending { it.updatedAt }

    fun getSession(sessionId: String): HgmCountPersistedSession? =
        loadMap()[sessionId]?.let { resolveSession(it) }

    fun getActiveSessionId(): String? = prefs.getString(KEY_ACTIVE_SESSION_ID, null)

    fun setActiveSessionId(sessionId: String?) {
        if (sessionId.isNullOrBlank()) {
            prefs.edit().remove(KEY_ACTIVE_SESSION_ID).apply()
        } else {
            prefs.edit().putString(KEY_ACTIVE_SESSION_ID, sessionId).apply()
        }
    }

    fun upsert(session: HgmCountPersistedSession) {
        val now = System.currentTimeMillis()
        val map = loadMap().toMutableMap()
        map[session.sessionId] = session.copy(updatedAt = now)
        persistMap(map)
        persistScanned(session.sessionId, session.scannedRfidsOrdered, now)
        setActiveSessionId(session.sessionId)
    }

    fun delete(sessionId: String) {
        val map = loadMap().toMutableMap()
        map.remove(sessionId)
        persistMap(map)
        prefs.edit()
            .remove(scannedKey(sessionId))
            .remove(updatedAtKey(sessionId))
            .apply()
        if (getActiveSessionId() == sessionId) {
            prefs.edit().remove(KEY_ACTIVE_SESSION_ID).apply()
        }
    }

    fun updateScanned(sessionId: String, scannedRfids: List<String>) {
        val now = System.currentTimeMillis()
        persistScanned(sessionId, scannedRfids, now)
        if (getActiveSessionId() == sessionId) {
            setActiveSessionId(sessionId)
        }
    }
}
