# 📝 Nota sobre Modo Trigger

## Comportamiento Actual del SDK

Según el SDK analizado:

### Modo Trigger (mWorkMode = 2)
- El SDK tiene un modo trigger que funciona de forma **toggle**:
  - Primera presión del gatillo → Inicia inventario
  - Segunda presión del gatillo → Detiene inventario

### Detección de Estado del Gatillo

El SDK puede detectar el estado del gatillo a través de `KeyStateBean`:
- `TYPE_KEY_STATE` (0x0089): Reporta el estado del botón/gatillo
- `mKeyState = 0x01`: Presionado (inicio)
- `mKeyState = 0x02`: Soltado (fin)

## Posible Implementación de Trigger "Hold" (Presionar y Mantener)

**IMPORTANTE**: El comportamiento nativo del modo trigger (mWorkMode = 2) del dispositivo funciona como toggle automático. Sin embargo, es posible implementar un comportamiento personalizado en la app:

### Opción 1: Implementación Manual en la App
1. No usar el modo trigger nativo (mWorkMode = 0 o 1)
2. Escuchar eventos `TYPE_KEY_STATE` en el callback `onNotify()`
3. Cuando `mKeyState == 0x01` (presionado): Iniciar inventario manualmente con `buildInventoryISOContinueCmd()`
4. Cuando `mKeyState == 0x02` (soltado): Detener inventario con `buildStopInventoryCmd()`

### Opción 2: Usar mTriggerTime
- Configurar `mTriggerTime = 0` podría cambiar el comportamiento
- **No documentado**: Necesita pruebas empíricas

## Recomendación

Para implementar el comportamiento "presionar para activar, soltar para desactivar":

```kotlin
override fun onNotify(cmdType: Int, cmdData: CmdData) {
    when (cmdType) {
        CmdType.TYPE_KEY_STATE -> {
            val keyState = cmdData.getData() as? KeyStateBean ?: return
            when (keyState.mKeyState.toInt()) {
                0x01 -> { // Presionado - Iniciar inventario
                    val inventoryCmd = CmdBuilder.buildInventoryISOContinueCmd(0x00, 0)
                    bleCore.writeData(serviceUuid, writeUuid, inventoryCmd)
                }
                0x02 -> { // Soltado - Detener inventario
                    val stopCmd = CmdBuilder.buildStopInventoryCmd()
                    bleCore.writeData(serviceUuid, writeUuid, stopCmd)
                }
            }
        }
    }
}
```

**Nota**: Esta implementación requiere que la app esté en modo Respuesta (mWorkMode = 0) o Activo (mWorkMode = 1), NO en modo Trigger (mWorkMode = 2).

