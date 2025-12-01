# Instrucciones de Configuración

## 1. Copiar el SDK

Antes de compilar, necesitas copiar el SDK RFID:

1. Copiar el archivo `cf-sdk-v1.0.0.aar` desde:
   ```
   ../aar/cf-sdk-v1.0.0.aar
   ```

2. Crear la carpeta `libs` en la raíz del proyecto:
   ```
   doHealth_Handheld/libs/
   ```

3. Pegar el archivo `cf-sdk-v1.0.0.aar` en esa carpeta

4. El archivo `app/build.gradle` ya está configurado para usar:
   ```gradle
   implementation files('../libs/cf-sdk-v1.0.0.aar')
   ```

## 2. Configurar URL del Servidor

Para que la aplicación pueda enviar datos al servidor:

1. Abre `Constants.kt`
2. Modifica la constante `KEY_SERVER_URL` o configura la URL desde la app
3. El formato esperado por el servidor está documentado en `README.md`

## 3. Compilar

1. Abre Android Studio
2. File > Open > Selecciona la carpeta `doHealth_Handheld`
3. Espera a que Gradle sincronice
4. Build > Make Project
5. Run > Run 'app'

## 4. Permisos

La aplicación solicitará automáticamente los permisos necesarios:
- Bluetooth
- Bluetooth Connect/Scan (Android 12+)
- Ubicación (requerido para Bluetooth)

## 5. Uso

1. **Primera vez**: La app mostrará la pantalla de conexión Bluetooth
2. **Buscar dispositivo**: Presiona "Buscar de nuevo" para escanear
3. **Conectar**: Toca un dispositivo de la lista para conectar
4. **Seleccionar modo**: Elige RFID o Código de Barras
5. **Inventariar**: Presiona "Iniciar Inventario"
6. **Exportar**: Usa el menú (⋮) para exportar o enviar datos

## Notas

- El dispositivo debe estar encendido y con Bluetooth activado
- El dispositivo debe tener el Company ID: 0x2795 (filtrado automático)
- Los archivos Excel se guardan en: `/Android/data/com.dohealth.handheld/files/`


