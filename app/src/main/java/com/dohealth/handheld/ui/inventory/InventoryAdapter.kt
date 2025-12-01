package com.dohealth.handheld.ui.inventory

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dohealth.handheld.databinding.ItemInventoryBinding
import com.dohealth.handheld.data.model.InventoryItem
import java.text.SimpleDateFormat
import java.util.*

class InventoryAdapter : ListAdapter<InventoryItem, InventoryAdapter.InventoryViewHolder>(
    InventoryDiffCallback()
) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InventoryViewHolder {
        val binding = ItemInventoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return InventoryViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: InventoryViewHolder, position: Int) {
        holder.bind(getItem(position), position + 1)
    }
    
    class InventoryViewHolder(
        private val binding: ItemInventoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        
        fun bind(item: InventoryItem, position: Int) {
            binding.positionText.text = "#$position"
            binding.dataText.text = item.data
            binding.modeText.text = item.mode
            
            // Mostrar contador de repeticiones si es mayor a 1
            if (item.readCount > 1) {
                binding.repeatCountText.text = "×${item.readCount}"
                binding.repeatCountText.visibility = android.view.View.VISIBLE
            } else {
                binding.repeatCountText.visibility = android.view.View.GONE
            }
            
            // Mostrar RSSI solo para RFID
            if (item.mode == "RFID" && item.rssi != 0) {
                binding.rssiText.text = "RSSI: ${item.rssi} dBm"
                binding.rssiText.visibility = android.view.View.VISIBLE
            } else {
                binding.rssiText.visibility = android.view.View.GONE
            }
            
            val time = dateFormat.format(Date(item.timestamp))
            binding.timeText.text = time
        }
    }
    
    class InventoryDiffCallback : DiffUtil.ItemCallback<InventoryItem>() {
        override fun areItemsTheSame(oldItem: InventoryItem, newItem: InventoryItem): Boolean {
            return oldItem.data == newItem.data && oldItem.timestamp == newItem.timestamp
        }
        
        override fun areContentsTheSame(oldItem: InventoryItem, newItem: InventoryItem): Boolean {
            return oldItem == newItem
        }
    }
}

