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

No se incluye: keystore de firma, `local.properties`, ni credenciales de API (la API key se configura en la app).

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
| Webhook Epione POST/GET + pruebas en app | Operativo |
| ESFERICA (clientes, almacenes, reconcile) | Operativo |
| HGM (catálogo CSV/Excel) | Operativo |
| Relación por códigos | Operativo |
| Config RF + presets de distancia | Operativo |

## Integración pendiente del lado servidor

- Validar API key de Epione en el entorno real.
- Confirmar que el GET `/inventory/get/{event_id}` devuelve el mismo contrato que el POST (la app ya tiene botón de verificación).
- Ajustar System ID / Event ID / Reader ID por instalación si no se usan los generados por dispositivo.

## Documentación

Todo el detalle técnico está en `README.md`. Este archivo es solo la hoja de entrega.
