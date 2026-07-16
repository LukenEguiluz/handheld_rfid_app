# Entrega — doHealth Handheld

**Producto:** aplicación Android handheld RFID / código de barras  
**Package:** `com.dohealth.handheld`  
**Versión en build:** 1.0.0 (versionCode 1)  
**Artefacto SDK incluido:** `app/libs/cf-sdk-v1.0.3.aar`

## Qué se entrega

- Código fuente del módulo `app`
- Gradle root (`build.gradle`, `settings.gradle`, `gradle.properties`, wrapper en `gradle/`)
- SDK CF embebido en `app/libs/`
- `README.md` con arquitectura, módulos e integración Epione

No se incluye: keystore de firma, `local.properties`, ni credenciales (API keys, URL de ESFERICA de producción).

## Cómo verificar que abre

1. Abrir el proyecto en Android Studio (AGP 8.7.2+).
2. Sync Gradle.
3. Compilar variante `debug`.
4. Instalar en handheld o teléfono con BLE y conectar el lector.

Si falta el AAR o Jetifier está activo, la sync falla: ver sección de requisitos en el README.

## Alcance funcional entregado

| Área | Estado |
|------|--------|
| Conexión BLE + standby | Operativo |
| Inventario RFID / barcode + sesiones | Operativo |
| Lectura continua + expiración 10 s | Operativo |
| Webhook Epione POST/GET + pruebas en app | Operativo (requiere API key) |
| ESFERICA (clientes, almacenes, reconcile) | Operativo (requiere URL/datos del backend) |
| HGM (catálogo CSV/Excel) | Operativo |
| Relación por códigos | Operativo |
| Config RF + presets de distancia | Operativo |

## Información que debe aportar quien recibe el proyecto

Antes de usar lectura continua / Epione en serio:

1. **API key** del webhook (header `X-API-Key`) — configurarla en la app. Sin ella los envíos fallan o no autentican.
2. Confirmar con backend que el GET de inventario por `event_id` responde con el mismo contrato que el POST.
3. Opcional: System ID, Event ID y Reader ID fijos por instalación (si no, se usan los generados del dispositivo).

Para el módulo ESFERICA:

1. **URL base de la API** y cualquier dato de acceso que use ese entorno — hay que ingresarlos en la app; no vienen definidos en esta entrega.
2. Acceso a clientes/almacenes de prueba si aplica.

## Documentación

Detalle técnico en `README.md`. Este archivo es la hoja de entrega.
