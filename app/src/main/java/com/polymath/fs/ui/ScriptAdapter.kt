package com.polymath.fs.ui

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.polymath.fs.R
import com.polymath.fs.core.BuiltInScriptManager
import java.io.File

class ScriptAdapter(
    private val scripts: List<File>,
    private val listener: OnScriptClickListener
) : RecyclerView.Adapter<ScriptAdapter.ScriptViewHolder>() {

    interface OnScriptClickListener {
        fun onRun(script: File)
        fun onEdit(script: File)
        fun onPin(script: File)
        fun onSchedule(script: File)
        fun onShare(script: File)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScriptViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_script, parent, false)
        return ScriptViewHolder(view)
    }

    override fun onBindViewHolder(holder: ScriptViewHolder, position: Int) {
        holder.bind(scripts[position], listener)
    }

    override fun getItemCount(): Int = scripts.size

    class ScriptViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardScript: CardView = itemView.findViewById(R.id.card_script)
        private val scriptIcon: TextView = itemView.findViewById(R.id.scriptIcon)
        private val scriptName: TextView = itemView.findViewById(R.id.scriptName)
        private val scriptCategory: TextView = itemView.findViewById(R.id.scriptCategory)
        private val scriptDesc: TextView = itemView.findViewById(R.id.scriptDesc)
        private val scriptPath: TextView = itemView.findViewById(R.id.scriptPath)
        private val tvPinnedBadge: TextView = itemView.findViewById(R.id.tvPinnedBadge)
        private val btnPin: TextView = itemView.findViewById(R.id.btnPin)
        private val btnSchedule: TextView = itemView.findViewById(R.id.btnSchedule)
        private val btnShare: TextView = itemView.findViewById(R.id.btnShare)
        private val btnEdit: TextView = itemView.findViewById(R.id.btnEdit)
        private val btnRun: TextView = itemView.findViewById(R.id.btnRun)

        fun bind(file: File, listener: OnScriptClickListener) {
            val context = itemView.context
            val info = BuiltInScriptManager.getScriptMetadata(file)

            scriptName.text = info.displayName
            scriptCategory.text = info.category.uppercase()
            scriptDesc.text = info.description
            scriptPath.text = file.name

            val pinPrefs = context.getSharedPreferences("script_pins", Context.MODE_PRIVATE)
            val isPinned = pinPrefs.getBoolean(file.absolutePath, false)

            tvPinnedBadge.visibility = if (isPinned) View.VISIBLE else View.GONE
            btnPin.text = if (isPinned) "📌 PINNED" else "📌 PIN"
            btnPin.setTextColor(if (isPinned) Color.parseColor("#F59E0B") else Color.parseColor("#94A3B8"))

            val icon = when (info.category) {
                "Organizer", "AutoOrganizer" -> "🗂️"
                "Security", "GhostVault" -> "🛡️"
                "Themes" -> "🎨"
                "Network" -> "🌐"
                "Utils" -> "⚙️"
                "Core" -> "⚡"
                "SystemAnalytics" -> "📊"
                else -> "📜"
            }
            scriptIcon.text = icon

            btnRun.setOnClickListener { listener.onRun(file) }
            btnEdit.setOnClickListener { listener.onEdit(file) }
            btnPin.setOnClickListener { listener.onPin(file) }
            btnSchedule.setOnClickListener { listener.onSchedule(file) }
            btnShare.setOnClickListener { listener.onShare(file) }
        }
    }
}
