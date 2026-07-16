package com.dohealth.handheld.utils

object Constants {
    // UUIDs BLE
    const val SERVICE_UUID = "0000ffe0-0000-1000-8000-00805f9b34fb"
    const val WRITE_UUID = "0000ffe3-0000-1000-8000-00805f9b34fb"
    const val NOTIFY_UUID = "0000ffe4-0000-1000-8000-00805f9b34fb"
    
    // Modos de operación
    const val MODE_RFID = 0x00
    const val MODE_BARCODE = 0x01
    
    // Preferencias
    const val PREFS_NAME = "dohealth_handheld_prefs"
    const val KEY_SERVER_URL = "server_url"
    /** Base URL de la API ESFERICA (debe configurarse en la app; con barra final). */
    const val KEY_ESFERICA_API_BASE = "esferica_api_base"
    const val KEY_POWER_LEVEL = "power_level"
    const val KEY_Q_VALUE = "q_value"
    const val KEY_SESSION = "session"
    const val KEY_ANTENNA = "antenna"
    const val KEY_INQUIRY_AREA = "inquiry_area"
    const val KEY_FREQUENCY_REGION = "frequency_region"
    const val KEY_FILTER_TIME = "filter_time"
    const val KEY_TRIGGER_TIME = "trigger_time"
    const val KEY_BUZZER_TIME = "buzzer_time"
    const val KEY_POLLING_INTERVAL = "polling_interval"
    const val KEY_ACS_ADDR = "acs_addr"
    const val KEY_ACS_DATA_LEN = "acs_data_len"
    const val KEY_WORK_MODE = "work_mode"
    const val KEY_RFID_DISTANCE_PRESET = "rfid_distance_preset"

    // Webhook lectura continua
    const val KEY_WEBHOOK_URL = "webhook_url"
    const val KEY_WEBHOOK_API_KEY = "webhook_api_key"
    const val KEY_WEBHOOK_SYSTEM_ID = "webhook_system_id"
    const val KEY_WEBHOOK_SYSTEM_NAME = "webhook_system_name"
    const val KEY_WEBHOOK_READER_ID = "webhook_reader_id"
    const val KEY_WEBHOOK_EVENT_ID = "webhook_event_id"
    const val KEY_CONNECTED_READER_NAME = "connected_reader_name"
    const val KEY_CONNECTED_READER_ADDRESS = "connected_reader_address"

    const val CONTINUOUS_TAG_TIMEOUT_MS = 10_000L
    
    // Valores por defecto
    const val DEFAULT_POWER_LEVEL = 26
    const val DEFAULT_Q_VALUE = 4
    const val DEFAULT_SESSION = 0
    const val DEFAULT_ANTENNA = 1
    const val DEFAULT_INQUIRY_AREA = 0x01 // EPC
    const val DEFAULT_FREQUENCY_REGION = 0x01 // US
    const val DEFAULT_FILTER_TIME = 0 // Sin filtro
    const val DEFAULT_TRIGGER_TIME = 1 // 1 segundo
    const val DEFAULT_BUZZER_TIME = 1 // 10ms
    const val DEFAULT_POLLING_INTERVAL = 1 // 10ms
    const val DEFAULT_ACS_ADDR = 0
    const val DEFAULT_ACS_DATA_LEN = 0
    const val DEFAULT_WORK_MODE = 0 // Modo respuesta
    
    // URL del servidor (mock - puedes cambiar esto)
    const val DEFAULT_SERVER_URL = "https://api.dohealth.com/inventory"
    /** Vacío a propósito: la URL la debe indicar quien opera el backend ESFERICA. */
    const val DEFAULT_ESFERICA_API_BASE = ""
    const val DEFAULT_WEBHOOK_URL =
        "https://api.dohealth-epione.com/epione-core-api/epione_event/receive_inventory"
    const val DEFAULT_WEBHOOK_GET_INVENTORY_BASE =
        "https://api.dohealth-epione.com/epione-core-api/epione_event/inventory/get/"
}

