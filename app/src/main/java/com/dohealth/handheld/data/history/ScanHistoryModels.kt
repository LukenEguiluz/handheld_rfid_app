package com.dohealth.handheld.data.history

data class ScanHistoryEntry(
    val id: String,
    val code: String,
    val timestamp: Long,
    /** "RFID" o "BARCODE" */
    val mode: String
)

