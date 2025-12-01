# Instalación Rápida - doHealth Handheld

## Opción 1: Usando Android Studio (Recomendado)

1. **Abrir el proyecto**:
   - Abre Android Studio
   - File > Open
   - Selecciona la carpeta `doHealth_Handheld`
   - Espera a que Gradle sincronice

2. **Copiar el SDK** (si no se copió automáticamente):
   - Verifica que existe: `app/libs/cf-sdk-v1.0.0.aar`
   - Si no existe, copia manualmente desde `../aar/cf-sdk-v1.0.0.aar` a `app/libs/`

3. **Conectar tu dispositivo**:
   - Conecta tu celular por USB
   - Activa "Modo Desarrollador" en tu celular
   - Activa "Depuración USB"
   - Autoriza la computadora cuando aparezca el diálogo

4. **Instalar**:
   - En Android Studio, haz clic en el botón "Run" (▶️) o presiona Shift+F10
   - Selecciona tu dispositivo de la lista
   - La app se compilará e instalará automáticamente

## Opción 2: Usando línea de comandos

1. **Abrir PowerShell o CMD** en la carpeta del proyecto

2. **Copiar el SDK**:
   ```cmd
   mkdir app\libs
   copy ..\aar\cf-sdk-v1.0.0.aar app\libs\
   ```

3. **Verificar dispositivo**:
   ```cmd
   adb devices
   ```
   Debe mostrar tu dispositivo conectado

4. **Compilar e instalar**:
   ```cmd
   gradlew.bat assembleDebug
   adb install -r app\build\outputs\apk\debug\app-debug.apk
   ```

## Opción 3: Script automático

Ejecuta el archivo `instalar.bat` que está en la raíz del proyecto:
```cmd
instalar.bat
```

Este script:
- Copia el SDK automáticamente
- Verifica el dispositivo
- Compila la aplicación
- La instala en tu celular

## Solución de problemas

### "SDK no encontrado"
- Verifica que existe `../aar/cf-sdk-v1.0.0.aar`
- Copia manualmente a `app/libs/`

### "No device found"
- Verifica que el USB está conectado
- Activa "Depuración USB" en el celular
- Autoriza la computadora
- Ejecuta `adb devices` para verificar

### "Gradle sync failed"
- Abre Android Studio
- File > Sync Project with Gradle Files
- Si persiste, File > Invalidate Caches / Restart

### "Build failed"
- Verifica que tienes Android SDK instalado
- Verifica que el SDK RFID está en `app/libs/`
- Revisa los errores en la pestaña "Build" de Android Studio

## Verificación

Una vez instalada, deberías ver la app "doHealth Handheld" en tu celular con el ícono rojo.


