#!/bin/bash
echo "Copiando SDK RFID..."
mkdir -p app/libs
cp ../aar/cf-sdk-v1.0.0.aar app/libs/cf-sdk-v1.0.0.aar
if [ $? -eq 0 ]; then
    echo "SDK copiado exitosamente!"
else
    echo "Error al copiar el SDK. Verifica que el archivo existe en ../aar/cf-sdk-v1.0.0.aar"
fi


