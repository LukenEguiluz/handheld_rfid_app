package com.dohealth.handheld.utils

import android.widget.TextView
import com.dohealth.handheld.R

enum class SessionStatus {
    PENDING,
    ACTIVE,
    COMPLETE,
}

object SessionStatusUi {

    fun apply(chip: TextView, status: SessionStatus) {
        val (label, bg) = when (status) {
            SessionStatus.PENDING -> R.string.status_pending to R.drawable.bg_chip_status_pending
            SessionStatus.ACTIVE -> R.string.status_active to R.drawable.bg_chip_status_active
            SessionStatus.COMPLETE -> R.string.status_complete to R.drawable.bg_chip_status_complete
        }
        chip.setText(label)
        chip.setBackgroundResource(bg)
    }

    /** Conteo con catálogo esperado (HGM / ESFERICA). */
    fun fromCountProgress(matched: Int, expected: Int, scanned: Int): SessionStatus {
        if (scanned == 0) return SessionStatus.PENDING
        if (expected > 0 && matched >= expected) return SessionStatus.COMPLETE
        return SessionStatus.ACTIVE
    }

    /** Sesión genérica con conteo simple (inventario / relación). */
    fun fromItemCount(count: Int): SessionStatus {
        if (count == 0) return SessionStatus.PENDING
        return SessionStatus.ACTIVE
    }
}
