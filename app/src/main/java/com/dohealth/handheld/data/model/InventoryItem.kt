package com.dohealth.handheld.data.model

data class InventoryItem(
    val data: String,
    val rssi: Int,
    val antenna: Int,
    val timestamp: Long,
    val mode: String, // "RFID" o "Barcode"
    val readCount: Int = 1 // Número de veces que se ha leído este item
)

