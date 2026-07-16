package com.dohealth.handheld.ui.inventorysessions

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dohealth.handheld.data.inventory.InventorySession
import com.dohealth.handheld.databinding.ItemSessionListBinding
import com.dohealth.handheld.utils.SessionStatusUi
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class InventorySessionsAdapter(
    private val onOpen: (InventorySession) -> Unit,
    private val onDelete: (InventorySession) -> Unit
) : ListAdapter<InventorySession, InventorySessionsAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemSessionListBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VH(binding, onOpen, onDelete)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    class VH(
        private val binding: ItemSessionListBinding,
        private val onOpen: (InventorySession) -> Unit,
        private val onDelete: (InventorySession) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(session: InventorySession) {
            binding.sessionTitleText.text = session.name
            binding.sessionSubtitleText.visibility = View.GONE
            val totalScans = session.items.size
            val uniqueCount = session.items.asSequence().map { it.data }.distinct().count()
            binding.sessionStatsText.text = if (session.mode == "RFID") {
                "Tags: $uniqueCount | Escaneos: $totalScans"
            } else {
                "Productos: $uniqueCount | Escaneos: $totalScans"
            }
            SessionStatusUi.apply(
                binding.statusChip,
                SessionStatusUi.fromItemCount(uniqueCount),
            )
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            binding.sessionUpdatedText.text = "Actualizado: ${sdf.format(Date(session.updatedAt))}"
            (binding.root as MaterialCardView).setOnClickListener { onOpen(session) }
            binding.deleteButton.setOnClickListener { onDelete(session) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<InventorySession>() {
            override fun areItemsTheSame(oldItem: InventorySession, newItem: InventorySession): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: InventorySession, newItem: InventorySession): Boolean =
                oldItem == newItem
        }
    }
}
