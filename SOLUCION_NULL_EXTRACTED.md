# Solución para Error "Null extracted folder"

## Error
```
Null extracted folder for artifact: ResolvedArtifact(...)
```

Este error indica que Gradle no puede extraer o procesar el AAR correctamente.

## Soluciones

### Solución 1: Verificar que el AAR esté correcto

1. **Verifica que el AAR existe y tiene tamaño**:
   ```powershell
   cd "C:\Users\Luken\Documents\doHealth\H100.102.103.104 SDK\doHealth_Handheld"
   dir app\libs\cf-sdk-v1.0.0.aar
   ```
   Debe mostrar un archivo de varios KB (no 0 bytes).

2. **Verifica la estructura del AAR** (debe ser un ZIP válido):
   - Debe contener `classes.jar`
   - Debe contener `AndroidManifest.xml`
   - Puede contener `R.txt`

### Solución 2: Re-copiar el AAR

Si el AAR está corrupto:

```powershell
cd "C:\Users\Luken\Documents\doHealth\H100.102.103.104 SDK\doHealth_Handheld"
Remove-Item "app\libs\cf-sdk-v1.0.0.aar" -ErrorAction SilentlyContinue
Copy-Item "..\aar\cf-sdk-v1.0.0.aar" "app\libs\" -Force
```

### Solución 3: Limpiar caché de Gradle

```powershell
cd "C:\Users\Luken\Documents\doHealth\H100.102.103.104 SDK\doHealth_Handheld"
Remove-Item -Recurse -Force ".gradle" -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force "app\build" -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force ".idea" -ErrorAction SilentlyContinue
```

Luego en Android Studio:
- **File → Invalidate Caches / Restart → Invalidate and Restart**
- **File → Sync Project with Gradle Files**

### Solución 4: Configuración actual

**`settings.gradle`:**
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

**`app/build.gradle`:**
```gradle
dependencies {
    // ...
    implementation(name: 'cf-sdk-v1.0.0', ext: 'aar') {
        transitive = false
    }
}
```

### Solución 5: Si nada funciona - Usar módulo local

Si el AAR sigue sin funcionar, puedes crear un módulo local del SDK:

1. Extrae el AAR (cambia la extensión a .zip y descomprime)
2. Crea un módulo nuevo en Android Studio
3. Copia el contenido del AAR al módulo
4. Usa `implementation project(':cf-sdk')` en lugar del AAR

### Solución 6: Verificar permisos

Asegúrate de que:
- El archivo AAR no esté bloqueado por otro proceso
- Tengas permisos de lectura/escritura en la carpeta `app/libs`
- El archivo no esté marcado como "solo lectura"

### Pasos Recomendados

1. **Verifica el AAR** (Solución 1)
2. **Re-copia el AAR** (Solución 2)
3. **Limpia caché** (Solución 3)
4. **Sincroniza Gradle** en Android Studio
5. **Intenta compilar** de nuevo

Si el problema persiste después de estos pasos, el AAR podría estar corrupto o incompatible con la versión de Gradle/Android que estás usando.


