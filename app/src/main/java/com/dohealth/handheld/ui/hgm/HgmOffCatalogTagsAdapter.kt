package com.dohealth.handheld.ui.hgm

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dohealth.handheld.databinding.ItemHgmOffCatalogTagBinding

data class HgmOffCatalogTagRow(
    val rfid: String,
    val sku: String,
)

class HgmOffCatalogTagsAdapter :
    ListAdapter<HgmOffCatalogTagRow, HgmOffCatalogTagsAdapter.Vh>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vh {
        val binding = ItemHgmOffCatalogTagBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return Vh(binding)
    }

    override fun onBindViewHolder(holder: Vh, position: Int) {
        holder.bind(getItem(position))
    }

    class Vh(private val binding: ItemHgmOffCatalogTagBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: HgmOffCatalogTagRow) {
            binding.offCatalogSkuText.text = row.sku
            binding.offCatalogRfidText.text = row.rfid
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<HgmOffCatalogTagRow>() {
            override fun areItemsTheSame(oldItem: HgmOffCatalogTagRow, newItem: HgmOffCatalogTagRow) =
                oldItem.rfid == newItem.rfid

            override fun areContentsTheSame(oldItem: HgmOffCatalogTagRow, newItem: HgmOffCatalogTagRow) =
                oldItem == newItem
        }
    }
}
