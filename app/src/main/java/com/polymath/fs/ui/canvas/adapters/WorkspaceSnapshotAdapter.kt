package com.polymath.fs.ui.canvas.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.polymath.fs.data.db.entities.WorkspaceSnapshotEntity
import com.polymath.fs.databinding.ItemWorkspaceSnapshotBinding
import com.polymath.fs.domain.canvas.models.WorkspaceSnapshot
import java.text.SimpleDateFormat
import java.util.*

class WorkspaceSnapshotAdapter(
    private val onRestoreSnapshot: (WorkspaceSnapshot) -> Unit,
    private val onDeleteSnapshot: (WorkspaceSnapshotEntity) -> Unit
) : RecyclerView.Adapter<WorkspaceSnapshotAdapter.ViewHolder>() {

    private val snapshots = mutableListOf<WorkspaceSnapshotEntity>()
    private val dateFormat = SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault())

    fun submitList(newSnapshots: List<WorkspaceSnapshotEntity>) {
        snapshots.clear()
        snapshots.addAll(newSnapshots)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWorkspaceSnapshotBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(snapshots[position])
    }

    override fun getItemCount(): Int = snapshots.size

    inner class ViewHolder(
        private val binding: ItemWorkspaceSnapshotBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(entity: WorkspaceSnapshotEntity) {
            binding.tvSnapshotLabel.text = entity.label
            binding.tvSnapshotTimestamp.text = dateFormat.format(Date(entity.timestamp))
            binding.tvSnapshotCounts.text = "${entity.nodeCount} nodes • ${entity.edgeCount} links"

            binding.btnRestoreSnapshot.setOnClickListener {
                val snapshot = WorkspaceSnapshot.fromJson(entity.snapshotJson)
                if (snapshot != null) {
                    onRestoreSnapshot(snapshot)
                }
            }

            binding.btnDeleteSnapshot.setOnClickListener {
                onDeleteSnapshot(entity)
            }
        }
    }
}
