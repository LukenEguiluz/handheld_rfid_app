@echo off
echo Copiando SDK RFID...
if not exist "app\libs" mkdir app\libs
copy "..\aar\cf-sdk-v1.0.0.aar" "app\libs\cf-sdk-v1.0.0.aar"
if %errorlevel% == 0 (
    echo SDK copiado exitosamente!
) else (
    echo Error al copiar el SDK. Verifica que el archivo existe en ..\aar\cf-sdk-v1.0.0.aar
)
pause


