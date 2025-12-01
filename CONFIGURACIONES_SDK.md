# Configuraciones Disponibles en el SDK RFID

Según el análisis del SDK, estas son todas las configuraciones que se pueden modificar:

## 📋 Parámetros Configurables (AllParamBean)

### 1. **Potencia RF (mRfidPower)**
- **Rango**: 0-33 dBm
- **Valor por defecto**: Variable según dispositivo
- **Comando**: `CmdBuilder.buildSetPwrCmd(power, 0x00)`
- **Descripción**: Controla la potencia de salida del transmisor RFID
- **Uso**: Mayor potencia = mayor rango de lectura, pero más consumo de batería

### 2. **Valor Q (mQValue)**
- **Rango**: 0-15
- **Valor por defecto**: 4
- **Comando**: `CmdBuilder.buildSetAllParamCmd(AllParamBean)`
- **Descripción**: Valor inicial Q para el algoritmo de anti-colisión
- **Uso**: Q ≈ log2(cantidad de tags). Q=4 para ~16 tags, Q=7 para ~128 tags

### 3. **Sesión (mSession)**
- **Rango**: 0-3
- **Valor por defecto**: 0
- **Comando**: `CmdBuilder.buildSetAllParamCmd(AllParamBean)`
- **Descripción**: Sesión RFID a usar para el inventario
- **Valores**:
  - 0: Session S0
  - 1: Session S1
  - 2: Session S2
  - 3: Session S3

### 4. **Antena (mAnt)**
- **Rango**: Bitmask (0x01, 0x02, 0x04, 0x08, etc.)
- **Valor por defecto**: 0x01 (Antena 1)
- **Comando**: `CmdBuilder.buildSetAllParamCmd(AllParamBean)`
- **Descripción**: Selección de antenas activas (bitmask)
- **Uso**: 
  - 0x01 = Antena 1
  - 0x02 = Antena 2
  - 0x03 = Antenas 1 y 2
  - 0x04 = Antena 3
  - etc.

### 5. **Área de Consulta (mInquiryArea)**
- **Rango**: 0x00-0x06
- **Valor por defecto**: 0x01 (EPC)
- **Comando**: `CmdBuilder.buildSetAllParamCmd(AllParamBean)`
- **Descripción**: Área de memoria del tag a consultar
- **Valores**:
  - 0x00: Reservado
  - 0x01: EPC (por defecto)
  - 0x02: TID
  - 0x03: USER
  - 0x04: EPC+TID
  - 0x05: EPC+USER
  - 0x06: EPC+TID+USER

### 6. **Frecuencia RFID (mRfidFreq)**
- **Tipo**: Objeto RfidFreq
- **Comando**: `CmdBuilder.buildSetAllParamCmd(AllParamBean)`
- **Descripción**: Configuración de frecuencia y región
- **Parámetros**:
  - **mREGION**: Región de frecuencia
    - 0x00: Personalizado
    - 0x01: US [902.75~927.25 MHz]
    - 0x02: Korea [917.1~923.5 MHz]
    - 0x03: EU [865.1~868.1 MHz]
    - 0x04: JAPAN [952.2~953.6 MHz]
    - 0x05: MALAYSIA [919.5~922.5 MHz]
    - 0x06: EU3 [865.7~867.5 MHz]
    - 0x07: CHINA_BAND1 [840.125~844.875 MHz]
    - 0x08: CHINA_BAND2 [920.125~924.875 MHz]
  - **mSTRATFREI**: Frecuencia inicial (parte entera)
  - **mSTRATFRED**: Frecuencia inicial (parte decimal)
  - **mSTEPFRE**: Paso de frecuencia (KHz)
  - **mCN**: Número de canales

### 7. **Tiempo de Filtro (mFilterTime)**
- **Rango**: 0-255 segundos
- **Valor por defecto**: 0 (sin filtro)
- **Comando**: `CmdBuilder.buildSetAllParamCmd(AllParamBean)`
- **Descripción**: Tiempo durante el cual se filtran tags duplicados después de una lectura exitosa

### 8. **Tiempo de Trigger (mTriggerTime)**
- **Rango**: 0-255 segundos
- **Valor por defecto**: 1 segundo
- **Comando**: `CmdBuilder.buildSetAllParamCmd(AllParamBean)`
- **Descripción**: Duración del inventario después de recibir señal de trigger (modo trigger)

### 9. **Tiempo de Buzzer (mBuzzerTime)**
- **Rango**: 0-255 (unidades de 10ms)
- **Valor por defecto**: 1 (10ms)
- **Comando**: `CmdBuilder.buildSetAllParamCmd(AllParamBean)`
- **Descripción**: Duración del sonido del buzzer al completar operación
- **Nota**: 0 = sin sonido

### 10. **Intervalo de Polling (mPollingInterval)**
- **Rango**: 0-255 (unidades de 10ms)
- **Valor por defecto**: 1 (10ms)
- **Comando**: `CmdBuilder.buildSetAllParamCmd(AllParamBean)`
- **Descripción**: Intervalo entre consultas de inventario

### 11. **Dirección de Acceso (mAcsAddr)**
- **Rango**: 0-255 bytes
- **Valor por defecto**: 0x00
- **Comando**: `CmdBuilder.buildSetAllParamCmd(AllParamBean)`
- **Descripción**: Dirección inicial de memoria del tag para lectura/escritura

### 12. **Longitud de Datos (mAcsDataLen)**
- **Rango**: 0-255 bytes
- **Valor por defecto**: 0x00
- **Comando**: `CmdBuilder.buildSetAllParamCmd(AllParamBean)`
- **Descripción**: Cantidad de bytes a leer/escribir en el tag

### 13. **Modo de Trabajo (mWorkMode)**
- **Rango**: 0-2
- **Valor por defecto**: 0
- **Comando**: `CmdBuilder.buildSetAllParamCmd(AllParamBean)`
- **Descripción**: Modo de operación del dispositivo
- **Valores**:
  - 0: Modo Respuesta
  - 1: Modo Activo
  - 2: Modo Trigger

### 14. **Dirección del Dispositivo (mAddr)**
- **Rango**: 0x00-0xFE (no puede ser 0xFF)
- **Valor por defecto**: 0x00
- **Comando**: `CmdBuilder.buildSetAllParamCmd(AllParamBean)`
- **Descripción**: Dirección de comunicación del dispositivo

### 15. **Protocolo RFID (mRFIDPRO)**
- **Rango**: 0x00-0x02
- **Valor por defecto**: 0x00 (ISO 18000-6C)
- **Comando**: `CmdBuilder.buildSetAllParamCmd(AllParamBean)`
- **Descripción**: Estándar de protocolo RFID
- **Valores**:
  - 0x00: ISO 18000-6C
  - 0x01: GB/T 29768
  - 0x02: GJB 7377.1

## 🔧 Comandos de Configuración Disponibles

### Comandos Individuales
1. **`buildSetPwrCmd(byte power, byte resv)`**
   - Configura solo la potencia RF
   - Más rápido que configurar todos los parámetros

2. **`buildSetAllParamCmd(AllParamBean bean)`**
   - Configura TODOS los parámetros de una vez
   - Requiere crear un objeto AllParamBean completo

3. **`buildGetAllParamCmd()`**
   - Obtiene todos los parámetros actuales del dispositivo
   - Retorna un AllParamBean con la configuración actual

### Comandos Adicionales
4. **`buildSetReadModeCmd(byte mode, byte[] recev)`**
   - Cambia entre modo RFID (0x00) y modo código de barras (0x01)

5. **`buildGetReadModeCmd()`**
   - Obtiene el modo actual (RFID o código de barras)

6. **`buildSetOrGetBtNameCmd(byte option, String btName)`**
   - Configura el nombre Bluetooth del dispositivo

7. **`buildGetBatteryCapacityCmd()`**
   - Obtiene el nivel de batería del dispositivo

8. **`buildGetDeviceInfoCmd()`**
   - Obtiene información del dispositivo (versión, modelo, etc.)

## 📝 Configuraciones Actualmente Implementadas

En la app `doHealth_Handheld`, actualmente se pueden configurar:

✅ **Potencia RF** (mRfidPower) - 0-26 dBm
✅ **Valor Q** (mQValue) - 0-15
✅ **Sesión** (mSession) - 0-3
✅ **Antena** (mAnt) - 1-4

## 🔄 Configuraciones No Implementadas (pero disponibles)

❌ **Área de Consulta** (mInquiryArea)
❌ **Frecuencia RFID** (mRfidFreq) - Región y canales
❌ **Tiempo de Filtro** (mFilterTime)
❌ **Tiempo de Trigger** (mTriggerTime)
❌ **Tiempo de Buzzer** (mBuzzerTime)
❌ **Intervalo de Polling** (mPollingInterval)
❌ **Modo de Trabajo** (mWorkMode)
❌ **Dirección de Acceso** (mAcsAddr)
❌ **Longitud de Datos** (mAcsDataLen)

## 💡 Recomendaciones

### Para Inventario General:
- **Potencia**: 20-26 dBm (mayor alcance)
- **Q Value**: 4-7 (depende de cantidad de tags)
- **Sesión**: 0 (S0, más común)
- **Antena**: 1 (o todas si hay múltiples)

### Para Lectura Precisa:
- **Potencia**: 15-20 dBm (menos interferencia)
- **Q Value**: 4-5 (menos tags)
- **Filtro**: 1-2 segundos (evitar duplicados)

### Para Lectura Rápida:
- **Potencia**: 26 dBm (máximo)
- **Q Value**: 7-10 (muchos tags)
- **Polling Interval**: 0 (sin delay)

