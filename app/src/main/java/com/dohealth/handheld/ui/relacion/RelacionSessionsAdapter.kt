package com.dohealth.handheld.ui.relacion

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dohealth.handheld.data.relacion.RelacionSession
import com.dohealth.handheld.databinding.ItemRelacionSessionBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RelacionSessionsAdapter(
    private val onOpen: (RelacionSession) -> Unit,
    private val onDelete: (RelacionSession) -> Unit
) : ListAdapter<RelacionSession, RelacionSessionsAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemRelacionSessionBinding.inflate(
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
        private val binding: ItemRelacionSessionBinding,
        private val onOpen: (RelacionSession) -> Unit,
        private val onDelete: (RelacionSession) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(session: RelacionSession) {
            binding.sessionNameText.text = session.name
            binding.sessionCountText.text = "Relaciones: ${session.relations.size}"

            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            binding.sessionUpdatedText.text = "Actualizado: ${sdf.format(Date(session.updatedAt))}"

            binding.root.setOnClickListener { onOpen(session) }
            binding.deleteButton.setOnClickListener { onDelete(session) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<RelacionSession>() {
            override fun areItemsTheSame(oldItem: RelacionSession, newItem: RelacionSession): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: RelacionSession, newItem: RelacionSession): Boolean =
                oldItem == newItem
        }
    }
}

