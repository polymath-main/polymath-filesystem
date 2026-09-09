package com.polymath.fs.ui.canvas.preview

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import coil.load
import coil.size.Scale
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.polymath.fs.R
import com.polymath.fs.databinding.LayoutCanvasPreviewPanelBinding
import com.polymath.fs.domain.canvas.models.CanvasNode
import com.polymath.fs.domain.canvas.models.extension
import com.polymath.fs.domain.canvas.models.formattedSize
import com.polymath.fs.models.FileNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Controller managing the Expandable Preview Panel that slides up from the bottom
 * when a user selects a file on the CognitiveCanvas.
 */
class CanvasFilePreviewPanelController(
    private val binding: LayoutCanvasPreviewPanelBinding,
    private val context: Context,
    private val scope: CoroutineScope,
    private val onOpenFile: (CanvasNode) -> Unit,
    private val onColorTag: (CanvasNode) -> Unit,
    private val onPanelDismissed: () -> Unit
) {

    private val bottomSheetBehavior: BottomSheetBehavior<View> =
        BottomSheetBehavior.from(binding.previewBottomSheet)

    private var currentNode: CanvasNode? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    init {
        setupBehavior()
        setupListeners()
    }

    private fun setupBehavior() {
        bottomSheetBehavior.isHideable = true
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        bottomSheetBehavior.skipCollapsed = false

        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        binding.btnToggleExpandPreview.setImageResource(R.drawable.ic_expand_more)
                        binding.btnToggleExpandPreview.contentDescription = "Collapse Preview"
                    }
                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        binding.btnToggleExpandPreview.setImageResource(R.drawable.ic_expand_less)
                        binding.btnToggleExpandPreview.contentDescription = "Expand Preview"
                    }
                    BottomSheetBehavior.STATE_HIDDEN -> {
                        currentNode = null
                        onPanelDismissed()
                    }
                    else -> {}
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // Smoothly fade or rotate expand toggle button as user drags
                if (slideOffset > 0.5f) {
                    binding.btnToggleExpandPreview.setImageResource(R.drawable.ic_expand_more)
                } else {
                    binding.btnToggleExpandPreview.setImageResource(R.drawable.ic_expand_less)
                }
            }
        })
    }

    private fun setupListeners() {
        // Toggle Expand / Collapse button
        binding.btnToggleExpandPreview.setOnClickListener {
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            } else {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }

        // Dismiss preview button
        binding.btnDismissPreview.setOnClickListener {
            hidePreview()
        }

        // Tap drag handle to toggle
        binding.dragHandle.setOnClickListener {
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            } else if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }

        // Tap header to expand if collapsed
        binding.llPreviewPeekHeader.setOnClickListener {
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }

        // Quick action: Open File
        binding.btnPreviewOpen.setOnClickListener {
            currentNode?.let { node -> onOpenFile(node) }
        }

        // Quick action: Share File
        binding.btnPreviewShare.setOnClickListener {
            currentNode?.let { node -> shareFile(node) }
        }

        // Quick action: Color Tag
        binding.btnPreviewColorTag.setOnClickListener {
            currentNode?.let { node -> onColorTag(node) }
        }

        // Copy path on click
        binding.tvMetaFullPath.setOnClickListener {
            val path = binding.tvMetaFullPath.text.toString()
            copyToClipboard("File Path", path)
        }
    }

    /**
     * Slides up and populates the expandable preview panel for the selected CanvasNode
     */
    fun showPreview(node: CanvasNode) {
        currentNode = node
        val file = File(node.fileNode.path)
        val ext = node.fileNode.extension.lowercase(Locale.getDefault())

        // Header / Peek Details
        binding.tvPreviewFileName.text = node.fileNode.name
        binding.tvPreviewLocation.text = file.parent ?: "/"

        val formattedSize = node.fileNode.formattedSize
        binding.tvPreviewFileSize.text = formattedSize

        // Extension Badge
        if (node.fileNode.isDirectory) {
            binding.tvPeekExtensionBadge.text = "DIR"
            binding.tvPreviewFileTypeBadge.text = "DIRECTORY"
        } else {
            val badgeText = if (ext.isNotEmpty()) ext.uppercase(Locale.getDefault()) else "FILE"
            binding.tvPeekExtensionBadge.text = badgeText
            binding.tvPreviewFileTypeBadge.text = getFileTypeLabel(ext)
        }

        // Theme color accents if custom color is applied
        val accentColor = node.themeColor ?: Color.parseColor("#38BDF8")
        binding.tvPreviewFileSize.setTextColor(accentColor)
        binding.dragHandle.setBackgroundColor(accentColor)

        // Metadata grid population
        val exactBytes = NumberFormat.getNumberInstance(Locale.US).format(file.length())
        binding.tvMetaExactSize.text = if (node.fileNode.isDirectory) {
            val itemCount = file.listFiles()?.size ?: 0
            "$itemCount items"
        } else {
            "$formattedSize ($exactBytes bytes)"
        }

        binding.tvMetaFullPath.text = file.absolutePath
        binding.tvMetaModified.text = dateFormat.format(Date(file.lastModified()))

        val mimeType = getMimeType(file, ext)
        binding.tvMetaMimeType.text = mimeType

        binding.tvMetaPermissions.text = getPermissionsString(file, node.fileNode)
        binding.tvMetaSpatialCoords.text = String.format(Locale.US, "X: %.1f, Y: %.1f", node.x, node.y)

        // Load image thumbnail and preview content with Coil
        loadThumbnailAndPreview(node, file, ext)

        // Slide up bottom sheet smoothly if hidden
        if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
    }

    /**
     * Hides the preview panel
     */
    fun hidePreview() {
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        currentNode = null
    }

    fun isShowing(): Boolean {
        return bottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN
    }

    fun getCurrentNode(): CanvasNode? = currentNode

    fun updateSpatialCoordinates(x: Float, y: Float) {
        binding.tvMetaSpatialCoords.text = String.format(Locale.US, "X: %.1f, Y: %.1f", x, y)
    }

    fun updateThemeColor(color: Int?) {
        val resolved = color ?: Color.parseColor("#38BDF8")
        binding.tvPreviewFileSize.setTextColor(resolved)
        binding.dragHandle.setBackgroundColor(resolved)
    }

    /**
     * Loads thumbnail using Coil with appropriate fallback and metadata overlays
     */
    private fun loadThumbnailAndPreview(node: CanvasNode, file: File, ext: String) {
        val isImage = ext in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "avif", "svg")
        val isVideo = ext in listOf("mp4", "mkv", "avi", "mov", "webm", "3gp")
        val isAudio = ext in listOf("mp3", "wav", "ogg", "flac", "m4a", "aac")
        val isApk = ext == "apk"
        val isTextOrCode = ext in listOf("kt", "java", "py", "js", "html", "css", "json", "xml", "txt", "md", "c", "cpp", "h", "sh", "sql")

        // Reset visibility
        binding.tvCodeSnippetPreview.visibility = View.GONE
        binding.ivLargePreviewThumbnail.visibility = View.VISIBLE
        binding.tvPreviewDimensionTag.visibility = View.VISIBLE

        val fallbackIcon = getFallbackIconResId(ext, node.fileNode.isDirectory)

        if (node.fileNode.isDirectory) {
            val itemCount = file.listFiles()?.size ?: 0
            binding.tvPreviewDimensionTag.text = "$itemCount items"
            binding.ivPeekThumbnail.load(R.drawable.ic_folder) {
                crossfade(true)
            }
            binding.ivLargePreviewThumbnail.load(R.drawable.ic_folder) {
                crossfade(true)
                scale(Scale.FIT)
            }
            return
        }

        if (isImage && file.exists() && file.canRead()) {
            // Load actual image file using Coil
            binding.ivPeekThumbnail.load(file) {
                crossfade(true)
                scale(Scale.FILL)
                placeholder(fallbackIcon)
                error(fallbackIcon)
            }

            binding.ivLargePreviewThumbnail.load(file) {
                crossfade(true)
                scale(Scale.FIT)
                placeholder(fallbackIcon)
                error(fallbackIcon)
            }

            // Asynchronously query image dimensions
            scope.launch(Dispatchers.IO) {
                val dimensions = getImageDimensions(file)
                withContext(Dispatchers.Main) {
                    if (dimensions != null) {
                        binding.tvPreviewDimensionTag.text = "${dimensions.first} × ${dimensions.second}"
                    } else {
                        binding.tvPreviewDimensionTag.text = "IMAGE"
                    }
                }
            }
        } else if (isApk && file.exists()) {
            // Load APK icon
            scope.launch(Dispatchers.IO) {
                val apkIcon = getApkIcon(file)
                withContext(Dispatchers.Main) {
                    binding.tvPreviewDimensionTag.text = "APK PACKAGE"
                    if (apkIcon != null) {
                        binding.ivPeekThumbnail.load(apkIcon) { crossfade(true) }
                        binding.ivLargePreviewThumbnail.load(apkIcon) {
                            crossfade(true)
                            scale(Scale.FIT)
                        }
                    } else {
                        binding.ivPeekThumbnail.load(fallbackIcon) { crossfade(true) }
                        binding.ivLargePreviewThumbnail.load(fallbackIcon) {
                            crossfade(true)
                            scale(Scale.FIT)
                        }
                    }
                }
            }
        } else if (isVideo && file.exists()) {
            // Video preview
            binding.tvPreviewDimensionTag.text = "VIDEO"
            binding.ivPeekThumbnail.load(file) {
                crossfade(true)
                placeholder(fallbackIcon)
                error(fallbackIcon)
            }
            binding.ivLargePreviewThumbnail.load(file) {
                crossfade(true)
                scale(Scale.FIT)
                placeholder(fallbackIcon)
                error(fallbackIcon)
            }
        } else if (isTextOrCode && file.exists() && file.canRead()) {
            // Text or Code file: Show code snippet preview along with Coil-loaded code icon
            binding.tvPreviewDimensionTag.text = "${ext.uppercase(Locale.getDefault())} CODE"
            binding.ivPeekThumbnail.load(fallbackIcon) {
                crossfade(true)
            }

            scope.launch(Dispatchers.IO) {
                val previewSnippet = readTextSnippet(file, maxLines = 14)
                withContext(Dispatchers.Main) {
                    if (!previewSnippet.isNullOrBlank()) {
                        binding.tvCodeSnippetPreview.text = previewSnippet
                        binding.tvCodeSnippetPreview.visibility = View.VISIBLE
                        binding.ivLargePreviewThumbnail.visibility = View.GONE
                    } else {
                        binding.ivLargePreviewThumbnail.load(fallbackIcon) {
                            crossfade(true)
                            scale(Scale.FIT)
                        }
                    }
                }
            }
        } else {
            // Default file type
            binding.tvPreviewDimensionTag.text = if (ext.isNotEmpty()) ext.uppercase(Locale.getDefault()) else "FILE"
            binding.ivPeekThumbnail.load(fallbackIcon) {
                crossfade(true)
            }
            binding.ivLargePreviewThumbnail.load(fallbackIcon) {
                crossfade(true)
                scale(Scale.FIT)
            }
        }
    }

    private fun getImageDimensions(file: File): Pair<Int, Int>? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            if (options.outWidth > 0 && options.outHeight > 0) {
                Pair(options.outWidth, options.outHeight)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun getApkIcon(file: File): Drawable? {
        return try {
            val pm = context.packageManager
            val info = pm.getPackageArchiveInfo(file.absolutePath, 0) ?: return null
            info.applicationInfo.sourceDir = file.absolutePath
            info.applicationInfo.publicSourceDir = file.absolutePath
            info.applicationInfo.loadIcon(pm)
        } catch (e: Exception) {
            null
        }
    }

    private fun readTextSnippet(file: File, maxLines: Int = 14): String? {
        return try {
            if (file.length() > 5 * 1024 * 1024) return "// File size exceeds 5MB text preview limit"
            val lines = file.bufferedReader().useLines { seq ->
                seq.take(maxLines).toList()
            }
            lines.joinToString("\n")
        } catch (e: Exception) {
            null
        }
    }

    private fun getFileTypeLabel(ext: String): String {
        return when (ext) {
            in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "avif", "svg") -> "IMAGE"
            in listOf("mp4", "mkv", "avi", "mov", "webm", "3gp") -> "VIDEO"
            in listOf("mp3", "wav", "ogg", "flac", "m4a", "aac") -> "AUDIO"
            in listOf("zip", "rar", "7z", "tar", "gz", "bz2") -> "ARCHIVE"
            in listOf("kt", "java", "py", "js", "html", "css", "c", "cpp", "json", "xml") -> "CODE SCRIPT"
            "pdf" -> "PDF DOCUMENT"
            "apk" -> "ANDROID PACKAGE"
            "txt", "md", "doc", "docx" -> "DOCUMENT"
            else -> "FILE"
        }
    }

    private fun getMimeType(file: File, ext: String): String {
        if (file.isDirectory) return "inode/directory"
        val fromExt = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        if (!fromExt.isNullOrEmpty()) return fromExt

        return when (ext) {
            "kt" -> "text/x-kotlin"
            "json" -> "application/json"
            "apk" -> "application/vnd.android.package-archive"
            "log" -> "text/plain"
            "md" -> "text/markdown"
            else -> "application/octet-stream"
        }
    }

    private fun getPermissionsString(file: File, node: FileNode): String {
        if (node is FileNode.RootFile) {
            return "${node.permissions} (Root Verified)"
        }
        val r = if (file.canRead()) "r" else "-"
        val w = if (file.canWrite()) "w" else "-"
        val x = if (file.canExecute()) "x" else "-"
        return "$r$w$x (User: ${if (file.canWrite()) "Read/Write" else "Read-Only"})"
    }

    private fun getFallbackIconResId(ext: String, isDirectory: Boolean): Int {
        if (isDirectory) return R.drawable.ic_folder
        return when (ext) {
            in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "avif") -> R.drawable.ic_file_image
            in listOf("mp4", "mkv", "avi", "mov", "webm") -> R.drawable.ic_file_video
            in listOf("mp3", "wav", "ogg", "flac", "m4a") -> R.drawable.ic_file_audio
            in listOf("zip", "rar", "7z", "tar", "gz") -> R.drawable.ic_file_archive
            in listOf("kt", "java", "py", "js", "html", "css", "json", "xml") -> R.drawable.ic_fluent_code
            "pdf" -> R.drawable.ic_fluent_pdf
            "apk" -> R.drawable.ic_fluent_apk
            else -> R.drawable.ic_file_default
        }
    }

    private fun shareFile(node: CanvasNode) {
        val file = File(node.fileNode.path)
        if (!file.exists()) {
            Toast.makeText(context, "File does not exist", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri = try {
                FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            } catch (e: Exception) {
                Uri.fromFile(file)
            }

            val mimeType = getMimeType(file, node.fileNode.extension.lowercase(Locale.getDefault()))
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share ${file.name}"))
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot share file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(context, "Copied $label to clipboard", Toast.LENGTH_SHORT).show()
    }
}
