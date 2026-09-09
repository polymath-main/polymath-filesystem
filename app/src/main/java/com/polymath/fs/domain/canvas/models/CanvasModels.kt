package com.polymath.fs.domain.canvas.models

import android.graphics.Color
import com.polymath.fs.models.FileNode
import java.util.UUID

val FileNode.extension: String
    get() = if (isDirectory) "" else name.substringAfterLast('.', "")

val FileNode.formattedSize: String
    get() {
        if (isDirectory) return "DIR"
        val b = size
        return when {
            b >= 1024 * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f GB", b / (1024.0 * 1024.0 * 1024.0))
            b >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", b / (1024.0 * 1024.0))
            b >= 1024 -> String.format(java.util.Locale.US, "%.1f KB", b / 1024.0)
            else -> "$b B"
        }
    }

enum class CanvasNodeType {
    FILE,
    DIRECTORY,
    SCRIPT,
    CLUSTER,
    VIRTUAL_NOTE
}

enum class CanvasRelationType {
    PARENT_CHILD,
    PARENT_TO_PARENT,
    PARENT_TO_CHILD,
    IMPORT_REFERENCE,
    SIMILARITY,
    USER_LINK,
    PIPELINE
}

data class CanvasNode(
    val id: String = UUID.randomUUID().toString(),
    val fileNode: FileNode,
    var x: Float = 0f,
    var y: Float = 0f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    val nodeType: CanvasNodeType = if (fileNode.isDirectory) CanvasNodeType.DIRECTORY else CanvasNodeType.FILE,
    var themeColor: Int? = null,
    var radius: Float = calculateNodeRadius(fileNode),
    var isSelected: Boolean = false,
    var isPinned: Boolean = false,
    var isExpanded: Boolean = false,
    var isHighlighted: Boolean = false,
    val clusterTag: String = if (fileNode.isDirectory) "folder" else fileNode.extension.ifEmpty { "other" }
) {
    val color: Int
        get() = themeColor ?: getNodeColorForFile(fileNode)

    companion object {
        fun calculateNodeRadius(file: FileNode): Float {
            if (file.isDirectory) return 56f
            val size = file.size
            return when {
                size > 100 * 1024 * 1024 -> 54f // > 100MB
                size > 10 * 1024 * 1024 -> 48f  // > 10MB
                size > 1024 * 1024 -> 42f       // > 1MB
                size > 100 * 1024 -> 36f        // > 100KB
                else -> 32f
            }
        }

        fun getNodeColorForFile(file: FileNode): Int {
            if (file.isDirectory) return Color.parseColor("#38BDF8") // Sky blue
            val ext = file.extension.lowercase()
            return when (ext) {
                "kt", "java", "js", "ts", "py", "c", "cpp", "rs", "go", "html", "xml", "json" -> Color.parseColor("#A855F7") // Purple
                "png", "jpg", "jpeg", "webp", "gif", "svg" -> Color.parseColor("#10B981") // Emerald Green
                "mp4", "mkv", "mov", "webm" -> Color.parseColor("#EC4899") // Pink
                "mp3", "flac", "wav", "ogg" -> Color.parseColor("#F59E0B") // Amber
                "zip", "tar", "gz", "7z", "rar" -> Color.parseColor("#EAB308") // Yellow
                "pdf", "doc", "docx", "txt", "md" -> Color.parseColor("#6366F1") // Indigo
                "sh", "bin", "apk" -> Color.parseColor("#F43F5E") // Rose
                else -> Color.parseColor("#94A3B8") // Slate
            }
        }
    }
}

data class CanvasEdge(
    val id: String = UUID.randomUUID().toString(),
    val sourceNodeId: String,
    val targetNodeId: String,
    val relationType: CanvasRelationType = CanvasRelationType.PARENT_CHILD,
    val label: String = "",
    val weight: Float = 1.0f
)

data class CanvasViewport(
    var translationX: Float = 0f,
    var translationY: Float = 0f,
    var scale: Float = 1.0f
) {
    fun toScreenX(worldX: Float): Float = worldX * scale + translationX
    fun toScreenY(worldY: Float): Float = worldY * scale + translationY

    fun toWorldX(screenX: Float): Float = (screenX - translationX) / scale
    fun toWorldY(screenY: Float): Float = (screenY - translationY) / scale

    fun clampScale(minScale: Float = 0.2f, maxScale: Float = 4.0f) {
        scale = scale.coerceIn(minScale, maxScale)
    }
}
