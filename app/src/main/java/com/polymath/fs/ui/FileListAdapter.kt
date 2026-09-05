package com.polymath.fs.ui

import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.polymath.fs.R
import com.polymath.fs.models.FileNode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileListAdapter(
    private val onItemClick: (FileNode) -> Unit,
    private val onMenuClick: (FileNode, View) -> Unit
) : ListAdapter<FileNode, FileListAdapter.FileViewHolder>(FileDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = getItem(position)
        holder.bind(file)
    }

    inner class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val fileIcon: TextView = itemView.findViewById(R.id.fileIcon)
        private val fileName: TextView = itemView.findViewById(R.id.fileName)
        private val fileDetails: TextView = itemView.findViewById(R.id.fileDetails)
        private val btnMenu: TextView = itemView.findViewById(R.id.btnMenu)
        
        private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
            
            btnMenu.setOnClickListener { view ->
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onMenuClick(getItem(position), view)
                }
            }
        }

        fun bind(file: FileNode) {
            fileName.text = file.name
            
            if (file.isDirectory) {
                fileIcon.text = "📁"
                fileDetails.text = dateFormat.format(Date(file.lastModified))
            } else {
                fileIcon.text = "📄"
                val sizeStr = Formatter.formatFileSize(itemView.context, file.size)
                val dateStr = dateFormat.format(Date(file.lastModified))
                fileDetails.text = "\$sizeStr • \$dateStr"
            }
            
            when (file) {
                is FileNode.RootFile -> {
                    fileDetails.text = "\${fileDetails.text} • \${file.permissions}"
                }
                else -> {}
            }
        }
    }
}

class FileDiffCallback : DiffUtil.ItemCallback<FileNode>() {
    override fun areItemsTheSame(oldItem: FileNode, newItem: FileNode): Boolean {
        return oldItem.path == newItem.path
    }

    override fun areContentsTheSame(oldItem: FileNode, newItem: FileNode): Boolean {
        return oldItem == newItem
    }
}
