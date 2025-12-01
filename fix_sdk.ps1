# Script para corregir problemas con el SDK
Write-Host "=== Corrección del SDK RFID ===" -ForegroundColor Cyan

$projectPath = Split-Path -Parent $MyInvocation.MyCommand.Path
$libsPath = Join-Path $projectPath "app\libs"
$aarPath = Join-Path $libsPath "cf-sdk-v1.0.0.aar"
$sourceAar = Join-Path (Split-Path -Parent $projectPath) "aar\cf-sdk-v1.0.0.aar"

# 1. Crear carpeta libs si no existe
Write-Host "`n[1/5] Verificando carpeta libs..." -ForegroundColor Yellow
if (-not (Test-Path $libsPath)) {
    New-Item -ItemType Directory -Path $libsPath -Force | Out-Null
    Write-Host "  [OK] Carpeta creada" -ForegroundColor Green
} else {
    Write-Host "  [OK] Carpeta existe" -ForegroundColor Green
}

# 2. Verificar AAR fuente
Write-Host "`n[2/5] Verificando AAR fuente..." -ForegroundColor Yellow
if (Test-Path $sourceAar) {
    $sourceFile = Get-Item $sourceAar
    Write-Host "  [OK] AAR fuente encontrado: $([math]::Round($sourceFile.Length/1KB,2)) KB" -ForegroundColor Green
} else {
    Write-Host "  [ERROR] AAR fuente NO encontrado en: $sourceAar" -ForegroundColor Red
    exit 1
}

# 3. Verificar/copiar AAR
Write-Host "`n[3/5] Verificando AAR en app/libs..." -ForegroundColor Yellow
if (Test-Path $aarPath) {
    $existingFile = Get-Item $aarPath
    $sourceFile = Get-Item $sourceAar
    
    if ($existingFile.Length -eq $sourceFile.Length -and $existingFile.LastWriteTime -eq $sourceFile.LastWriteTime) {
        Write-Host "  [OK] AAR ya está actualizado" -ForegroundColor Green
    } else {
        Write-Host "  [INFO] AAR desactualizado, copiando..." -ForegroundColor Yellow
        Copy-Item $sourceAar $aarPath -Force
        Write-Host "  [OK] AAR copiado" -ForegroundColor Green
    }
} else {
    Write-Host "  [INFO] AAR no existe, copiando..." -ForegroundColor Yellow
    Copy-Item $sourceAar $aarPath -Force
    Write-Host "  [OK] AAR copiado" -ForegroundColor Green
}

# 4. Verificar estructura del AAR
Write-Host "`n[4/5] Verificando estructura del AAR..." -ForegroundColor Yellow
try {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($aarPath)
    
    $hasClasses = $zip.Entries | Where-Object { $_.FullName -eq "classes.jar" }
    $hasManifest = $zip.Entries | Where-Object { $_.FullName -eq "AndroidManifest.xml" }
    
    if ($hasClasses) {
        Write-Host "  [OK] Contiene classes.jar" -ForegroundColor Green
    } else {
        Write-Host "  [ERROR] NO contiene classes.jar - AAR corrupto!" -ForegroundColor Red
        $zip.Dispose()
        exit 1
    }
    
    if ($hasManifest) {
        Write-Host "  [OK] Contiene AndroidManifest.xml" -ForegroundColor Green
    } else {
        Write-Host "  [WARN] NO contiene AndroidManifest.xml" -ForegroundColor Yellow
    }
    
    Write-Host "  [INFO] Total entradas: $($zip.Entries.Count)" -ForegroundColor Cyan
    $zip.Dispose()
} catch {
    Write-Host "  [ERROR] No se pudo leer como ZIP: $_" -ForegroundColor Red
    exit 1
}

# 5. Limpiar caché de Gradle (opcional)
Write-Host "`n[5/5] Limpieza de caché (opcional)..." -ForegroundColor Yellow
$gradleCache = Join-Path $projectPath ".gradle"
$buildCache = Join-Path $projectPath "app\build"

$cleanCache = Read-Host "¿Limpiar caché de Gradle? (S/N)"
if ($cleanCache -eq "S" -or $cleanCache -eq "s") {
    if (Test-Path $gradleCache) {
        Remove-Item -Recurse -Force $gradleCache -ErrorAction SilentlyContinue
        Write-Host "  [OK] Caché de Gradle eliminado" -ForegroundColor Green
    }
    if (Test-Path $buildCache) {
        Remove-Item -Recurse -Force $buildCache -ErrorAction SilentlyContinue
        Write-Host "  [OK] Carpeta build eliminada" -ForegroundColor Green
    }
} else {
    Write-Host "  [INFO] Caché no limpiado" -ForegroundColor Yellow
}

Write-Host "`n=== Verificación completada ===" -ForegroundColor Cyan
Write-Host "`nPróximos pasos en Android Studio:" -ForegroundColor Yellow
Write-Host "1. File → Sync Project with Gradle Files" -ForegroundColor White
Write-Host "2. Build → Clean Project" -ForegroundColor White
Write-Host "3. Build → Rebuild Project" -ForegroundColor White
Write-Host "`nSi el problema persiste:" -ForegroundColor Yellow
Write-Host "- File → Invalidate Caches / Restart → Invalidate and Restart" -ForegroundColor White


