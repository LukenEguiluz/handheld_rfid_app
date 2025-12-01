@echo off
echo ========================================
echo   COPIANDO SDK RFID
echo ========================================
echo.

cd /d "%~dp0"

REM Crear carpeta libs si no existe
if not exist "app\libs" (
    echo [1/2] Creando carpeta app\libs...
    mkdir "app\libs"
    echo [OK] Carpeta creada
) else (
    echo [1/2] Carpeta app\libs ya existe
)

REM Copiar SDK
echo.
echo [2/2] Copiando SDK desde ..\aar\...
if exist "..\aar\cf-sdk-v1.0.0.aar" (
    copy /Y "..\aar\cf-sdk-v1.0.0.aar" "app\libs\cf-sdk-v1.0.0.aar"
    if exist "app\libs\cf-sdk-v1.0.0.aar" (
        echo [OK] SDK copiado exitosamente!
        echo.
        echo Verificando...
        dir "app\libs\cf-sdk-v1.0.0.aar"
        echo.
        echo ========================================
        echo   SDK LISTO PARA USAR
        echo ========================================
    ) else (
        echo [ERROR] No se pudo copiar el SDK
    )
) else (
    echo [ERROR] SDK no encontrado en ..\aar\cf-sdk-v1.0.0.aar
    echo.
    echo Buscando SDK en otras ubicaciones...
    dir /s /b "..\*.aar" 2>nul
)

echo.
pause


