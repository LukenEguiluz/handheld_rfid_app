package com.dohealth.handheld.utils

import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import com.dohealth.handheld.R
import com.dohealth.handheld.databinding.DialogRfidDistanceBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Diálogo Stitch: distancia de lectura con radio + subtítulo por preset.
 * Preselecciona el preset activo (prefs o coincidencia de parámetros).
 */
object RfidDistanceDialog {

    fun show(
        activity: AppCompatActivity,
        current: RfidDistancePreset? = RfidDistancePresets.currentPreset(activity),
        onSelected: (RfidDistancePreset) -> Unit,
    ) {
        val binding = DialogRfidDistanceBinding.inflate(LayoutInflater.from(activity))
        binding.distanceRadioGroup.clearCheck()
        when (current) {
            RfidDistancePreset.FAR -> binding.presetFar.isChecked = true
            RfidDistancePreset.MEDIUM -> binding.presetMedium.isChecked = true
            RfidDistancePreset.NEAR -> binding.presetNear.isChecked = true
            RfidDistancePreset.ZERO -> binding.presetZero.isChecked = true
            null -> Unit
        }

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.rfid_preset_dialog_title)
            .setView(binding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val preset = when (binding.distanceRadioGroup.checkedRadioButtonId) {
                    R.id.presetZero -> RfidDistancePreset.ZERO
                    R.id.presetMedium -> RfidDistancePreset.MEDIUM
                    R.id.presetNear -> RfidDistancePreset.NEAR
                    R.id.presetFar -> RfidDistancePreset.FAR
                    else -> return@setPositiveButton
                }
                onSelected(preset)
            }
            .show()
    }
}
