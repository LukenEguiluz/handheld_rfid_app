package com.dohealth.handheld.ui.hgm

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dohealth.handheld.R
import com.dohealth.handheld.data.esferica.ProductCountResultDto
import com.dohealth.handheld.databinding.ItemEsfericaProductResultBinding
import com.dohealth.handheld.databinding.ItemHgmHemoGroupBinding
import com.dohealth.handheld.domain.HgmHemoGroup
import com.dohealth.handheld.utils.ProductCountStatusUi

sealed class HgmDisplayRow {
    data class HemocomponenteHeader(
        val group: HgmHemoGroup,
        val expanded: Boolean,
    ) : HgmDisplayRow()

    data class GrupoSubgroup(
        val dto: ProductCountResultDto,
        val expanded: Boolean,
    ) : HgmDisplayRow()
}

class HgmGroupedProductAdapter(
    private val onToggleHemo: (HgmDisplayRow.HemocomponenteHeader) -> Unit,
    private val onToggleGrupo: (HgmDisplayRow.GrupoSubgroup) -> Unit,
) : ListAdapter<HgmDisplayRow, RecyclerView.ViewHolder>(DIFF) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is HgmDisplayRow.HemocomponenteHeader -> 0
        is HgmDisplayRow.GrupoSubgroup -> 1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        when (viewType) {
            0 -> HemoVH(ItemHgmHemoGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            else -> GrupoVH(ItemEsfericaProductResultBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is HgmDisplayRow.HemocomponenteHeader -> (holder as HemoVH).bind(row)
            is HgmDisplayRow.GrupoSubgroup -> (holder as GrupoVH).bind(row)
        }
    }

    inner class HemoVH(private val binding: ItemHgmHemoGroupBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: HgmDisplayRow.HemocomponenteHeader) {
            val g = row.group
            val ctx = binding.root.context
            binding.hemoTitleText.text = g.hemocomponente
            binding.hemoStatsText.text = ctx.getString(
                R.string.hgm_hemo_group_stats,
                g.expectedQty,
                g.scannedQty,
                g.missingQty,
            )
            binding.hemoGrupoCountText.text = ctx.getString(R.string.hgm_hemo_subgroup_count, g.grupos.size)
            binding.expandButton.rotation = if (row.expanded) 90f else 0f
            binding.cardRoot.setOnClickListener { onToggleHemo(row) }
            binding.expandButton.setOnClickListener { onToggleHemo(row) }
        }
    }

    inner class GrupoVH(private val binding: ItemEsfericaProductResultBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: HgmDisplayRow.GrupoSubgroup) {
            val p = row.dto
            val ctx = binding.root.context
            binding.productCodeText.text = ctx.getString(R.string.hgm_grupo_label, p.description ?: "—")
            binding.productDescText.text = ctx.getString(
                R.string.hgm_grupo_units_hint,
                p.expectedQty,
            )
            binding.countExpected.text = ctx.getString(R.string.esferica_row_expected_fmt, p.expectedQty)
            binding.countRead.text = ctx.getString(R.string.esferica_row_read_fmt, p.scannedQty)
            binding.countMissing.text = ctx.getString(R.string.esferica_row_miss_fmt, p.missingQty)
            binding.countExtra.text = ctx.getString(R.string.esferica_row_extra_fmt, p.extraQty)
            binding.detailSection.visibility = if (row.expanded) View.VISIBLE else View.GONE
            binding.expandButton.rotation = if (row.expanded) 90f else 0f
            binding.missingRfidsText.text = rfidBlob(p.missingRfids)
            binding.readRfidsText.text = rfidBlob(p.scannedRfids)
            binding.extraRfidsText.text = rfidBlob(p.extraRfids)

            val status = ProductCountStatusUi.fromDto(p)
            ProductCountStatusUi.applyChip(binding.statusChip, status)
            (binding.root as? com.google.android.material.card.MaterialCardView)?.let {
                ProductCountStatusUi.applyCardStroke(it, status)
            }

            val click = View.OnClickListener { onToggleGrupo(row) }
            binding.cardRoot.setOnClickListener(click)
            binding.expandButton.setOnClickListener(click)
        }

        private fun rfidBlob(xs: List<String>) = xs.joinToString("\n").ifBlank { "—" }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<HgmDisplayRow>() {
            override fun areItemsTheSame(old: HgmDisplayRow, new: HgmDisplayRow): Boolean =
                when {
                    old is HgmDisplayRow.HemocomponenteHeader && new is HgmDisplayRow.HemocomponenteHeader ->
                        old.group.hemocomponente == new.group.hemocomponente
                    old is HgmDisplayRow.GrupoSubgroup && new is HgmDisplayRow.GrupoSubgroup ->
                        (old.dto.code ?: "") == (new.dto.code ?: "") &&
                            (old.dto.description ?: "") == (new.dto.description ?: "")
                    else -> false
                }

            override fun areContentsTheSame(old: HgmDisplayRow, new: HgmDisplayRow) = old == new
        }
    }
}
