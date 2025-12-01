# doHealth Handheld

Aplicación Android para gestión de inventarios con handheld RFID y código de barras.

## Características

- ✅ Conexión Bluetooth con dispositivos RFID
- ✅ Modo RFID para lectura de tags UHF
- ✅ Modo Código de Barras/QR para escaneo
- ✅ Configuración de parámetros RFID (potencia, Q, sesión, antena)
- ✅ Exportación de inventarios a Excel
- ✅ Envío de datos a servidor mediante API REST

## Requisitos

- Android 5.0 (API 21) o superior
- Bluetooth Low Energy (BLE)
- Permisos de Bluetooth y ubicación

## Instalación

1. Copiar el archivo `cf-sdk-v1.0.0.aar` a la carpeta `doHealth_Handheld/libs/`
2. Abrir el proyecto en Android Studio
3. Sincronizar Gradle
4. Compilar y ejecutar

## Configuración

### URL del Servidor

Para configurar la URL del servidor donde se enviarán los inventarios:

1. Ir a Configuración en la aplicación
2. Ingresar la URL del endpoint (ej: `https://api.ejemplo.com/inventory`)
3. Guardar configuración

### Formato de Datos API

Los datos se envían en formato JSON con la siguiente estructura:

```json
{
  "items": [
    {
      "data": "E20034120102000000000000",
      "rssi": -50,
      "antenna": 1,
      "timestamp": 1234567890,
      "mode": "RFID"
    }
  ],
  "total_count": 100,
  "unique_count": 50
}
```

## Uso

1. **Conectar Dispositivo**: Al abrir la app, se busca y conecta el dispositivo Bluetooth
2. **Seleccionar Modo**: Elegir entre RFID o Código de Barras
3. **Iniciar Inventario**: Presionar "Iniciar Inventario"
4. **Exportar/Enviar**: Usar el menú para exportar a Excel o enviar al servidor

## Colores

- **Rojo Primario**: #DC143C
- **Gris Primario**: #616161
- **Blanco**: #FFFFFF

## Licencia

Copyright © 2024 doHealth


