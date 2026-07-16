package com.dohealth.handheld.ui.esferica

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dohealth.handheld.data.esferica.EsfericaCountPersistedSession
import com.dohealth.handheld.databinding.ItemSessionListBinding
import com.dohealth.handheld.domain.EsfericaReconcileLocal
import com.dohealth.handheld.utils.SessionStatus
import com.dohealth.handheld.utils.SessionStatusUi
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EsfericaSessionsAdapter(
    private val onOpen: (EsfericaCountPersistedSession) -> Unit,
    private val onDelete: (EsfericaCountPersistedSession) -> Unit,
) : ListAdapter<EsfericaCountPersistedSession, EsfericaSessionsAdapter.Vh>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vh {
        val b = ItemSessionListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Vh(b, onOpen, onDelete)
    }

    override fun onBindViewHolder(holder: Vh, position: Int) {
        holder.bind(getItem(position))
    }

    class Vh(
        private val binding: ItemSessionListBinding,
        private val onOpen: (EsfericaCountPersistedSession) -> Unit,
        private val onDelete: (EsfericaCountPersistedSession) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(s: EsfericaCountPersistedSession) {
            binding.sessionTitleText.text = "${s.clientName} · ${s.warehouseName}"
            binding.sessionSubtitleText.visibility = View.GONE

            val matched =
                EsfericaReconcileLocal.countMatchedUnique(s.expectedItems, s.scannedRfidsOrdered)
            val offCatalog =
                EsfericaReconcileLocal.countOffCatalogUnique(s.expectedItems, s.scannedRfidsOrdered)
            val scanned = s.scannedRfidsOrdered.size
            binding.sessionStatsText.text =
                binding.root.context.getString(
                    com.dohealth.handheld.R.string.esferica_session_stats_line,
                    s.expectedItems.size,
                    matched,
                    offCatalog,
                )

            val status = when {
                s.lastReconcile != null -> SessionStatus.COMPLETE
                else -> SessionStatusUi.fromCountProgress(matched, s.expectedItems.size, scanned)
            }
            SessionStatusUi.apply(binding.statusChip, status)

            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            binding.sessionUpdatedText.text =
                binding.root.context.getString(
                    com.dohealth.handheld.R.string.esferica_session_updated_fmt,
                    sdf.format(Date(s.updatedAt)),
                )

            val card = binding.root as MaterialCardView
            card.setOnClickListener { onOpen(s) }
            binding.deleteButton.setOnClickListener { onDelete(s) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<EsfericaCountPersistedSession>() {
            override fun areItemsTheSame(
                oldItem: EsfericaCountPersistedSession,
                newItem: EsfericaCountPersistedSession,
            ) = oldItem.sessionId == newItem.sessionId

            override fun areContentsTheSame(
                oldItem: EsfericaCountPersistedSession,
                newItem: EsfericaCountPersistedSession,
            ) = oldItem == newItem
        }
    }
}
