package com.dohealth.handheld.utils

import com.dohealth.handheld.data.model.InventoryItem
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object ApiService {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    private val JSON = "application/json; charset=utf-8".toMediaType()
    
    suspend fun sendInventory(context: android.content.Context, items: List<InventoryItem>): Boolean = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences(Constants.PREFS_NAME, android.content.Context.MODE_PRIVATE)
            var serverUrl = prefs.getString(Constants.KEY_SERVER_URL, "") ?: ""
            
            // Si no hay URL configurada, usar una URL mock/por defecto
            if (serverUrl.isEmpty()) {
                serverUrl = Constants.DEFAULT_SERVER_URL
            }
            
            val payload = mutableMapOf<String, Any>().apply {
                put("items", items.map { item ->
                    mapOf(
                        "data" to item.data,
                        "rssi" to item.rssi,
                        "antenna" to item.antenna,
                        "timestamp" to item.timestamp,
                        "mode" to item.mode,
                        "read_count" to item.readCount
                    )
                })
                put("total_count", items.size)
                put("unique_count", items.distinctBy { it.data }.size)
                put("device_mode", items.firstOrNull()?.mode ?: "RFID")
                put("exported_at", System.currentTimeMillis())
            }
            
            val json = gson.toJson(payload)
            android.util.Log.d("ApiService", "Enviando datos a: $serverUrl")
            android.util.Log.d("ApiService", "Payload: $json")
            
            val body = json.toRequestBody(JSON)
            
            val request = Request.Builder()
                .url(serverUrl)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            android.util.Log.d("ApiService", "Respuesta del servidor: ${response.code} - $responseBody")
            
            response.isSuccessful
        } catch (e: Exception) {
            android.util.Log.e("ApiService", "Error al enviar datos: ${e.message}", e)
            e.printStackTrace()
            false
        }
    }
    
    data class InventoryRequest(
        val items: List<InventoryItemDto>,
        val total_count: Int,
        val unique_count: Int
    )
    
    data class InventoryItemDto(
        val data: String,
        val rssi: Int,
        val antenna: Int,
        val timestamp: Long,
        val mode: String
    )
}

