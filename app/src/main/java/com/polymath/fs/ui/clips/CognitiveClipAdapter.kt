package com.polymath.fs.ui.clips

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.polymath.fs.R
import com.polymath.fs.data.db.entities.CognitiveClipEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CognitiveClipAdapter(
    private val onClipClick: (CognitiveClipEntity) -> Unit,
    private val onEditClick: (CognitiveClipEntity) -> Unit,
    private val onDeleteClick: (CognitiveClipEntity) -> Unit,
    private val onPinToggle: (CognitiveClipEntity) -> Unit
) : ListAdapter<CognitiveClipEntity, CognitiveClipAdapter.ClipViewHolder>(ClipDiffCallback()) {

    companion object {
        private val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClipViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_cognitive_clip, parent, false)
        return ClipViewHolder(view)
    }

    override fun onBindViewHolder(holder: ClipViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ClipViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardClip: MaterialCardView = itemView.findViewById(R.id.card_clip)
        private val ivPinned: ImageView = itemView.findViewById(R.id.iv_pinned_icon)
        private val tvTitle: TextView = itemView.findViewById(R.id.tv_clip_title)
        private val tvContent: TextView = itemView.findViewById(R.id.tv_clip_content)
        private val tvCharCount: TextView = itemView.findViewById(R.id.tv_clip_char_count)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tv_clip_timestamp)
        private val tvTags: TextView = itemView.findViewById(R.id.tv_clip_tags)
        private val btnPin: ImageButton = itemView.findViewById(R.id.btn_item_pin)
        private val btnEdit: ImageButton = itemView.findViewById(R.id.btn_item_edit)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btn_item_delete)

        fun bind(clip: CognitiveClipEntity) {
            tvTitle.text = clip.title.ifBlank { "Untitled Clip" }
            tvContent.text = clip.content

            val words = if (clip.content.isBlank()) 0 else clip.content.trim().split("\\s+".toRegex()).size
            tvCharCount.text = "${clip.charCount} chars • $words words"

            tvTimestamp.text = dateFormat.format(Date(clip.timestamp))

            if (clip.tags.isNotBlank()) {
                tvTags.text = clip.tags
                tvTags.visibility = View.VISIBLE
            } else {
                tvTags.visibility = View.GONE
            }

            if (clip.isPinned) {
                ivPinned.visibility = View.VISIBLE
                btnPin.setImageResource(R.drawable.ic_pin)
                btnPin.setColorFilter(Color.parseColor("#A78BFA"))
                cardClip.strokeColor = Color.parseColor("#7C3AED")
                cardClip.strokeWidth = 2
            } else {
                ivPinned.visibility = View.GONE
                btnPin.setImageResource(R.drawable.ic_pin_outline)
                btnPin.setColorFilter(Color.parseColor("#94A3B8"))
                cardClip.strokeColor = Color.parseColor("#24304A")
                cardClip.strokeWidth = 1
            }

            // Single tap on item: opens dialog showing full text with stats and copy
            cardClip.setOnClickListener {
                onClipClick(clip)
            }

            // CRITICAL REQUIREMENT: "tap and hold on the listed data should be copied in current clipboard to paste"
            cardClip.setOnLongClickListener { view ->
                val context = view.context
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipData = ClipData.newPlainText("Cognitive Clip", clip.content)
                clipboard.setPrimaryClip(clipData)
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                Toast.makeText(context, "Copied to clipboard for pasting!", Toast.LENGTH_SHORT).show()
                true
            }

            btnPin.setOnClickListener {
                onPinToggle(clip)
            }

            btnEdit.setOnClickListener {
                onEditClick(clip)
            }

            btnDelete.setOnClickListener {
                onDeleteClick(clip)
            }
        }
    }

    class ClipDiffCallback : DiffUtil.ItemCallback<CognitiveClipEntity>() {
        override fun areItemsTheSame(oldItem: CognitiveClipEntity, newItem: CognitiveClipEntity): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: CognitiveClipEntity, newItem: CognitiveClipEntity): Boolean =
            oldItem == newItem
    }
}
