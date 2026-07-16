package com.dohealth.handheld.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dohealth.handheld.data.history.ScanHistoryEntry
import com.dohealth.handheld.databinding.ItemScanHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScanHistoryAdapter : ListAdapter<ScanHistoryEntry, ScanHistoryAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemScanHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position), position)
    }

    class VH(private val binding: ItemScanHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ScanHistoryEntry, position: Int) {
            binding.positionText.text = "#${position + 1}"
            binding.codeText.text = item.code
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            binding.timeText.text = sdf.format(Date(item.timestamp))
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ScanHistoryEntry>() {
            override fun areItemsTheSame(oldItem: ScanHistoryEntry, newItem: ScanHistoryEntry): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: ScanHistoryEntry, newItem: ScanHistoryEntry): Boolean =
                oldItem == newItem
        }
    }
}

