package com.dohealth.handheld.data.esferica

import android.content.Context
import com.dohealth.handheld.utils.Constants
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

private const val KEY_SESSIONS_MAP = "esferica_sessions_map_v4"
private const val KEY_ACTIVE_SESSION_ID = "esferica_active_session_id_v4"
private const val LEGACY_SINGLE_SESSION = "esferica_physical_count_session_v2"

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

    fun listSessions(): List<EsfericaCountPersistedSession> =
        loadMap().values.sortedByDescending { it.updatedAt }

    fun getSession(sessionId: String): EsfericaCountPersistedSession? = loadMap()[sessionId]

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
        val map = loadMap().toMutableMap()
        map[session.sessionId] = session.copy(updatedAt = System.currentTimeMillis())
        persistMap(map)
        setActiveSessionId(session.sessionId)
    }

    fun delete(sessionId: String) {
        val map = loadMap().toMutableMap()
        map.remove(sessionId)
        persistMap(map)
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
        val cur = getSession(sessionId) ?: return
        upsert(cur.copy(scannedRfidsOrdered = scannedRfids))
    }

    fun updateLastReconcile(sessionId: String, result: ReconcileResponseDto) {
        val cur = getSession(sessionId) ?: return
        upsert(cur.copy(lastReconcile = result))
    }
}
