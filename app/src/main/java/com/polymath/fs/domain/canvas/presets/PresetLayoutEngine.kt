package com.polymath.fs.domain.canvas.presets

import com.polymath.fs.domain.canvas.models.CanvasEdge
import com.polymath.fs.domain.canvas.models.CanvasNode
import com.polymath.fs.domain.canvas.models.CanvasNodeType
import com.polymath.fs.domain.canvas.models.CanvasPreset
import com.polymath.fs.domain.canvas.models.CanvasRelationType
import com.polymath.fs.domain.canvas.models.CognitiveCanvas
import com.polymath.fs.domain.canvas.models.PresetLayoutType
import com.polymath.fs.domain.canvas.models.extension
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object PresetLayoutEngine {

    fun applyPreset(canvas: CognitiveCanvas, preset: CanvasPreset): List<CanvasEdge> {
        val nodes = canvas.nodes
        if (nodes.isEmpty()) return emptyList()

        val generatedEdges = mutableListOf<CanvasEdge>()

        when (preset.layoutType) {
            PresetLayoutType.PROJECT_FLOW -> {
                applyProjectFlowLayout(nodes, generatedEdges)
            }
            PresetLayoutType.CHRONOLOGICAL -> {
                applyChronologicalLayout(nodes, generatedEdges)
            }
            PresetLayoutType.RESOURCE_CLUSTERS -> {
                applyResourceClustersLayout(nodes, generatedEdges)
            }
            PresetLayoutType.HIERARCHICAL_ORBIT -> {
                applyHierarchicalOrbitLayout(nodes, generatedEdges)
            }
            PresetLayoutType.CUSTOM -> {
                // Apply custom JSON template if available, or fall back to resource clusters
                applyResourceClustersLayout(nodes, generatedEdges)
            }
        }

        return generatedEdges
    }

    private fun applyProjectFlowLayout(nodes: List<CanvasNode>, outEdges: MutableList<CanvasEdge>) {
        // Partition into 5 sequential pipeline lanes
        val lane1Config = mutableListOf<CanvasNode>()
        val lane2Code = mutableListOf<CanvasNode>()
        val lane3Assets = mutableListOf<CanvasNode>()
        val lane4Dirs = mutableListOf<CanvasNode>()
        val lane5Build = mutableListOf<CanvasNode>()

        for (node in nodes) {
            if (node.nodeType == CanvasNodeType.DIRECTORY) {
                lane4Dirs.add(node)
                continue
            }
            val ext = node.fileNode.extension.lowercase()
            when (ext) {
                "gradle", "kts", "json", "xml", "yaml", "yml", "env", "properties", "toml", "md", "txt" ->
                    lane1Config.add(node)
                "kt", "java", "py", "js", "ts", "c", "cpp", "rs", "go", "html", "css", "sh" ->
                    lane2Code.add(node)
                "png", "jpg", "jpeg", "webp", "gif", "svg", "mp3", "mp4", "wav", "pdf" ->
                    lane3Assets.add(node)
                "apk", "zip", "tar", "gz", "7z", "jar", "bin" ->
                    lane5Build.add(node)
                else -> lane2Code.add(node)
            }
        }

        val lanes = listOf(
            Pair("Config & Specs", lane1Config),
            Pair("Core Logic", lane2Code),
            Pair("Media & Docs", lane3Assets),
            Pair("Modules", lane4Dirs),
            Pair("Artifacts", lane5Build)
        ).filter { it.second.isNotEmpty() }

        val laneSpacingX = 260f
        val startX = -((lanes.size - 1) * laneSpacingX) / 2f

        lanes.forEachIndexed { laneIdx, (_, laneNodes) ->
            val laneX = startX + laneIdx * laneSpacingX
            val nodeSpacingY = 120f
            val startY = -((laneNodes.size - 1) * nodeSpacingY) / 2f

            laneNodes.forEachIndexed { nodeIdx, node ->
                node.x = laneX
                node.y = startY + nodeIdx * nodeSpacingY
                node.vx = 0f
                node.vy = 0f
            }
        }
    }

    private fun applyChronologicalLayout(nodes: List<CanvasNode>, outEdges: MutableList<CanvasEdge>) {
        // Sort from most recently modified to oldest
        val sortedNodes = nodes.sortedByDescending { it.fileNode.lastModified }
        val count = sortedNodes.size
        if (count == 0) return

        val spacing = 140f
        val cols = max(3, kotlin.math.ceil(kotlin.math.sqrt(count.toDouble())).toInt())

        sortedNodes.forEachIndexed { index, node ->
            val row = index / cols
            val col = index % cols
            val startX = -((cols - 1) * spacing) / 2f
            val startY = -(((count / cols)) * spacing) / 2f

            // Slightly staggered serpentine timeline layout
            val staggeredCol = if (row % 2 == 1) (cols - 1 - col) else col
            node.x = startX + staggeredCol * spacing
            node.y = startY + row * spacing
            node.vx = 0f
            node.vy = 0f
        }
    }

    private fun applyResourceClustersLayout(nodes: List<CanvasNode>, outEdges: MutableList<CanvasEdge>) {
        val clusters = nodes.groupBy { node ->
            if (node.nodeType == CanvasNodeType.DIRECTORY) {
                "Folders"
            } else {
                val ext = node.fileNode.extension.lowercase()
                when (ext) {
                    "kt", "java", "js", "ts", "py", "c", "cpp", "rs", "go", "html", "xml", "json", "sh" -> "Code"
                    "png", "jpg", "jpeg", "webp", "gif", "svg", "ico" -> "Images"
                    "mp4", "mkv", "mov", "webm", "mp3", "flac", "wav" -> "Audio/Video"
                    "pdf", "doc", "docx", "txt", "md", "csv", "xlsx" -> "Documents"
                    "zip", "tar", "gz", "7z", "apk", "bin" -> "Archives"
                    else -> "Files"
                }
            }
        }

        val clusterList = clusters.entries.toList()
        val numClusters = clusterList.size
        val clusterRadius = max(340f, numClusters * 110f)

        clusterList.forEachIndexed { cIdx, entry ->
            val angle = (cIdx * (2 * Math.PI / numClusters)).toFloat()
            val cX = (cos(angle.toDouble()) * clusterRadius).toFloat()
            val cY = (sin(angle.toDouble()) * clusterRadius).toFloat()

            val members = entry.value
            val orbitalRadiusStep = 95f

            members.forEachIndexed { mIdx, node ->
                val localAngle = (mIdx * 1.15f)
                val localDist = ((mIdx / 5) + 1) * orbitalRadiusStep
                node.x = cX + (cos(localAngle.toDouble()) * localDist).toFloat()
                node.y = cY + (sin(localAngle.toDouble()) * localDist).toFloat()
                node.vx = 0f
                node.vy = 0f
            }
        }
    }

    private fun applyHierarchicalOrbitLayout(nodes: List<CanvasNode>, outEdges: MutableList<CanvasEdge>) {
        val rootNode = nodes.find { it.id.startsWith("root_") } ?: nodes.firstOrNull()
        if (rootNode != null) {
            rootNode.x = 0f
            rootNode.y = 0f
            rootNode.vx = 0f
            rootNode.vy = 0f
            rootNode.isPinned = true
        }

        val dirs = nodes.filter { it.nodeType == CanvasNodeType.DIRECTORY && it != rootNode }
        val files = nodes.filter { it.nodeType != CanvasNodeType.DIRECTORY && it != rootNode }

        // Ring 1: Directories (radius 220f)
        val dirRingRadius = 220f
        val dirAngleStep = if (dirs.isNotEmpty()) (2 * Math.PI / dirs.size).toFloat() else 0f
        dirs.forEachIndexed { index, dirNode ->
            val angle = index * dirAngleStep
            dirNode.x = (cos(angle.toDouble()) * dirRingRadius).toFloat()
            dirNode.y = (sin(angle.toDouble()) * dirRingRadius).toFloat()
            dirNode.vx = 0f
            dirNode.vy = 0f
        }

        // Ring 2: Files (radius 440f)
        val fileRingRadius = 440f
        val fileAngleStep = if (files.isNotEmpty()) (2 * Math.PI / files.size).toFloat() else 0f
        files.forEachIndexed { index, fileNode ->
            val angle = index * fileAngleStep
            fileNode.x = (cos(angle.toDouble()) * fileRingRadius).toFloat()
            fileNode.y = (sin(angle.toDouble()) * fileRingRadius).toFloat()
            fileNode.vx = 0f
            fileNode.vy = 0f
        }
    }
}
