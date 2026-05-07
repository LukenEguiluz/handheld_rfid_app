package com.dohealth.handheld.network

import com.dohealth.handheld.data.esferica.ExpectedInventoryItemDto
import com.dohealth.handheld.data.esferica.InventoryItemLiteDto
import com.dohealth.handheld.utils.Constants
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object EsfericaApiClient {

    private val gson = Gson()

    /**
     * Retrofit exige una URL absoluta válida terminada en /.
     * Si el usuario escribe sólo el host (sin esquema), se asume https://.
     */
    fun normalizeBaseUrl(raw: String): String {
        val fallback = Constants.DEFAULT_ESFERICA_API_BASE
        val t = raw.trim().ifBlank { return fallback }
        val withScheme = when {
            t.startsWith("http://", ignoreCase = true) -> t
            t.startsWith("https://", ignoreCase = true) -> t
            else -> "https://$t"
        }.trim()
        return if (withScheme.endsWith("/")) withScheme else "$withScheme/"
    }

    fun parseInventoryJson(body: String): List<ExpectedInventoryItemDto> {
        val type = object : TypeToken<List<ExpectedInventoryItemDto>>() {}.type
        return try {
            val t = body.trim()
            when {
                t.startsWith("[") -> gson.fromJson<List<ExpectedInventoryItemDto>>(t, type) ?: emptyList()
                t.startsWith("{") -> {
                    val root = JsonParser().parse(t).asJsonObject
                    when {
                        root.has("items") && root["items"].isJsonArray ->
                            gson.fromJson<List<ExpectedInventoryItemDto>>(root["items"].toString(), type) ?: emptyList()
                        else -> emptyList()
                    }
                }
                else -> emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun parseLookupJson(body: String): List<InventoryItemLiteDto> {
        val type = object : TypeToken<List<InventoryItemLiteDto>>() {}.type
        return try {
            val t = body.trim()
            when {
                t.startsWith("[") -> gson.fromJson<List<InventoryItemLiteDto>>(t, type) ?: emptyList()
                t.startsWith("{") -> {
                    val root = JsonParser().parse(t).asJsonObject
                    val nested = when {
                        root.has("items") && root["items"].isJsonArray -> root["items"].toString()
                        root.has("data") && root["data"].isJsonArray -> root["data"].toString()
                        else -> null
                    }
                    when {
                        nested != null -> gson.fromJson<List<InventoryItemLiteDto>>(nested, type) ?: emptyList()
                        else -> emptyList()
                    }
                }
                else -> emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun createService(baseUrl: String): EsfericaApiService {
        val normalized = normalizeBaseUrl(baseUrl)
        val log = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .addInterceptor(log)
            .build()
        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(EsfericaApiService::class.java)
    }

}
