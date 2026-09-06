package com.polymath.fs.ui

import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.size.Scale
import com.polymath.fs.R
import com.polymath.fs.models.FileNode
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileListAdapter(
    private var viewOptions: com.polymath.fs.models.ViewOptions = com.polymath.fs.models.ViewOptions(),
    private val onItemClick: (FileNode) -> Unit,
    private val onMenuClick: (FileNode, View) -> Unit
) : ListAdapter<FileNode, FileListAdapter.FileViewHolder>(FileDiffCallback()) {

    var isSelectionMode = false
    val selectedItems = mutableSetOf<String>()
    var onSelectionChange: ((Int) -> Unit)? = null

    fun toggleSelection(path: String) {
        if (selectedItems.contains(path)) {
            selectedItems.remove(path)
            if (selectedItems.isEmpty()) {
                isSelectionMode = false
            }
        } else {
            selectedItems.add(path)
        }
        onSelectionChange?.invoke(selectedItems.size)
        notifyDataSetChanged()
    }

    fun clearSelection() {
        selectedItems.clear()
        isSelectionMode = false
        onSelectionChange?.invoke(0)
        notifyDataSetChanged()
    }

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
                    val file = getItem(position)
                    if (isSelectionMode) {
                        toggleSelection(file.path)
                    } else {
                        onItemClick(file)
                    }
                }
            }
            
            itemView.setOnLongClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION && !isSelectionMode) {
                    isSelectionMode = true
                    toggleSelection(getItem(position).path)
                }
                true
            }
            
            btnMenu.setOnClickListener { view ->
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onMenuClick(getItem(position), view)
                }
            }
        }

        fun bind(file: FileNode) {
            val isSelected = selectedItems.contains(file.path)
            itemView.setBackgroundColor(if (isSelected) android.graphics.Color.parseColor("#3338bdf8") else android.graphics.Color.TRANSPARENT)
            
            fileName.text = file.name
            
            val iconPackPrefix = when (viewOptions.iconPack) {
                com.polymath.fs.models.IconPack.FLUENT -> "ic_fluent"
                com.polymath.fs.models.IconPack.OUTLINE -> "ic_outline"
                com.polymath.fs.models.IconPack.SOLID -> "ic_solid"
                com.polymath.fs.models.IconPack.MACOS -> "ic_macos"
            }

            if (file.isDirectory) {
                fileIcon.setPadding(8, 8, 8, 8)
                val finalIconType = if (file.name.startsWith(".")) "hidden" else if (file.name == "sys" || file.name == "system") "system" else "folder"
                val resId = itemView.context.resources.getIdentifier("${iconPackPrefix}_$finalIconType", "drawable", itemView.context.packageName)
                fileIcon.setImageResource(if (resId != 0) resId else R.drawable.ic_folder)
                fileDetails.text = dateFormat.format(Date(file.lastModified))
            } else {
                val ext = file.name.substringAfterLast('.', "").lowercase()
                val isImage = ext in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "avif")
                
                var iconType = when {
                    ext in listOf("kt", "java", "py", "js", "html", "css", "cpp", "c", "json", "xml") -> "code"
                    ext in listOf("mp3", "wav", "ogg", "flac", "m4a") -> "audio"
                    ext in listOf("mp4", "mkv", "avi", "mov", "webm") -> "video"
                    ext in listOf("zip", "rar", "7z", "tar", "gz") -> "archive"
                    ext == "apk" -> "apk"
                    ext == "pdf" -> "pdf"
                    else -> "default"
                }
                
                if (file.name.startsWith(".")) iconType = "hidden"
                else if (file.name == "sys" || file.name == "system") iconType = "system"

                val fallbackResId = itemView.context.resources.getIdentifier("${iconPackPrefix}_$iconType", "drawable", itemView.context.packageName).let {
                    if (it != 0) it else R.drawable.ic_file_default
                }

                if (isImage) {
                    val localFile = File(file.path)
                    if (localFile.exists() && localFile.canRead()) {
                        fileIcon.setPadding(0, 0, 0, 0)
                        fileIcon.load(localFile) {
                            crossfade(true)
                            scale(Scale.FILL)
                            placeholder(fallbackResId)
                            error(fallbackResId)
                            listener(
                                onError = { _, _ -> fileIcon.setPadding(8, 8, 8, 8) },
                                onSuccess = { _, _ -> fileIcon.setPadding(0, 0, 0, 0) }
                            )
                        }
                    } else {
                        fileIcon.setPadding(8, 8, 8, 8)
                        fileIcon.setImageResource(fallbackResId)
                    }
                } else {
                    fileIcon.setPadding(8, 8, 8, 8)
                    fileIcon.setImageResource(fallbackResId)
                }

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
                com.polymath.fs.models.BoxSize.EXTRA_LARGE -> 80
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
