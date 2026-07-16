package com.dohealth.handheld.utils

import android.content.Context
import android.content.SharedPreferences
import com.dohealth.handheld.R

enum class RfidDistancePreset(
    val key: String,
    val titleRes: Int,
    val powerDbm: Int,
    val qValue: Int,
    val session: Int,
    val filterTimeSeconds: Int,
    val pollingInterval: Int,
    /** true = inventario masivo; false = lectura de una sola etiqueta. */
    val isBulkReading: Boolean,
) {
    /** Potencia mínima, Q=0 y Session S0: lectura individual de una sola etiqueta. */
    ZERO(
        key = "zero",
        titleRes = R.string.rfid_preset_zero,
        powerDbm = 0,
        qValue = 0,
        session = 0,
        filterTimeSeconds = 0,
        pollingInterval = 1,
        isBulkReading = false,
    ),
    /** Inventario masivo a larga distancia: potencia alta, Q alto, Session S1. */
    FAR(
        key = "far",
        titleRes = R.string.rfid_preset_far,
        powerDbm = 26,
        qValue = 6,
        session = 1,
        filterTimeSeconds = 0,
        pollingInterval = 1,
        isBulkReading = true,
    ),
    /** Inventario masivo a distancia media. */
    MEDIUM(
        key = "medium",
        titleRes = R.string.rfid_preset_medium,
        powerDbm = 18,
        qValue = 5,
        session = 1,
        filterTimeSeconds = 0,
        pollingInterval = 1,
        isBulkReading = true,
    ),
    /** Inventario masivo muy cercano: potencia baja, Q de bulk para varias etiquetas. */
    NEAR(
        key = "near",
        titleRes = R.string.rfid_preset_near,
        powerDbm = 10,
        qValue = 4,
        session = 1,
        filterTimeSeconds = 0,
        pollingInterval = 1,
        isBulkReading = true,
    ),
    ;

    fun matches(powerDbm: Int, qValue: Int, session: Int, filterTimeSeconds: Int, pollingInterval: Int): Boolean =
        this.powerDbm == powerDbm &&
            this.qValue == qValue &&
            this.session == session &&
            this.filterTimeSeconds == filterTimeSeconds &&
            this.pollingInterval == pollingInterval

    companion object {
        val bulkPresets: List<RfidDistancePreset> = entries.filter { it.isBulkReading }
    }
}

object RfidDistancePresets {

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    fun savePresetToPrefs(context: Context, preset: RfidDistancePreset) {
        prefs(context).edit()
            .putString(Constants.KEY_RFID_DISTANCE_PRESET, preset.key)
            .putInt(Constants.KEY_POWER_LEVEL, preset.powerDbm)
            .putInt(Constants.KEY_Q_VALUE, preset.qValue)
            .putInt(Constants.KEY_SESSION, preset.session)
            .putInt(Constants.KEY_FILTER_TIME, preset.filterTimeSeconds)
            .putInt(Constants.KEY_POLLING_INTERVAL, preset.pollingInterval)
            .commit()
    }

    /** Preset activo según preferencias guardadas o coincidencia exacta de parámetros. */
    fun currentPreset(context: Context): RfidDistancePreset? {
        val p = prefs(context)
        p.getString(Constants.KEY_RFID_DISTANCE_PRESET, null)?.let { key ->
            RfidDistancePreset.entries.find { it.key == key }?.let { return it }
        }
        return matchFromPrefs(p)
    }

    fun matchFromPrefs(prefs: SharedPreferences): RfidDistancePreset? {
        val power = prefs.getInt(Constants.KEY_POWER_LEVEL, Int.MIN_VALUE)
        if (power == Int.MIN_VALUE) return null
        val q = prefs.getInt(Constants.KEY_Q_VALUE, Constants.DEFAULT_Q_VALUE)
        val session = prefs.getInt(Constants.KEY_SESSION, Constants.DEFAULT_SESSION)
        val filter = prefs.getInt(Constants.KEY_FILTER_TIME, Constants.DEFAULT_FILTER_TIME)
        val polling = prefs.getInt(Constants.KEY_POLLING_INTERVAL, Constants.DEFAULT_POLLING_INTERVAL)
        return RfidDistancePreset.entries.find { it.matches(power, q, session, filter, polling) }
    }
}
