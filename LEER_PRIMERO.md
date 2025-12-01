# ⚠️ IMPORTANTE: Copiar SDK antes de compilar

## El SDK debe estar en: `app/libs/cf-sdk-v1.0.0.aar`

### Opción 1: Ejecutar el script (RECOMENDADO)

**Doble clic en:** `copiar_sdk.bat`

O desde PowerShell:
```powershell
cd "C:\Users\Luken\Documents\doHealth\H100.102.103.104 SDK\doHealth_Handheld"
.\copiar_sdk.bat
```

### Opción 2: Manualmente

1. **Abre el Explorador de Archivos**
2. **Ve a:** `C:\Users\Luken\Documents\doHealth\H100.102.103.104 SDK\aar\`
3. **Copia el archivo:** `cf-sdk-v1.0.0.aar`
4. **Ve a:** `C:\Users\Luken\Documents\doHealth\H100.102.103.104 SDK\doHealth_Handheld\app\`
5. **Crea la carpeta** `libs` (si no existe)
6. **Pega el archivo** `cf-sdk-v1.0.0.aar` dentro de `libs`

### Verificación

Después de copiar, verifica que el archivo existe:

**Ruta completa debe ser:**
```
C:\Users\Luken\Documents\doHealth\H100.102.103.104 SDK\doHealth_Handheld\app\libs\cf-sdk-v1.0.0.aar
```

**En Android Studio:**
- Ve a la vista de **Project** (no Android)
- Expande: `app` → `libs`
- Debe aparecer: `cf-sdk-v1.0.0.aar`

### Si el archivo no aparece en Android Studio

1. **Clic derecho en la carpeta `app`**
2. **Sincronizar** o **Refresh**
3. O **File → Sync Project with Gradle Files**

### Después de copiar el SDK

1. **File → Sync Project with Gradle Files**
2. Espera a que termine
3. **Build → Clean Project**
4. **Build → Rebuild Project**

---

## Estructura correcta:

```
doHealth_Handheld/
├── app/
│   ├── libs/              ← Esta carpeta DEBE existir
│   │   └── cf-sdk-v1.0.0.aar  ← Este archivo DEBE estar aquí
│   ├── build.gradle
│   └── src/
├── build.gradle
└── settings.gradle
```

**Si la carpeta `libs` no existe, créala.**
**Si el archivo `.aar` no está, cópialo desde `../aar/cf-sdk-v1.0.0.aar`**


