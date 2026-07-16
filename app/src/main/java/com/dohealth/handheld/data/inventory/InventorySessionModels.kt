package com.dohealth.handheld.data.inventory

import com.dohealth.handheld.data.model.InventoryItem

data class InventorySession(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** "RFID" o "BARCODE" */
    val mode: String,
    val items: List<InventoryItem> = emptyList()
)

