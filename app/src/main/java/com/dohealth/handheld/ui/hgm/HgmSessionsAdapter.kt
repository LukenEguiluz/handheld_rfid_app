package com.dohealth.handheld.ui.hgm

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dohealth.handheld.R
import com.dohealth.handheld.data.hgm.HgmCountPersistedSession
import com.dohealth.handheld.databinding.ItemSessionListBinding
import com.dohealth.handheld.domain.EsfericaReconcileLocal
import com.dohealth.handheld.utils.SessionStatusUi
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HgmSessionsAdapter(
    private val onOpen: (HgmCountPersistedSession) -> Unit,
    private val onDelete: (HgmCountPersistedSession) -> Unit,
) : ListAdapter<HgmCountPersistedSession, HgmSessionsAdapter.Vh>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vh {
        val b = ItemSessionListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Vh(b, onOpen, onDelete)
    }

    override fun onBindViewHolder(holder: Vh, position: Int) {
        holder.bind(getItem(position))
    }

    class Vh(
        private val binding: ItemSessionListBinding,
        private val onOpen: (HgmCountPersistedSession) -> Unit,
        private val onDelete: (HgmCountPersistedSession) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(s: HgmCountPersistedSession) {
            binding.sessionTitleText.text = s.displayTitle()
            binding.sessionSubtitleText.text = s.catalogFileName
            binding.sessionSubtitleText.visibility = View.VISIBLE

            val matched =
                EsfericaReconcileLocal.countMatchedUnique(s.expectedItems, s.scannedRfidsOrdered)
            val offCatalog =
                EsfericaReconcileLocal.countOffCatalogUnique(s.expectedItems, s.scannedRfidsOrdered)
            val scanned = s.scannedRfidsOrdered.size
            binding.sessionStatsText.text =
                binding.root.context.getString(
                    R.string.hgm_session_stats_line,
                    s.expectedItems.size,
                    matched,
                    offCatalog,
                )

            SessionStatusUi.apply(
                binding.statusChip,
                SessionStatusUi.fromCountProgress(matched, s.expectedItems.size, scanned),
            )

            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            binding.sessionUpdatedText.text =
                binding.root.context.getString(
                    R.string.hgm_session_updated_fmt,
                    sdf.format(Date(s.updatedAt)),
                )

            val card = binding.root as MaterialCardView
            card.setOnClickListener { onOpen(s) }
            binding.deleteButton.setOnClickListener { onDelete(s) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<HgmCountPersistedSession>() {
            override fun areItemsTheSame(
                oldItem: HgmCountPersistedSession,
                newItem: HgmCountPersistedSession,
            ) = oldItem.sessionId == newItem.sessionId

            override fun areContentsTheSame(
                oldItem: HgmCountPersistedSession,
                newItem: HgmCountPersistedSession,
            ) = oldItem == newItem
        }
    }
}
