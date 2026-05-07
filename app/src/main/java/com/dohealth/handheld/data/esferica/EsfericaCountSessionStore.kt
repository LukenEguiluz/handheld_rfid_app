package com.dohealth.handheld.data.esferica

import android.content.Context
import com.dohealth.handheld.utils.Constants
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

private const val KEY_SESSIONS_MAP = "esferica_sessions_map_v4"
private const val KEY_ACTIVE_SESSION_ID = "esferica_active_session_id_v4"
private const val LEGACY_SINGLE_SESSION = "esferica_physical_count_session_v2"
private const val KEY_SCANNED_PREFIX = "esferica_session_scanned_v1__"
private const val KEY_UPDATED_AT_PREFIX = "esferica_session_updatedAt_v1__"

data class EsfericaCountPersistedSession(
    val sessionId: String = UUID.randomUUID().toString(),
    val clientId: String,
    val clientName: String,
    val warehouseId: String,
    val warehouseName: String,
    val inventoryStatus: String,
    val expectedItems: List<ExpectedInventoryItemDto>,
    val scannedRfidsOrdered: List<String>,
    val lastReconcile: ReconcileResponseDto? = null,
    /** Para ordenar en historial; se actualiza al guardar lecturas/reconcilia. */
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * Persiste varios conteos ESFERICA y permite reanudar por [sessionId].
 * Migra el valor único anterior (legacy v2).
 */
class EsfericaCountSessionStore(context: Context) {

    private val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val mapTypeToken = object : TypeToken<Map<String, EsfericaCountPersistedSession>>() {}.type
    private val stringListTypeToken = object : TypeToken<List<String>>() {}.type

    init {
        migrateLegacyIfNeeded()
    }

    /**
     * - Inicializa el mapa vacío si es la primera vez.
     * - Migra sesión única v2 cuando aún no hay mapa.
     * - Si ya hay mapa y sobra la clave v2 (instalaciones mezcladas), la borra.
     */
    private fun migrateLegacyIfNeeded() {
        val hasMap = prefs.contains(KEY_SESSIONS_MAP)
        val legacyRaw = prefs.getString(LEGACY_SINGLE_SESSION, null)
        val editorChain = prefs.edit()
        var apply = false
        when {
            legacyRaw != null && !hasMap -> {
                val session = kotlin.runCatching {
                    gson.fromJson(legacyRaw, EsfericaCountPersistedSession::class.java)
                }.getOrNull()
                if (session != null) {
                    val fixed = session.copy(
                        updatedAt = session.updatedAt.takeIf { it > 0 }
                            ?: System.currentTimeMillis(),
                    )
                    persistMap(mapOf(fixed.sessionId to fixed))
                    editorChain.putString(KEY_ACTIVE_SESSION_ID, fixed.sessionId).remove(
                        LEGACY_SINGLE_SESSION,
                    )
                    apply = true
                } else {
                    editorChain.remove(LEGACY_SINGLE_SESSION).putString(KEY_SESSIONS_MAP, "{}")
                    apply = true
                }
            }
            legacyRaw != null && hasMap -> {
                editorChain.remove(LEGACY_SINGLE_SESSION)
                apply = true
            }
            !hasMap -> {
                editorChain.putString(KEY_SESSIONS_MAP, "{}")
                apply = true
            }
        }
        if (apply) editorChain.apply()
    }

    private fun loadMap(): MutableMap<String, EsfericaCountPersistedSession> {
        val raw = prefs.getString(KEY_SESSIONS_MAP, null) ?: return mutableMapOf()
        return kotlin.runCatching {
            gson.fromJson<Map<String, EsfericaCountPersistedSession>>(raw, mapTypeToken)
                ?.toMutableMap() ?: mutableMapOf()
        }.getOrElse { mutableMapOf() }
    }

    private fun persistMap(map: Map<String, EsfericaCountPersistedSession>) {
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

    private fun resolveSession(base: EsfericaCountPersistedSession): EsfericaCountPersistedSession {
        val scanned = loadScanned(base.sessionId) ?: base.scannedRfidsOrdered
        val updated = loadUpdatedAt(base.sessionId) ?: base.updatedAt
        return base.copy(
            scannedRfidsOrdered = scanned,
            updatedAt = updated,
        )
    }

    fun listSessions(): List<EsfericaCountPersistedSession> =
        loadMap().values.map { resolveSession(it) }.sortedByDescending { it.updatedAt }

    fun getSession(sessionId: String): EsfericaCountPersistedSession? =
        loadMap()[sessionId]?.let { resolveSession(it) }

    fun getActiveSessionId(): String? = prefs.getString(KEY_ACTIVE_SESSION_ID, null)

    fun setActiveSessionId(sessionId: String?) {
        if (sessionId.isNullOrBlank()) {
            prefs.edit().remove(KEY_ACTIVE_SESSION_ID).apply()
        } else {
            prefs.edit().putString(KEY_ACTIVE_SESSION_ID, sessionId).apply()
        }
    }

    /**
     * Crea o reemplaza la sesión por [EsfericaCountPersistedSession.sessionId] y la marca como activa.
     */
    fun upsert(session: EsfericaCountPersistedSession) {
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

    /** Compatibilidad: sesión activa o la primera del mapa. */
    fun load(): EsfericaCountPersistedSession? {
        val sid = getActiveSessionId()
        val fromActive = sid?.let { getSession(it) }
        if (fromActive != null) return fromActive
        return loadMap().values.maxByOrNull { it.updatedAt }
    }

    /** @deprecated Usar [upsert] */
    fun save(session: EsfericaCountPersistedSession) {
        upsert(session)
    }

    fun updateScanned(sessionId: String, scannedRfids: List<String>) {
        val now = System.currentTimeMillis()
        persistScanned(sessionId, scannedRfids, now)
        if (getActiveSessionId() == sessionId) {
            setActiveSessionId(sessionId)
        }
    }

    fun updateLastReconcile(sessionId: String, result: ReconcileResponseDto) {
        val cur = getSession(sessionId) ?: return
        upsert(cur.copy(lastReconcile = result))
    }
}
