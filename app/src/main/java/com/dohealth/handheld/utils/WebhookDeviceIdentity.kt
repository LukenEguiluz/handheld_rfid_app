package com.dohealth.handheld.utils

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.util.UUID

/**
 * Identificadores determinísticos por dispositivo (Android + lector BLE conectado).
 * Se muestran como valor por defecto y pueden editarse en configuración.
 */
object WebhookDeviceIdentity {

    fun deviceSeed(context: Context): String {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown-android"
        val readerAddress = prefs.getString(Constants.KEY_CONNECTED_READER_ADDRESS, "").orEmpty()
        val readerName = prefs.getString(Constants.KEY_CONNECTED_READER_NAME, "").orEmpty()
        return listOf(
            androidId,
            readerAddress,
            readerName,
            Build.MANUFACTURER,
            Build.MODEL,
            Build.DEVICE,
        ).joinToString("|")
    }

    fun defaultSystemId(context: Context): String {
        return uuidFromSeed(context, "dohealth-system").toString()
    }

    fun defaultEventId(context: Context): String {
        val uuid = uuidFromSeed(context, "dohealth-event")
        return "evt_${uuid.toString().replace("-", "")}"
    }

    fun defaultReaderId(context: Context): String {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val readerName = prefs.getString(Constants.KEY_CONNECTED_READER_NAME, "").orEmpty()
        if (readerName.isNotBlank()) return readerName
        val readerAddress = prefs.getString(Constants.KEY_CONNECTED_READER_ADDRESS, "").orEmpty()
        if (readerAddress.isNotBlank()) return readerAddress.replace(":", "").takeLast(8)
        return "handheld-${Build.MODEL.replace(" ", "-")}"
    }

    fun defaultSystemName(context: Context): String {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val readerName = prefs.getString(Constants.KEY_CONNECTED_READER_NAME, "").orEmpty()
        if (readerName.isNotBlank()) return readerName
        return "doHealth-${Build.MODEL.replace(" ", "-")}"
    }

    fun saveConnectedReader(context: Context, name: String, address: String) {
        context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putString(Constants.KEY_CONNECTED_READER_NAME, name.trim())
            putString(Constants.KEY_CONNECTED_READER_ADDRESS, address.trim())
            apply()
        }
    }

    private fun uuidFromSeed(context: Context, namespace: String): UUID {
        val bytes = "$namespace:${deviceSeed(context)}".toByteArray(Charsets.UTF_8)
        return UUID.nameUUIDFromBytes(bytes)
    }
}
