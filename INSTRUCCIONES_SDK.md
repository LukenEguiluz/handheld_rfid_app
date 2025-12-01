# ⚠️ INSTRUCCIONES CRÍTICAS: Configuración del SDK

## Problema Actual

El SDK no se está cargando correctamente. Los errores "Unresolved reference: cf" indican que Gradle no encuentra las clases del SDK.

## Solución Paso a Paso

### 1. Verificar que el SDK esté copiado

**Ejecuta este comando en PowerShell:**
```powershell
cd "C:\Users\Luken\Documents\doHealth\H100.102.103.104 SDK\doHealth_Handheld"
if (-not (Test-Path "app\libs")) { New-Item -ItemType Directory -Path "app\libs" -Force }
if (Test-Path "..\aar\cf-sdk-v1.0.0.aar") {
    Copy-Item "..\aar\cf-sdk-v1.0.0.aar" "app\libs\" -Force
    Write-Host "[OK] SDK copiado" -ForegroundColor Green
} else {
    Write-Host "[ERROR] SDK no encontrado en ..\aar\" -ForegroundColor Red
}
```

### 2. Verificar el archivo

```powershell
dir app\libs\cf-sdk-v1.0.0.aar
```

**Debe mostrar:**
- Un archivo con tamaño > 100 KB (no 0 bytes)
- Última modificación reciente

### 3. En Android Studio

**PASO CRÍTICO: Sincronizar Gradle**

1. **File → Sync Project with Gradle Files**
   - Espera a que termine completamente
   - Si hay errores, continúa con el siguiente paso

2. **Build → Clean Project**
   - Espera a que termine

3. **Build → Rebuild Project**
   - Esto puede tardar varios minutos
   - Observa si aparecen errores relacionados con el SDK

4. **Si persisten errores:**
   - **File → Invalidate Caches / Restart**
   - Selecciona **Invalidate and Restart**
   - Espera a que Android Studio reinicie
   - Sincroniza Gradle de nuevo

### 4. Verificar configuración

**`settings.gradle` debe tener:**
```gradle
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        flatDir {
            dirs 'app/libs'
        }
    }
}
```

**`app/build.gradle` debe tener:**
```gradle
dependencies {
    // ...
    implementation files('libs/cf-sdk-v1.0.0.aar')
}
```

### 5. Si el problema persiste

**Opción A: Verificar que el AAR no esté corrupto**

1. Intenta abrir el AAR como ZIP (cambia la extensión a .zip)
2. Debe contener:
   - `classes.jar`
   - `AndroidManifest.xml`
   - `R.txt`
   - `res/` (carpeta)

**Opción B: Re-copiar el SDK**

```powershell
cd "C:\Users\Luken\Documents\doHealth\H100.102.103.104 SDK\doHealth_Handheld"
Remove-Item "app\libs\cf-sdk-v1.0.0.aar" -ErrorAction SilentlyContinue
Copy-Item "..\aar\cf-sdk-v1.0.0.aar" "app\libs\" -Force
```

**Opción C: Limpiar caché de Gradle**

```powershell
cd "C:\Users\Luken\Documents\doHealth\H100.102.103.104 SDK\doHealth_Handheld"
Remove-Item -Recurse -Force ".gradle" -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force "app\build" -ErrorAction SilentlyContinue
```

Luego en Android Studio:
- File → Sync Project with Gradle Files
- Build → Rebuild Project

### 6. Verificar en el log de Gradle

Si el problema persiste, revisa el log de Gradle:
- **View → Tool Windows → Build**
- Busca mensajes relacionados con "cf-sdk" o "aar"
- Verifica si hay errores de resolución de dependencias

## Estado Actual de la Configuración

✅ `settings.gradle`: Configurado con `PREFER_SETTINGS` y `flatDir`  
✅ `app/build.gradle`: Usa `implementation files('libs/cf-sdk-v1.0.0.aar')`  
⚠️ **Pendiente**: Verificar que el AAR esté en `app/libs/`  
⚠️ **Pendiente**: Sincronizar Gradle en Android Studio  

## Próximos Pasos

1. **Ejecuta el script de verificación** (paso 1)
2. **Sincroniza Gradle** en Android Studio
3. **Intenta compilar** de nuevo
4. **Si falla**, sigue con las opciones A, B o C


