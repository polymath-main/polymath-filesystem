package com.polymath.fs.ui.canvas.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.polymath.fs.data.db.entities.CanvasPresetEntity
import com.polymath.fs.databinding.ItemCanvasPresetBinding

class CanvasPresetAdapter(
    private val onApplyPreset: (CanvasPresetEntity) -> Unit
) : RecyclerView.Adapter<CanvasPresetAdapter.ViewHolder>() {

    private val presets = mutableListOf<CanvasPresetEntity>()

    fun submitList(newPresets: List<CanvasPresetEntity>) {
        presets.clear()
        presets.addAll(newPresets)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCanvasPresetBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(presets[position])
    }

    override fun getItemCount(): Int = presets.size

    inner class ViewHolder(
        private val binding: ItemCanvasPresetBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(preset: CanvasPresetEntity) {
            binding.tvPresetName.text = preset.name
            binding.tvPresetDesc.text = preset.description

            binding.tvPresetBadge.text = if (preset.isBuiltIn) "BUILT-IN" else "CUSTOM"

            val icon = when (preset.layoutType) {
                "PROJECT_FLOW" -> "🌊"
                "CHRONOLOGICAL" -> "⏳"
                "RESOURCE_CLUSTERS" -> "🪐"
                "GRID_MATRIX" -> "📐"
                else -> "✨"
            }
            binding.tvPresetIcon.text = icon

            binding.btnApplyPreset.setOnClickListener {
                onApplyPreset(preset)
            }
        }
    }
}
