package com.dohealth.handheld.utils

import android.widget.TextView
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Barra determinada: sube **rápido** de 0 al [holdAt] (por defecto 95).
 * Mientras sigue la carga, texto y barra permanecen en [holdAt] ([ensureHeldAt]).
 * Solo al cerrar overlay o navegar, [flashFullProgress] lleva barra y porcentaje a **100**.
 */
object DeterminateLoadingProgress {

    /** Duración típica de la subida 0 → [holdAt] (solo visual). */
    private const val DEFAULT_RAMP_MS = 310L

    private fun easedProgressTowardEnd(step: Int, steps: Int, holdAt: Int): Int {
        val t = step / steps.toFloat()
        val eased = 1f - (1f - t) * (1f - t)
        return ((holdAt * eased).toInt()).coerceIn(0, holdAt)
    }

    /**
     * Anima rápido hasta [holdAt] y termina el Job dejando la barra en ese valor hasta [flashFullProgress].
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
            val steps = 18
            val stepMs = (rampDurationMs / steps).coerceAtLeast(8L)
            for (s in 1..steps) {
                if (!isActive) return@launch
                val p = easedProgressTowardEnd(s, steps, holdAt)
                bar.setProgressCompat(p, true)
                percentLabel?.text = "${p}%"
                delay(stepMs)
            }
            if (!isActive) return@launch
            bar.setProgressCompat(holdAt, true)
            percentLabel?.text = "${holdAt}%"
        }
    }

    /**
     * Una sola subida determinada entre [from] y [to] con ease-out cuadrático (percibida como “casí lista”).
     */
    suspend fun rampSegmentEaseOut(
        bar: LinearProgressIndicator,
        percentLabel: TextView?,
        from: Int,
        to: Int,
        durationMs: Long,
    ) {
        bar.isIndeterminate = false
        if (from == to) {
            bar.setProgressCompat(to, false)
            percentLabel?.text = "${to}%"
            return
        }
        val lo = min(from, to)
        val hi = max(from, to)
        val span = hi - lo
        val steps = span.coerceIn(12, 30)
        val stepMs = (durationMs / steps).coerceAtLeast(8L)
        for (i in 1..steps) {
            val t = i / steps.toFloat()
            val eased = 1f - (1f - t) * (1f - t)
            val travelled = ((span * eased).toInt()).coerceIn(0, span)
            val p = if (to >= from) from + travelled else from - travelled
            bar.setProgressCompat(p, true)
            percentLabel?.text = "${p}%"
            delay(stepMs)
        }
        bar.setProgressCompat(to, true)
        percentLabel?.text = "${to}%"
    }

    /**
     * Barra y etiqueta exactamente en [holdAt] (sube desde el valor actual si cancelaste la rampa a medias).
     * No muestra **100 %** hasta [flashFullProgress].
     */
    suspend fun ensureHeldAt(
        bar: LinearProgressIndicator,
        percentLabel: TextView?,
        holdAt: Int = 95,
        bridgeToHoldMs: Long = 180L,
    ) {
        bar.isIndeterminate = false
        val curRaw = bar.progress.coerceAtLeast(0)
        if (curRaw >= holdAt) {
            bar.setProgressCompat(holdAt, false)
            percentLabel?.text = "${holdAt}%"
            return
        }
        val cur = curRaw
        val span = holdAt - cur
        val steps = span.coerceIn(4, 22)
        val stepMs = (bridgeToHoldMs / steps).coerceAtLeast(8L)
        for (i in 1..steps) {
            val p = cur + span * i / steps
            bar.setProgressCompat(p, true)
            percentLabel?.text = "${p}%"
            delay(stepMs)
        }
        bar.setProgressCompat(holdAt, false)
        percentLabel?.text = "${holdAt}%"
    }

    /** Barra llena + **100 %**; breve pausa para que se vea el cierre justo antes de ocultar o navegar. */
    suspend fun flashFullProgress(
        bar: LinearProgressIndicator,
        percentLabel: TextView?,
        settleMs: Long = 140L,
    ) {
        bar.isIndeterminate = false
        bar.setProgressCompat(100, true)
        percentLabel?.text = "100%"
        delay(settleMs)
    }
}
