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
    private var viewOptions: com.polymath.fs.models.ViewOptions = com.polymath.fs.models.ViewOptions(),
    private val onItemClick: (FileNode) -> Unit,
    private val onMenuClick: (FileNode, View) -> Unit
) : ListAdapter<FileNode, FileListAdapter.FileViewHolder>(FileDiffCallback()) {

    fun setViewOptions(options: com.polymath.fs.models.ViewOptions) {
        this.viewOptions = options
        notifyDataSetChanged()
    }

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
        private val fileIcon: android.widget.ImageView = itemView.findViewById(R.id.fileIcon)
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
                fileIcon.setImageResource(R.drawable.ic_folder)
                fileDetails.text = dateFormat.format(Date(file.lastModified))
            } else {
                val ext = file.name.substringAfterLast('.', "").lowercase()
                val iconRes = when (ext) {
                    "jpg", "jpeg", "png", "gif", "bmp", "webp" -> R.drawable.ic_file_image
                    "mp3", "wav", "ogg", "flac", "m4a" -> R.drawable.ic_file_audio
                    "mp4", "mkv", "avi", "mov", "webm" -> R.drawable.ic_file_video
                    "zip", "rar", "7z", "tar", "gz" -> R.drawable.ic_file_archive
                    else -> R.drawable.ic_file_default
                }
                fileIcon.setImageResource(iconRes)
                val sizeStr = Formatter.formatFileSize(itemView.context, file.size)
                val dateStr = dateFormat.format(Date(file.lastModified))
                fileDetails.text = "$sizeStr • $dateStr"
            }
            
            when (file) {
                is FileNode.RootFile -> {
                    fileDetails.text = "${fileDetails.text} • ${file.permissions}"
                }
                else -> {}
            }
            
            // Apply ViewOptions
            fileDetails.visibility = if (viewOptions.showDetails) View.VISIBLE else View.GONE
            
            val scale = itemView.context.resources.displayMetrics.density
            val sizeDp = when (viewOptions.boxSize) {
                com.polymath.fs.models.BoxSize.SMALL -> 32
                com.polymath.fs.models.BoxSize.MEDIUM -> 48
                com.polymath.fs.models.BoxSize.LARGE -> 64
            }
            val sizePx = (sizeDp * scale + 0.5f).toInt()
            
            val iconContainer = fileIcon.parent as View
            val layoutParams = iconContainer.layoutParams
            layoutParams.width = sizePx
            layoutParams.height = sizePx
            iconContainer.layoutParams = layoutParams
            
            val itemContainer = itemView.findViewById<android.widget.LinearLayout>(R.id.itemContainer)
            if (itemContainer != null) {
                itemContainer.orientation = if (viewOptions.layout == com.polymath.fs.models.ViewLayout.GRID) {
                    android.widget.LinearLayout.VERTICAL
                } else {
                    android.widget.LinearLayout.HORIZONTAL
                }
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
