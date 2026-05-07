package com.dohealth.handheld.ui.esferica

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.dohealth.handheld.data.esferica.ProductCountResultDto
import com.dohealth.handheld.databinding.ItemEsfericaProductResultBinding
import com.dohealth.handheld.R
import kotlin.math.roundToInt

data class EsfericaProductRow(
    val dto: ProductCountResultDto,
    val expanded: Boolean,
)

private val ROW_DIFF = object : DiffUtil.ItemCallback<EsfericaProductRow>() {
    override fun areItemsTheSame(old: EsfericaProductRow, new: EsfericaProductRow) =
        (old.dto.code ?: "") == (new.dto.code ?: "") &&
            (old.dto.description ?: "") == (new.dto.description ?: "")

    override fun areContentsTheSame(old: EsfericaProductRow, new: EsfericaProductRow) =
        old == new
}

class EsfericaProductResultsAdapter(
    private val onToggle: (EsfericaProductRow) -> Unit,
) : ListAdapter<EsfericaProductRow, EsfericaProductResultsAdapter.VH>(ROW_DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemEsfericaProductResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(private val binding: ItemEsfericaProductResultBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(row: EsfericaProductRow) {
            val p = row.dto
            val ctx = binding.root.context
            binding.productCodeText.text = p.code?.ifBlank { "—" } ?: "—"
            binding.productDescText.text = p.description ?: ""

            binding.countExpected.text = ctx.getString(R.string.esferica_row_expected_fmt, p.expectedQty)
            binding.countRead.text = ctx.getString(R.string.esferica_row_read_fmt, p.scannedQty)
            binding.countMissing.text = ctx.getString(R.string.esferica_row_miss_fmt, p.missingQty)
            binding.countExtra.text = ctx.getString(R.string.esferica_row_extra_fmt, p.extraQty)

            binding.detailSection.visibility = if (row.expanded) View.VISIBLE else View.GONE
            binding.expandButton.rotation = if (row.expanded) 180f else 0f
            binding.missingRfidsText.text = rfidBlob(p.missingRfids)
            binding.readRfidsText.text = rfidBlob(p.scannedRfids)
            binding.extraRfidsText.text = rfidBlob(p.extraRfids)

            val tint = tintForCard(p)
            (binding.root as? MaterialCardView)?.setCardBackgroundColor(tint)

            val click = View.OnClickListener { onToggle(row) }
            binding.cardRoot.setOnClickListener(click)
            binding.expandButton.setOnClickListener(click)
        }

        private fun rfidBlob(xs: List<String>) =
            xs.joinToString("\n").ifBlank { "—" }

        private fun tintForCard(p: ProductCountResultDto): Int {
            val ctx = binding.root.context
            val baseWarn = androidx.core.content.ContextCompat.getColor(ctx, R.color.warning)
            val baseErr = androidx.core.content.ContextCompat.getColor(ctx, R.color.error)
            val baseOk = androidx.core.content.ContextCompat.getColor(ctx, R.color.success)
            val baseInfo = androidx.core.content.ContextCompat.getColor(ctx, R.color.info)

            return when {
                p.expectedQty == 0 && p.extraQty > 0 -> ColorUtils.setAlphaComponent(baseInfo, 40)
                p.expectedQty > 0 && p.missingQty == p.expectedQty -> ColorUtils.setAlphaComponent(baseErr, 45)
                p.expectedQty > 0 && p.missingQty > 0 ->
                    ColorUtils.setAlphaComponent(baseWarn, mixAlpha(p.expectedQty, p.missingQty))
                p.expectedQty > 0 && p.missingQty == 0 && p.extraQty == 0 -> ColorUtils.setAlphaComponent(baseOk, 45)
                else -> androidx.core.content.ContextCompat.getColor(ctx, R.color.background_white)
            }
        }

        private fun mixAlpha(expected: Int, missing: Int): Int {
            val ratio = missing.toDouble() / expected.toDouble().coerceAtLeast(1.0)
            return (ratio * 160).roundToInt().coerceIn(48, 192)
        }
    }
}
