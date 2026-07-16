package com.dohealth.handheld.data.model

data class ContinuousTag(
    val epc: String,
    val rssi: Int,
    val antenna: Int,
    var readCount: Int = 1,
    var lastSeen: Long = System.currentTimeMillis(),
    var missedCycles: Int = 0,
)
