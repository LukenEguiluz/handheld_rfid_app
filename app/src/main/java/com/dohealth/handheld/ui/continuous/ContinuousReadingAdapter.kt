package com.dohealth.handheld.ui.continuous

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dohealth.handheld.data.model.ContinuousTag
import com.dohealth.handheld.databinding.ItemContinuousTagBinding
import com.dohealth.handheld.utils.ProximityHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ContinuousReadingAdapter : ListAdapter<ContinuousTag, ContinuousReadingAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemContinuousTagBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position + 1)
    }

    class ViewHolder(
        private val binding: ItemContinuousTagBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        fun bind(tag: ContinuousTag, position: Int) {
            binding.positionText.text = "#$position"
            binding.epcText.text = tag.epc
            binding.readCountText.text = "×${tag.readCount}"
            binding.rssiText.text = "RSSI: ${tag.rssi} dBm"
            val proximity = ProximityHelper.fromRssi(tag.rssi)
            binding.proximityText.text = proximity.label
            binding.timeText.text = timeFormat.format(Date(tag.lastSeen))
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ContinuousTag>() {
        override fun areItemsTheSame(oldItem: ContinuousTag, newItem: ContinuousTag): Boolean =
            oldItem.epc == newItem.epc

        override fun areContentsTheSame(oldItem: ContinuousTag, newItem: ContinuousTag): Boolean =
            oldItem == newItem
    }
}
