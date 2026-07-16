package com.dohealth.handheld.utils

import android.content.Context
import com.dohealth.handheld.data.model.ContinuousTag
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

object InventoryWebhookService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val isoFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'")
        .withZone(ZoneOffset.UTC)

    data class WebhookResult(
        val success: Boolean,
        val httpCode: Int? = null,
        val message: String? = null,
        val payload: String? = null,
    )

    data class VerificationResult(
        val success: Boolean,
        val matches: Boolean = false,
        val eventId: String? = null,
        val postHttpCode: Int? = null,
        val getHttpCode: Int? = null,
        val sentJson: String? = null,
        val receivedJson: String? = null,
        val differences: List<String> = emptyList(),
        val message: String = "",
    )

    data class WebhookConfig(
        val endpoint: String,
        val apiKey: String,
        val systemId: String,
        val systemName: String,
        val readerId: String,
        val eventId: String = "",
    )

    fun readConfig(context: Context): WebhookConfig {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        return WebhookConfig(
            endpoint = prefs.getString(Constants.KEY_WEBHOOK_URL, "") ?: "",
            apiKey = prefs.getString(Constants.KEY_WEBHOOK_API_KEY, "") ?: "",
            systemId = prefs.getString(Constants.KEY_WEBHOOK_SYSTEM_ID, "") ?: "",
            systemName = prefs.getString(Constants.KEY_WEBHOOK_SYSTEM_NAME, "") ?: "",
            readerId = prefs.getString(Constants.KEY_WEBHOOK_READER_ID, "") ?: "",
            eventId = prefs.getString(Constants.KEY_WEBHOOK_EVENT_ID, "") ?: "",
        )
    }

    /** Rellena campos vacíos con defaults (endpoint Epione + IDs del dispositivo). */
    fun resolveDefaults(context: Context, config: WebhookConfig): WebhookConfig {
        return config.copy(
            endpoint = config.endpoint.ifBlank { Constants.DEFAULT_WEBHOOK_URL },
            systemId = config.systemId.ifBlank { WebhookDeviceIdentity.defaultSystemId(context) },
            systemName = config.systemName.ifBlank { WebhookDeviceIdentity.defaultSystemName(context) },
            readerId = config.readerId.ifBlank { WebhookDeviceIdentity.defaultReaderId(context) },
            eventId = config.eventId.ifBlank { WebhookDeviceIdentity.defaultEventId(context) },
        )
    }

    fun saveConfig(context: Context, config: WebhookConfig) {
        context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putString(Constants.KEY_WEBHOOK_URL, config.endpoint.trim())
            putString(Constants.KEY_WEBHOOK_API_KEY, config.apiKey.trim())
            putString(Constants.KEY_WEBHOOK_SYSTEM_ID, config.systemId.trim())
            putString(Constants.KEY_WEBHOOK_SYSTEM_NAME, config.systemName.trim())
            putString(Constants.KEY_WEBHOOK_READER_ID, config.readerId.trim())
            putString(Constants.KEY_WEBHOOK_EVENT_ID, config.eventId.trim())
            apply()
        }
    }

    suspend fun sendInventoryUpdate(
        context: Context,
        activeTags: Map<String, ContinuousTag>,
        added: List<String>,
        removed: List<String>,
    ): WebhookResult = withContext(Dispatchers.IO) {
        val config = resolveDefaults(context, readConfig(context))
        val payload = buildPayload(config, activeTags, added, removed)
        postPayload(config, payload)
    }

    suspend fun sendMockPayload(context: Context): WebhookResult = withContext(Dispatchers.IO) {
        val config = resolveDefaults(context, readConfig(context))
        postPayload(config, buildMockPayload(config))
    }

    suspend fun sendTestPing(context: Context): WebhookResult = withContext(Dispatchers.IO) {
        val config = resolveDefaults(context, readConfig(context))
        val payload = buildPayload(config, emptyMap(), emptyList(), emptyList())
        postPayload(config, payload)
    }

    suspend fun verifySendAndReceive(context: Context, config: WebhookConfig): VerificationResult =
        withContext(Dispatchers.IO) {
            val resolved = resolveDefaults(context, config)
            if (resolved.endpoint.isBlank()) {
                return@withContext VerificationResult(
                    success = false,
                    message = "Webhook no configurado",
                )
            }

            val payload = buildMockPayload(resolved)
            val postResult = postPayload(resolved, payload)
            val sentJson = postResult.payload ?: gson.toJson(payload)

            if (!postResult.success) {
                return@withContext VerificationResult(
                    success = false,
                    postHttpCode = postResult.httpCode,
                    sentJson = sentJson,
                    message = "POST falló: ${postResult.message}",
                )
            }

            val eventId = payload["id"] as? String
                ?: return@withContext VerificationResult(
                    success = false,
                    postHttpCode = postResult.httpCode,
                    sentJson = sentJson,
                    message = "No se encontró id en el payload enviado",
                )

            val getUrl = "${Constants.DEFAULT_WEBHOOK_GET_INVENTORY_BASE}$eventId"
            val getResult = getInventory(eventId, resolved.apiKey)

            if (!getResult.success) {
                return@withContext VerificationResult(
                    success = false,
                    eventId = eventId,
                    postHttpCode = postResult.httpCode,
                    getHttpCode = getResult.httpCode,
                    sentJson = sentJson,
                    message = "GET falló ($getUrl): ${getResult.message}",
                )
            }

            val receivedJson = getResult.payload ?: ""
            val differences = compareInventoryPayloads(sentJson, receivedJson)

            VerificationResult(
                success = true,
                matches = differences.isEmpty(),
                eventId = eventId,
                postHttpCode = postResult.httpCode,
                getHttpCode = getResult.httpCode,
                sentJson = sentJson,
                receivedJson = receivedJson,
                differences = differences,
                message = if (differences.isEmpty()) {
                    "POST y GET coinciden para $eventId"
                } else {
                    "Datos recibidos difieren (${differences.size} diferencias)"
                },
            )
        }

    private fun getInventory(eventId: String, apiKey: String): WebhookResult {
        return try {
            val url = "${Constants.DEFAULT_WEBHOOK_GET_INVENTORY_BASE}$eventId"
            android.util.Log.d("InventoryWebhook", "GET $url")

            val requestBuilder = Request.Builder()
                .url(url)
                .get()
                .addHeader("Accept", "application/json")

            if (apiKey.isNotBlank()) {
                requestBuilder.addHeader("X-API-Key", apiKey)
            }

            val response = client.newCall(requestBuilder.build()).execute()
            val body = response.body?.string()
            android.util.Log.d("InventoryWebhook", "GET Response ${response.code}: $body")

            WebhookResult(
                success = response.isSuccessful,
                httpCode = response.code,
                message = if (response.isSuccessful) "OK" else "HTTP ${response.code}: ${body ?: "sin cuerpo"}",
                payload = body,
            )
        } catch (e: Exception) {
            android.util.Log.e("InventoryWebhook", "GET Error: ${e.message}", e)
            WebhookResult(false, message = e.message ?: "Error desconocido")
        }
    }

    private fun compareInventoryPayloads(sentJson: String, receivedJson: String): List<String> {
        val differences = mutableListOf<String>()
        val sent = parseJsonObject(sentJson) ?: return listOf("JSON enviado inválido")
        val received = parseJsonObject(receivedJson) ?: return listOf("JSON recibido inválido")

        compareField(differences, "type", sent, received)
        compareField(differences, "version", sent, received)
        compareField(differences, "id", sent, received)

        val sentData = sent.getAsJsonObject("data")
        val receivedData = received.getAsJsonObject("data")
        if (sentData == null || receivedData == null) {
            differences.add("Falta objeto data en uno de los payloads")
            return differences
        }

        compareField(differences, "data.systemId", sentData, receivedData, "systemId")
        compareField(differences, "data.systemName", sentData, receivedData, "systemName")
        compareField(differences, "data.count", sentData, receivedData, "count")
        compareJsonArrayAsSet(differences, "data.epcs", sentData, receivedData, "epcs")
        compareJsonArrayAsSet(differences, "data.added", sentData, receivedData, "added")
        compareJsonArrayAsSet(differences, "data.removed", sentData, receivedData, "removed")
        compareTagsByEpc(differences, sentData, receivedData)

        return differences
    }

    private fun compareTagsByEpc(differences: MutableList<String>, sentData: JsonObject, receivedData: JsonObject) {
        val sentTags = tagsByEpc(sentData.getAsJsonArray("tags"))
        val receivedTags = tagsByEpc(receivedData.getAsJsonArray("tags"))

        val allEpcs = (sentTags.keys + receivedTags.keys).toSet()
        for (epc in allEpcs.sorted()) {
            val sentTag = sentTags[epc]
            val receivedTag = receivedTags[epc]
            if (sentTag == null) {
                differences.add("Tag $epc presente en GET pero no en POST")
                continue
            }
            if (receivedTag == null) {
                differences.add("Tag $epc presente en POST pero no en GET")
                continue
            }
            compareField(differences, "tags[$epc].proximity", sentTag, receivedTag, "proximity")
            compareField(differences, "tags[$epc].proximityLabel", sentTag, receivedTag, "proximityLabel")
            compareField(differences, "tags[$epc].readerId", sentTag, receivedTag, "readerId")
            compareField(differences, "tags[$epc].antennaPort", sentTag, receivedTag, "antennaPort")
        }
    }

    private fun tagsByEpc(array: JsonArray?): Map<String, JsonObject> {
        if (array == null) return emptyMap()
        return array.mapNotNull { element ->
            val obj = element.asJsonObject
            val epc = obj.get("epc")?.asString ?: return@mapNotNull null
            epc to obj
        }.toMap()
    }

    private fun compareField(
        differences: MutableList<String>,
        label: String,
        sent: JsonObject,
        received: JsonObject,
        field: String = label.substringAfterLast('.'),
    ) {
        val sentValue = sent.get(field)
        val receivedValue = received.get(field)
        if (!jsonElementsEqual(sentValue, receivedValue)) {
            differences.add("$label: enviado=${sentValue ?: "null"}, recibido=${receivedValue ?: "null"}")
        }
    }

    private fun compareJsonArrayAsSet(
        differences: MutableList<String>,
        label: String,
        sent: JsonObject,
        received: JsonObject,
        field: String,
    ) {
        val sentSet = jsonArrayToStringSet(sent.get(field))
        val receivedSet = jsonArrayToStringSet(received.get(field))
        if (sentSet != receivedSet) {
            differences.add("$label: enviado=$sentSet, recibido=$receivedSet")
        }
    }

    private fun jsonArrayToStringSet(element: JsonElement?): Set<String> {
        if (element == null || !element.isJsonArray) return emptySet()
        return element.asJsonArray.map { it.asString }.toSet()
    }

    private fun jsonElementsEqual(a: JsonElement?, b: JsonElement?): Boolean {
        if (a == null && b == null) return true
        if (a == null || b == null) return false
        return a == b
    }

    private fun parseJsonObject(json: String): JsonObject? {
        return try {
            gson.fromJson(json, JsonObject::class.java)
        } catch (_: Exception) {
            null
        }
    }

    private fun postPayload(config: WebhookConfig, payload: Map<String, Any>): WebhookResult {
        return try {
            val json = gson.toJson(payload)
            android.util.Log.d("InventoryWebhook", "POST ${config.endpoint}")
            android.util.Log.d("InventoryWebhook", json)

            val requestBuilder = Request.Builder()
                .url(config.endpoint)
                .post(json.toRequestBody(JSON))
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")

            if (config.apiKey.isNotBlank()) {
                requestBuilder.addHeader("X-API-Key", config.apiKey)
            }

            val response = client.newCall(requestBuilder.build()).execute()
            val body = response.body?.string()
            android.util.Log.d("InventoryWebhook", "Response ${response.code}: $body")

            WebhookResult(
                success = response.isSuccessful,
                httpCode = response.code,
                message = if (response.isSuccessful) "OK" else "HTTP ${response.code}: ${body ?: "sin cuerpo"}",
                payload = json,
            )
        } catch (e: Exception) {
            android.util.Log.e("InventoryWebhook", "Error: ${e.message}", e)
            WebhookResult(false, message = e.message ?: "Error desconocido")
        }
    }

    private fun buildPayload(
        config: WebhookConfig,
        activeTags: Map<String, ContinuousTag>,
        added: List<String>,
        removed: List<String>,
    ): Map<String, Any> {
        val now = Instant.now()
        val timestamp = isoFormatter.format(now)
        val sortedTags = activeTags.values.sortedByDescending { it.lastSeen }
        val epcs = sortedTags.map { it.epc }

        return mapOf(
            "id" to config.eventId,
            "type" to "INVENTORY_LIST_UPDATE",
            "version" to "1",
            "timestamp" to timestamp,
            "data" to mapOf(
                "systemId" to config.systemId,
                "systemName" to config.systemName,
                "generatedAt" to timestamp,
                "count" to sortedTags.size,
                "epcs" to epcs,
                "tags" to sortedTags.map { tag ->
                    val proximity = ProximityHelper.fromRssi(tag.rssi)
                    mapOf(
                        "epc" to tag.epc,
                        "rssi" to tag.rssi.toDouble(),
                        "proximity" to proximity.code,
                        "proximityLabel" to proximity.label,
                        "readerId" to config.readerId,
                        "antennaPort" to tag.antenna,
                        "missedCycles" to tag.missedCycles,
                    )
                },
                "added" to added,
                "removed" to removed,
            ),
        )
    }

    private fun buildMockPayload(config: WebhookConfig): Map<String, Any> {
        val now = Instant.now()
        val timestamp = isoFormatter.format(now)

        val mockEpcs = listOf(
            "424430303031313032313900",
            "424430303031313230313100",
            "424430303031313230303500",
            "424430303031313230313000",
            "424430303031313230313200",
        )
        val mockTags = listOf(
            mockTag(mockEpcs[0], -52.5, "CERCA", "Cerca", config.readerId),
            mockTag(mockEpcs[1], -45.5, "CERCA", "Cerca", config.readerId),
            mockTag(mockEpcs[2], -63.5, "MEDIA", "Media", config.readerId),
            mockTag(mockEpcs[3], -52.5, "CERCA", "Cerca", config.readerId),
            mockTag(mockEpcs[4], -63.5, "MEDIA", "Media", config.readerId),
        )

        return mapOf(
            "id" to config.eventId,
            "type" to "INVENTORY_LIST_UPDATE",
            "version" to "1",
            "timestamp" to timestamp,
            "data" to mapOf(
                "systemId" to config.systemId,
                "systemName" to config.systemName,
                "generatedAt" to timestamp,
                "count" to mockEpcs.size,
                "epcs" to mockEpcs,
                "tags" to mockTags,
                "added" to mockEpcs,
                "removed" to emptyList<String>(),
            ),
        )
    }

    private fun mockTag(
        epc: String,
        rssi: Double,
        proximity: String,
        proximityLabel: String,
        readerId: String,
    ): Map<String, Any> = mapOf(
        "epc" to epc,
        "rssi" to rssi,
        "proximity" to proximity,
        "proximityLabel" to proximityLabel,
        "readerId" to readerId,
        "antennaPort" to 1,
        "missedCycles" to 0,
    )
}
