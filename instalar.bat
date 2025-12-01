@echo off
echo ========================================
echo Instalando doHealth Handheld
echo ========================================
echo.

REM Verificar y copiar SDK
echo [1/4] Verificando SDK...
if not exist "app\libs" (
    echo Creando carpeta libs...
    mkdir app\libs
)
if not exist "app\libs\cf-sdk-v1.0.0.aar" (
    echo Copiando SDK desde ..\aar\...
    copy "..\aar\cf-sdk-v1.0.0.aar" "app\libs\cf-sdk-v1.0.0.aar"
    if errorlevel 1 (
        echo ERROR: No se pudo copiar el SDK
        echo Asegurese de que existe: ..\aar\cf-sdk-v1.0.0.aar
        pause
        exit /b 1
    )
    echo SDK copiado exitosamente
) else (
    echo SDK ya existe
)
echo.

REM Verificar dispositivo conectado
echo [2/4] Verificando dispositivo Android...
adb devices
echo.
echo Si no aparece ningun dispositivo, conecte su telefono y active:
echo - Modo desarrollador
echo - Depuracion USB
echo - Autorizar esta computadora
echo.
pause
echo.

REM Compilar APK
echo [3/4] Compilando aplicacion...
if exist "gradlew.bat" (
    call gradlew.bat assembleDebug
    if errorlevel 1 (
        echo ERROR al compilar
        pause
        exit /b 1
    )
) else (
    echo ERROR: gradlew.bat no encontrado
    echo Por favor, abra el proyecto en Android Studio y compile desde alli
    pause
    exit /b 1
)
echo.

REM Instalar APK
echo [4/4] Instalando en dispositivo...
adb install -r app\build\outputs\apk\debug\app-debug.apk
if errorlevel 1 (
    echo ERROR al instalar
    echo Verifique que:
    echo - El dispositivo este conectado
    echo - La depuracion USB este activada
    echo - Haya autorizado esta computadora
) else (
    echo.
    echo ========================================
    echo Instalacion completada exitosamente!
    echo ========================================
    echo.
    echo La aplicacion "doHealth Handheld" ha sido instalada en su dispositivo.
    echo.
)
pause


