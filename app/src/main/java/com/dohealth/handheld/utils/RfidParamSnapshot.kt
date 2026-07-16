package com.dohealth.handheld.utils

import android.content.SharedPreferences

/** Parámetros RFID de distancia/lectura para restaurar tras un modo temporal (p. ej. verificación 0 dBm). */
data class RfidParamSnapshot(
    val powerDbm: Int,
    val qValue: Int,
    val session: Int,
    val filterTimeSeconds: Int,
    val pollingInterval: Int,
) {
    companion object {
        fun fromPrefs(prefs: SharedPreferences): RfidParamSnapshot =
            RfidParamSnapshot(
                powerDbm = prefs.getInt(Constants.KEY_POWER_LEVEL, Constants.DEFAULT_POWER_LEVEL),
                qValue = prefs.getInt(Constants.KEY_Q_VALUE, Constants.DEFAULT_Q_VALUE),
                session = prefs.getInt(Constants.KEY_SESSION, Constants.DEFAULT_SESSION),
                filterTimeSeconds = prefs.getInt(Constants.KEY_FILTER_TIME, Constants.DEFAULT_FILTER_TIME),
                pollingInterval = prefs.getInt(Constants.KEY_POLLING_INTERVAL, Constants.DEFAULT_POLLING_INTERVAL),
            )
    }
}
