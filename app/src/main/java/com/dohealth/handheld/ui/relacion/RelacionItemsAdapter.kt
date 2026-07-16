package com.dohealth.handheld.ui.relacion

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dohealth.handheld.R
import com.dohealth.handheld.data.relacion.RelacionItem
import com.dohealth.handheld.databinding.ItemRelacionBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RelacionItemsAdapter(
    private val onItemClick: (RelacionItem) -> Unit
) : ListAdapter<RelacionItem, RelacionItemsAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemRelacionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position), position, onItemClick)
    }

    class VH(private val binding: ItemRelacionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: RelacionItem, position: Int, onItemClick: (RelacionItem) -> Unit) {
            binding.positionText.text = "#${position + 1}"
            binding.productText.text = item.productCode
            binding.rfidText.text = item.rfidCode
            // El segundo valor siempre representa un RFID (hex), sin importar si se leyó por UHF o por barras.
            binding.secondCodeLabel.text = binding.root.context.getString(R.string.relacion_item_second_rfid)

            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            binding.timeText.text = sdf.format(Date(item.timestamp))

            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<RelacionItem>() {
            override fun areItemsTheSame(oldItem: RelacionItem, newItem: RelacionItem): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: RelacionItem, newItem: RelacionItem): Boolean =
                oldItem == newItem
        }
    }
}

