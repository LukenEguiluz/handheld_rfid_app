# Script de instalación para doHealth Handheld
# Busca adb automáticamente y compila/instala la app

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Instalando doHealth Handheld" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Buscar adb en ubicaciones comunes
$adbPaths = @(
    "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    "$env:ProgramFiles\Android\Android Studio\platform-tools\adb.exe",
    "$env:ANDROID_HOME\platform-tools\adb.exe",
    "C:\Android\platform-tools\adb.exe"
)

$adb = $null
foreach ($path in $adbPaths) {
    if (Test-Path $path) {
        $adb = $path
        Write-Host "[OK] ADB encontrado en: $path" -ForegroundColor Green
        break
    }
}

if (-not $adb) {
    Write-Host "[ERROR] ADB no encontrado" -ForegroundColor Red
    Write-Host ""
    Write-Host "Opciones:" -ForegroundColor Yellow
    Write-Host "1. Instalar Android Studio (recomendado)"
    Write-Host "2. Agregar Android SDK Platform Tools al PATH"
    Write-Host "3. Usar Android Studio directamente para instalar"
    Write-Host ""
    Write-Host "Para usar Android Studio:" -ForegroundColor Cyan
    Write-Host "1. Abre Android Studio"
    Write-Host "2. File > Open > Selecciona la carpeta doHealth_Handheld"
    Write-Host "3. Conecta tu celular y haz clic en Run (▶️)"
    Write-Host ""
    Read-Host "Presiona Enter para salir"
    exit 1
}

# Verificar y copiar SDK
Write-Host "[1/4] Verificando SDK..." -ForegroundColor Yellow
if (-not (Test-Path "app\libs")) {
    New-Item -ItemType Directory -Path "app\libs" | Out-Null
}
if (-not (Test-Path "app\libs\cf-sdk-v1.0.0.aar")) {
    if (Test-Path "..\aar\cf-sdk-v1.0.0.aar") {
        Copy-Item "..\aar\cf-sdk-v1.0.0.aar" "app\libs\cf-sdk-v1.0.0.aar"
        Write-Host "[OK] SDK copiado" -ForegroundColor Green
    } else {
        Write-Host "[ERROR] SDK no encontrado en ..\aar\cf-sdk-v1.0.0.aar" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "[OK] SDK ya existe" -ForegroundColor Green
}
Write-Host ""

# Verificar dispositivo
Write-Host "[2/4] Verificando dispositivo Android..." -ForegroundColor Yellow
$devices = & $adb devices
Write-Host $devices
$deviceCount = ($devices | Select-String "device$" | Measure-Object).Count

if ($deviceCount -eq 0) {
    Write-Host "[ADVERTENCIA] No se encontraron dispositivos conectados" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Asegurate de:" -ForegroundColor Cyan
    Write-Host "- Conectar tu celular por USB"
    Write-Host "- Activar 'Modo Desarrollador'"
    Write-Host "- Activar 'Depuracion USB'"
    Write-Host "- Autorizar esta computadora"
    Write-Host ""
    $continue = Read-Host "¿Deseas continuar de todas formas? (S/N)"
    if ($continue -ne "S" -and $continue -ne "s") {
        exit 1
    }
}
Write-Host ""

# Compilar
Write-Host "[3/4] Compilando aplicacion..." -ForegroundColor Yellow
if (Test-Path "gradlew.bat") {
    & .\gradlew.bat assembleDebug
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERROR] Error al compilar" -ForegroundColor Red
        Write-Host "Por favor, abre el proyecto en Android Studio y compila desde alli" -ForegroundColor Yellow
        exit 1
    }
    Write-Host "[OK] Compilacion exitosa" -ForegroundColor Green
} else {
    Write-Host "[ERROR] gradlew.bat no encontrado" -ForegroundColor Red
    Write-Host "Por favor, abre el proyecto en Android Studio" -ForegroundColor Yellow
    exit 1
}
Write-Host ""

# Instalar
Write-Host "[4/4] Instalando en dispositivo..." -ForegroundColor Yellow
$apkPath = "app\build\outputs\apk\debug\app-debug.apk"
if (Test-Path $apkPath) {
    & $adb install -r $apkPath
    if ($LASTEXITCODE -eq 0) {
        Write-Host ""
        Write-Host "========================================" -ForegroundColor Green
        Write-Host "Instalacion completada exitosamente!" -ForegroundColor Green
        Write-Host "========================================" -ForegroundColor Green
        Write-Host ""
        Write-Host "La aplicacion 'doHealth Handheld' ha sido instalada." -ForegroundColor Cyan
    } else {
        Write-Host "[ERROR] Error al instalar" -ForegroundColor Red
    }
} else {
    Write-Host "[ERROR] APK no encontrado en: $apkPath" -ForegroundColor Red
}

Write-Host ""
Read-Host "Presiona Enter para salir"


