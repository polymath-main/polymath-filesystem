package com.polymath.fs.ui.canvas.adapters

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.polymath.fs.databinding.ItemMlSuggestionClusterBinding
import com.polymath.fs.domain.canvas.ai.CanvasSuggestionCluster

class MLSuggestionClusterAdapter(
    private val onApplyClicked: (CanvasSuggestionCluster) -> Unit
) : RecyclerView.Adapter<MLSuggestionClusterAdapter.ViewHolder>() {

    private val clusters = mutableListOf<CanvasSuggestionCluster>()

    fun submitList(newClusters: List<CanvasSuggestionCluster>) {
        clusters.clear()
        clusters.addAll(newClusters)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMlSuggestionClusterBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(clusters[position])
    }

    override fun getItemCount(): Int = clusters.size

    inner class ViewHolder(
        private val binding: ItemMlSuggestionClusterBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(cluster: CanvasSuggestionCluster) {
            binding.tvClusterTitle.text = cluster.title
            val confidencePct = (cluster.confidence * 100).toInt()
            binding.tvConfidenceBadge.text = "$confidencePct% Match"
            binding.tvClusterReason.text = cluster.reason

            val fileSummary = cluster.nodes.joinToString(" • ") { it.fileNode.name }
            binding.tvClusterFileNames.text = fileSummary

            cluster.suggestedThemeColor?.let { color ->
                binding.vColorIndicator.backgroundTintList = ColorStateList.valueOf(color)
            } ?: run {
                binding.vColorIndicator.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#38BDF8"))
            }

            binding.btnApplyCluster.setOnClickListener {
                onApplyClicked(cluster)
            }
        }
    }
}
