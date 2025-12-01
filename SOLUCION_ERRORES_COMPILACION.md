# Solución a Errores de Compilación

## Problema: "Unresolved reference: cf"

Este error indica que el SDK no se está cargando correctamente.

### Solución 1: Verificar que el SDK esté copiado

1. **Verifica que el archivo existe**:
   ```powershell
   cd "C:\Users\Luken\Documents\doHealth\H100.102.103.104 SDK\doHealth_Handheld"
   dir app\libs\cf-sdk-v1.0.0.aar
   ```

2. **Si no existe, cópialo**:
   ```powershell
   if (-not (Test-Path "app\libs")) { New-Item -ItemType Directory -Path "app\libs" -Force }
   Copy-Item "..\aar\cf-sdk-v1.0.0.aar" "app\libs\" -Force
   ```

### Solución 2: Sincronizar Gradle en Android Studio

1. **File → Sync Project with Gradle Files**
2. Espera a que termine la sincronización
3. Si hay errores, ve a **Build → Clean Project**
4. Luego **Build → Rebuild Project**

### Solución 3: Invalidar caché

1. **File → Invalidate Caches / Restart**
2. Selecciona **Invalidate and Restart**
3. Espera a que Android Studio reinicie

### Solución 4: Verificar configuración de Gradle

El archivo `settings.gradle` debe tener:
```gradle
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        flatDir {
            dirs 'app/libs'
        }
    }
}
```

Y `app/build.gradle` debe tener:
```gradle
dependencies {
    // ...
    implementation fileTree(dir: 'libs', include: ['*.aar'])
}
```

### Solución 5: Verificar que el AAR no esté corrupto

1. Verifica el tamaño del archivo (debe ser > 100 KB)
2. Intenta copiarlo de nuevo desde el original
3. Verifica que el archivo original no esté dañado

### Solución 6: Si nada funciona

1. Elimina las carpetas `.gradle` y `build` en el proyecto
2. En Android Studio: **File → Invalidate Caches / Restart**
3. Sincroniza Gradle de nuevo
4. Compila de nuevo

## Error en ExcelExporter

El error de `setCellValue` ya está corregido. Si persiste, verifica que el código tenga:

```kotlin
val rssiCell = row.createCell(3)
if (item.rssi != 0) {
    rssiCell.setCellValue(item.rssi.toDouble())
} else {
    rssiCell.setCellValue("")
}
```

## Pasos Finales

Después de aplicar las soluciones:

1. **Sincroniza Gradle**: File → Sync Project with Gradle Files
2. **Limpia el proyecto**: Build → Clean Project
3. **Reconstruye**: Build → Rebuild Project
4. **Compila**: Build → Make Project

Si el problema persiste, verifica que:
- El SDK esté en `app/libs/cf-sdk-v1.0.0.aar`
- El archivo tenga tamaño > 0 bytes
- No haya errores de sintaxis en los archivos Kotlin


