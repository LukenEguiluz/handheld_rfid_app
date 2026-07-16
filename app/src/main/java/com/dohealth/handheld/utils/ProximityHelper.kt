package com.dohealth.handheld.utils

data class ProximityInfo(
    val code: String,
    val label: String,
)

object ProximityHelper {

    fun fromRssi(rssi: Int): ProximityInfo = when {
        rssi >= -50 -> ProximityInfo("CERCA", "Cerca")
        rssi >= -65 -> ProximityInfo("MEDIA", "Media")
        else -> ProximityInfo("LEJOS", "Lejos")
    }
}
