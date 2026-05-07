package com.dohealth.handheld.utils

import android.widget.TextView
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Barra determinada: sube **rápido** de 0 al [holdAt] (por defecto 95) y permanece ahí hasta que la
 * corrutina de la tarea real llame [snapToDone], que lleva la barra a 100 acorde al cierre real.
 */
object DeterminateLoadingProgress {

    /** Duración típica de la subida 0 → [holdAt] (solo visual). */
    private const val DEFAULT_RAMP_MS = 380L

    /**
     * Anima rápido hasta [holdAt] y termina el Job dejando la barra quiet en ese valor hasta [snapToDone].
     */
    fun startQuickRampThenHold(
        scope: CoroutineScope,
        bar: LinearProgressIndicator,
        percentLabel: TextView?,
        holdAt: Int = 95,
        rampDurationMs: Long = DEFAULT_RAMP_MS,
    ): Job {
        bar.isIndeterminate = false
        bar.setProgressCompat(0, false)
        percentLabel?.text = "0%"
        return scope.launch {
            val steps = 14
            val stepMs = (rampDurationMs / steps).coerceAtLeast(10L)
            for (s in 1..steps) {
                if (!isActive) return@launch
                val p = ((holdAt * s) / steps).coerceIn(0, holdAt)
                bar.setProgressCompat(p, true)
                percentLabel?.text = "${p}%"
                delay(stepMs)
            }
            if (!isActive) return@launch
            bar.setProgressCompat(holdAt, true)
            percentLabel?.text = "${holdAt}%"
        }
    }

    suspend fun snapToDone(bar: LinearProgressIndicator, percentLabel: TextView?) {
        bar.setProgressCompat(100, true)
        percentLabel?.text = "100%"
        delay(160)
    }
}
