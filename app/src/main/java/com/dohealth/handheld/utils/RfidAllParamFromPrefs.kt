package com.dohealth.handheld.utils

import android.content.SharedPreferences
import com.cf.beans.AllParamBean

/**
 * Construye [AllParamBean] coherente con lo guardado en preferencias (misma lógica base que
 * [com.dohealth.handheld.ui.config.RfidConfigActivity]) para poder enviar potencia RF sin abrir la pantalla de config.
 */
fun buildAllParamBeanFromPrefs(prefs: SharedPreferences, powerDbm: Int): AllParamBean {
    val qValue = prefs.getInt(Constants.KEY_Q_VALUE, Constants.DEFAULT_Q_VALUE)
    val session = prefs.getInt(Constants.KEY_SESSION, Constants.DEFAULT_SESSION)
    val filterTime = prefs.getInt(Constants.KEY_FILTER_TIME, Constants.DEFAULT_FILTER_TIME)
    val pollingInterval = prefs.getInt(Constants.KEY_POLLING_INTERVAL, Constants.DEFAULT_POLLING_INTERVAL)
    return buildAllParamBean(
        prefs = prefs,
        powerDbm = powerDbm,
        qValue = qValue,
        session = session,
        filterTime = filterTime,
        pollingInterval = pollingInterval,
    )
}

/** Parámetros del preset de distancia + resto desde preferencias (antena, buzzer, modo trabajo, etc.). */
fun buildAllParamBeanFromPreset(preset: RfidDistancePreset, prefs: SharedPreferences): AllParamBean =
    buildAllParamBean(
        prefs = prefs,
        powerDbm = preset.powerDbm,
        qValue = preset.qValue,
        session = preset.session,
        filterTime = preset.filterTimeSeconds,
        pollingInterval = preset.pollingInterval,
    )

fun buildAllParamBeanFromSnapshot(snapshot: RfidParamSnapshot, prefs: SharedPreferences): AllParamBean =
    buildAllParamBean(
        prefs = prefs,
        powerDbm = snapshot.powerDbm,
        qValue = snapshot.qValue,
        session = snapshot.session,
        filterTime = snapshot.filterTimeSeconds,
        pollingInterval = snapshot.pollingInterval,
    )

private fun buildAllParamBean(
    prefs: SharedPreferences,
    powerDbm: Int,
    qValue: Int,
    session: Int,
    filterTime: Int,
    pollingInterval: Int,
): AllParamBean {
    val antenna = prefs.getInt(Constants.KEY_ANTENNA, Constants.DEFAULT_ANTENNA).coerceAtLeast(1)
    val buzzerTime = prefs.getInt(Constants.KEY_BUZZER_TIME, Constants.DEFAULT_BUZZER_TIME)
    val workMode = prefs.getInt(Constants.KEY_WORK_MODE, Constants.DEFAULT_WORK_MODE)
    val inquiryArea = prefs.getInt(Constants.KEY_INQUIRY_AREA, Constants.DEFAULT_INQUIRY_AREA).coerceIn(1, 6)
    val power = powerDbm.coerceIn(0, 33)

    return AllParamBean().apply {
        mAddr = 0x00
        mRFIDPRO = 0x00
        mInterface = 0x00.toByte()
        mBaudrate = 0x04
        mWGSet = 0x00
        mRfidFreq = AllParamBean.RfidFreq().apply {
            mREGION = 0x01.toByte()
            mSTRATFREI = byteArrayOf(0x03, 0x86.toByte())
            mSTRATFRED = byteArrayOf(0x02, 0xEE.toByte())
            mSTEPFRE = byteArrayOf(0x01, 0xF4.toByte())
            mCN = 0x32
        }
        mAcsAddr = 0x00
        mAcsDataLen = 0x00
        mTriggerTime = 1
        mRfidPower = power.toByte()
        mQValue = qValue.toByte()
        mSession = session.toByte()
        mAnt = (1 shl (antenna - 1)).toByte()
        mFilterTime = filterTime.toByte()
        mBuzzerTime = buzzerTime.toByte()
        mPollingInterval = pollingInterval.toByte()
        mWorkMode = workMode.toByte()
        mInquiryArea = inquiryArea.toByte()
    }
}
