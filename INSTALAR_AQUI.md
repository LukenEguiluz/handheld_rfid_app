# 🚀 Instalación Rápida - doHealth Handheld

## ⚡ Método Más Fácil: Android Studio

**Si no tienes `adb` en el PATH, usa Android Studio directamente:**

### Pasos:

1. **Abre Android Studio**
   - Si no lo tienes, descárgalo de: https://developer.android.com/studio

2. **Abre el proyecto**
   - File → Open
   - Navega a: `C:\Users\Luken\Documents\doHealth\H100.102.103.104 SDK\doHealth_Handheld`
   - Haz clic en "OK"
   - Espera a que Gradle sincronice (primera vez puede tardar 5-10 minutos)

3. **Copia el SDK** (si no está automáticamente)
   - Verifica que existe: `app/libs/cf-sdk-v1.0.0.aar`
   - Si NO existe:
     - Copia manualmente desde: `..\aar\cf-sdk-v1.0.0.aar`
     - Pega en: `app\libs\cf-sdk-v1.0.0.aar`

4. **Conecta tu celular**
   - Conecta por USB
   - En tu celular: **Configuración → Acerca del teléfono**
   - Toca 7 veces en "Número de compilación" (activa Modo Desarrollador)
   - Ve a: **Configuración → Opciones de desarrollador**
   - Activa "Depuración USB"
   - Conecta el USB y autoriza cuando aparezca el diálogo

5. **Instala la app**
   - En Android Studio, haz clic en el botón **Run** (▶️ verde) o presiona `Shift+F10`
   - Selecciona tu dispositivo de la lista
   - ¡Listo! La app se compilará e instalará automáticamente

---

## 🔧 Método Alternativo: Script PowerShell

Si tienes Android SDK instalado pero `adb` no está en el PATH:

1. **Abre PowerShell** en la carpeta del proyecto:
   ```powershell
   cd "C:\Users\Luken\Documents\doHealth\H100.102.103.104 SDK\doHealth_Handheld"
   ```

2. **Ejecuta el script**:
   ```powershell
   .\instalar_con_adb.ps1
   ```

   El script buscará `adb` automáticamente y te guiará.

---

## 📱 Verificación

Una vez instalada, busca en tu celular la app **"doHealth Handheld"** con ícono rojo.

---

## ❓ Problemas Comunes

### "Gradle sync failed"
- Espera a que termine la descarga de dependencias
- File → Invalidate Caches / Restart

### "SDK not found"
- Copia manualmente: `..\aar\cf-sdk-v1.0.0.aar` → `app\libs\`

### "No device found"
- Verifica que el USB está conectado
- Activa "Depuración USB" en el celular
- Autoriza la computadora en el diálogo del celular

### "Build failed"
- Verifica que tienes Android SDK instalado
- Verifica que el SDK RFID está en `app/libs/`

---

## 💡 Recomendación

**Usa Android Studio** - Es la forma más fácil y confiable. El IDE se encarga de todo automáticamente.


