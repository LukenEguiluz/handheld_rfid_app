package com.dohealth.handheld.utils

import android.content.Context
import android.widget.Toast
import com.cf.zsdk.BleCore
import com.cf.zsdk.cmd.CmdBuilder
import com.dohealth.handheld.R
import java.util.UUID

object RfidDistancePresetHelper {

    /** Envía al lector el preset guardado en prefs, si hay uno reconocido. */
    fun syncSavedPresetToDevice(
        context: Context,
        bleCore: BleCore,
        serviceUuid: UUID,
        writeUuid: UUID,
    ): RfidDistancePreset? {
        val preset = RfidDistancePresets.currentPreset(context) ?: return null
        writePresetToDevice(context, bleCore, serviceUuid, writeUuid, preset)
        return preset
    }

    /** Aplica preset al lector sin modificar preferencias (p. ej. verificación puntual). */
    fun applyPresetToDeviceOnly(
        context: Context,
        bleCore: BleCore,
        serviceUuid: UUID,
        writeUuid: UUID,
        preset: RfidDistancePreset,
    ) {
        writePresetToDevice(context, bleCore, serviceUuid, writeUuid, preset)
    }

    fun writePresetToDevice(
        context: Context,
        bleCore: BleCore,
        serviceUuid: UUID,
        writeUuid: UUID,
        preset: RfidDistancePreset,
    ) {
        val prefs = RfidDistancePresets.prefs(context)
        val bean = buildAllParamBeanFromPreset(preset, prefs)
        bleCore.writeData(serviceUuid, writeUuid, CmdBuilder.buildSetAllParamCmd(bean))
    }

    /** Restaura en el lector un snapshot capturado antes de un modo temporal. */
    fun restoreSnapshotToDevice(
        context: Context,
        bleCore: BleCore,
        serviceUuid: UUID,
        writeUuid: UUID,
        snapshot: RfidParamSnapshot,
    ) {
        val prefs = RfidDistancePresets.prefs(context)
        val bean = buildAllParamBeanFromSnapshot(snapshot, prefs)
        bleCore.writeData(serviceUuid, writeUuid, CmdBuilder.buildSetAllParamCmd(bean))
    }

    /** Restaura en el lector la configuración de distancia guardada para el conteo. */
    fun restoreCountPresetFromPrefs(
        context: Context,
        bleCore: BleCore,
        serviceUuid: UUID,
        writeUuid: UUID,
    ) {
        val prefs = RfidDistancePresets.prefs(context)
        val preset = RfidDistancePresets.currentPreset(context)
        val bean = if (preset != null) {
            buildAllParamBeanFromPreset(preset, prefs)
        } else {
            buildAllParamBeanFromPrefs(
                prefs,
                prefs.getInt(Constants.KEY_POWER_LEVEL, Constants.DEFAULT_POWER_LEVEL),
            )
        }
        bleCore.writeData(serviceUuid, writeUuid, CmdBuilder.buildSetAllParamCmd(bean))
    }

    fun apply(
        context: Context,
        bleCore: BleCore,
        serviceUuid: UUID,
        writeUuid: UUID,
        preset: RfidDistancePreset,
        wasReading: Boolean,
        resumeReading: () -> Unit,
    ) {
        if (wasReading) {
            bleCore.writeData(serviceUuid, writeUuid, CmdBuilder.buildStopInventoryCmd())
        }
        RfidDistancePresets.savePresetToPrefs(context, preset)
        writePresetToDevice(context, bleCore, serviceUuid, writeUuid, preset)
        Toast.makeText(
            context,
            context.getString(R.string.rfid_preset_applied, context.getString(preset.titleRes)),
            Toast.LENGTH_SHORT,
        ).show()
        if (wasReading) {
            resumeReading()
        }
    }
}
