# doHealth Handheld

App Android (Kotlin) para inventarios con handheld RFID UHF y código de barras. Se conecta al lector por Bluetooth LE usando el SDK CF (`cf-sdk-v1.0.3.aar`).

Package: `com.dohealth.handheld` · versión `1.0.0` (versionCode 1)

---

## Requisitos

| | |
|---|---|
| Android | API 26+ (Android 8.0) |
| Target / compile SDK | 34 |
| Hardware | Bluetooth LE |
| IDE | Android Studio + AGP **8.7.2** o superior |
| SDK del lector | `app/libs/cf-sdk-v1.0.3.aar` (bytecode Java 21; no activar Jetifier) |

Sin el AAR el proyecto no compila. Ese archivo debe viajar con el repo.

---

## Cómo abrir y compilar

1. Clonar / copiar el repositorio.
2. Abrir la carpeta raíz en Android Studio.
3. Dejar que Gradle sincronice (genera `local.properties` con la ruta del SDK de Android; ese archivo no se versiona).
4. Run en un dispositivo con BLE (recomendado el handheld real).

Build types: `debug` y `release` (minify desactivado en release).

---

## Flujo de la app

```
Splash → Connection (scan/conexión BLE) → Main (menú de módulos)
```

En Main se elige el módulo. Al salir de una pantalla de lectura el dispositivo queda en standby (modo RFID, inventario detenido).

---

## Módulos

### RFID
Lectura de tags UHF. Cada EPC se registra una sola vez (sin contar relecturas). Hay historial de sesiones, exportación a TXT y envío genérico a servidor.

### Lectura continua
Inventario en vivo pensado para integración Epione:

- Solo RFID.
- Cada lectura suma al contador del tag y al total de lecturas.
- Si un tag no se vuelve a detectar en **10 segundos**, sale de la lista (también con la lectura detenida). Cuando la lista queda vacía, el total de lecturas vuelve a 0.
- En cada alta/baja se hace POST al webhook con tipo `INVENTORY_LIST_UPDATE`.

Configuración del webhook: menú ⋮ de Lectura continua, o **Configuración → Webhook lectura continua**.

### Código de barras
Escaneo en modo scanhead. Cuenta repeticiones del mismo código.

### ESFERICA
Conteo físico por cliente/almacén contra la API ESFERICA (base por defecto `https://esferica.leyluz.com/`). Endpoints usados: `clients`, `warehouses`, `inventory`, `inventory/reconcile`, `rfid/lookup`.

### HGM (banco de sangre)
Conteo RFID frente a un catálogo CSV/Excel (Unidad, SKU, Hemocomponente, Grupo ABO/Rh). El SKU ASCII del archivo se convierte a EPC hex para el match.

### Relación por códigos
Asociación entre códigos leídos (sesión propia).

### Configuración
Parámetros RF del lector (potencia, Q, sesión, antena, filtro, buzzer, polling, modo de trabajo, área de consulta) y acceso al webhook de lectura continua. También hay presets de distancia de lectura (contacto / cerca / media / lejos) desde el menú de inventario.

---

## Webhook Epione (lectura continua)

### Endpoints

| Método | URL |
|--------|-----|
| POST | `https://api.dohealth-epione.com/epione-core-api/epione_event/receive_inventory` |
| GET | `https://api.dohealth-epione.com/epione-core-api/epione_event/inventory/get/{event_id}` |

Auth: header `X-API-Key` (configurable en la app).

### Identificadores

`systemId` y `eventId` se generan de forma estable a partir del Android ID, el lector BLE conectado (nombre/MAC) y el modelo del teléfono. Se pueden editar a mano o regenerar con **Restaurar IDs desde dispositivo**.

Campos en pantalla: URL, API key, Event ID, System ID, System Name, Reader ID.

### Payload (POST)

```json
{
  "id": "evt_…",
  "type": "INVENTORY_LIST_UPDATE",
  "version": "1",
  "timestamp": "2026-06-23T19:29:25.226513Z",
  "data": {
    "systemId": "…",
    "systemName": "…",
    "generatedAt": "2026-06-23T19:29:25.226465Z",
    "count": 2,
    "epcs": ["4244…", "4244…"],
    "tags": [
      {
        "epc": "4244…",
        "rssi": -52.5,
        "proximity": "CERCA",
        "proximityLabel": "Cerca",
        "readerId": "IHT-2",
        "antennaPort": 1,
        "missedCycles": 0
      }
    ],
    "added": ["4244…"],
    "removed": []
  }
}
```

Proximidad por RSSI: ≥ −50 → CERCA · ≥ −65 → MEDIA · resto → LEJOS.

### Pruebas desde la app

En la pantalla de configuración del webhook:

- **Probar conexión** — POST con lista vacía.
- **Enviar payload de prueba (mock)** — POST con 5 EPCs de ejemplo.
- **Verificar POST vs GET** — envía el mock, consulta el GET del mismo `event_id` y compara campos relevantes (id, type, systemId, epcs, tags, etc.).

Código: `InventoryWebhookService`, `WebhookDeviceIdentity`, `ContinuousReadingActivity`, `ContinuousConfigActivity`.

---

## Estructura del código

```
app/src/main/java/com/dohealth/handheld/
  ui/
    connection/     ConnectionActivity
    main/           MainActivity
    inventory/      InventoryActivity (+ adapter)
    inventorysessions/
    continuous/     Lectura continua + config webhook
    config/         RfidConfigActivity
    esferica/       Sesiones, selección, conteo, resultados
    hgm/            Sesiones, import, conteo
    relacion/       Sesiones + lectura
    history/        Historial de scans
    splash/
  data/             Modelos y stores (SharedPreferences + Gson)
  network/          Retrofit ESFERICA
  utils/            BLE presets, export, ApiService, webhook
app/libs/
  cf-sdk-v1.0.3.aar
```

UI con View Binding y Material. Navegación por Intents (sin Navigation Graph de pantallas principales). Estado de sesiones en SharedPreferences, no Room.

---

## Dependencias relevantes

- AndroidX AppCompat / Material / Lifecycle / Coroutines
- Retrofit 2.9 + OkHttp + Gson (ESFERICA y webhooks)
- Apache POI 5.2.4 (Excel HGM / export donde aplique)
- `cf-sdk-v1.0.3.aar` (BleCore, CmdBuilder, TagInfoBean, …)

UUIDs BLE del servicio: `ffe0` / write `ffe3` / notify `ffe4` (ver `Constants.kt`).

---

## Notas para quien toma el proyecto

- `local.properties` es de cada máquina; no lo subas.
- Jetifier está apagado a propósito: el AAR del SDK no pasa por Jetifier (Java 21).
- La URL genérica `api.dohealth.com/inventory` del módulo RFID clásico es distinta del webhook Epione; no las mezclar.
- Lectura continua no guarda sesiones: es estado en memoria + push al webhook.
- Para validar integración Epione: configurar API key → mock → **Verificar POST vs GET**.

---

## Contacto / propiedad

Código y marca doHealth. Entrega de código fuente del cliente handheld RFID.
