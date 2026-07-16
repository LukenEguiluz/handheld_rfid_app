package com.dohealth.handheld.utils

import android.widget.TextView
import androidx.core.content.ContextCompat
import com.dohealth.handheld.R
import com.dohealth.handheld.data.esferica.ProductCountResultDto
import com.google.android.material.card.MaterialCardView

enum class ProductCountStatus {
    PENDING,
    INCOMPLETE,
    COMPLETE,
    EXTRA,
}

object ProductCountStatusUi {

    fun fromDto(p: ProductCountResultDto): ProductCountStatus {
        val expected = p.expectedQty
        val scanned = p.scannedQty
        val missing = p.missingQty
        val extra = p.extraQty
        return when {
            expected == 0 && extra > 0 -> ProductCountStatus.EXTRA
            expected > 0 && scanned == 0 -> ProductCountStatus.PENDING
            expected > 0 && missing == 0 && extra == 0 -> ProductCountStatus.COMPLETE
            expected > 0 && missing > 0 -> ProductCountStatus.INCOMPLETE
            else -> ProductCountStatus.INCOMPLETE
        }
    }

    fun applyChip(chip: TextView, status: ProductCountStatus) {
        val (label, bg) = when (status) {
            ProductCountStatus.PENDING -> R.string.status_pending to R.drawable.bg_chip_status_pending
            ProductCountStatus.INCOMPLETE -> R.string.status_incomplete to R.drawable.bg_chip_status_incomplete
            ProductCountStatus.COMPLETE -> R.string.status_complete to R.drawable.bg_chip_status_complete
            ProductCountStatus.EXTRA -> R.string.status_extra to R.drawable.bg_chip_status_extra
        }
        chip.setText(label)
        chip.setBackgroundResource(bg)
    }

    fun applyCardStroke(card: MaterialCardView, status: ProductCountStatus) {
        val strokeColor = when (status) {
            ProductCountStatus.COMPLETE -> R.color.success
            ProductCountStatus.INCOMPLETE -> R.color.warning
            ProductCountStatus.EXTRA -> R.color.info
            ProductCountStatus.PENDING -> R.color.outline_variant
        }
        card.setCardBackgroundColor(ContextCompat.getColor(card.context, R.color.background_white))
        card.strokeColor = ContextCompat.getColor(card.context, strokeColor)
        card.strokeWidth = card.context.resources.getDimensionPixelSize(R.dimen.card_stroke_width_status)
    }
}
