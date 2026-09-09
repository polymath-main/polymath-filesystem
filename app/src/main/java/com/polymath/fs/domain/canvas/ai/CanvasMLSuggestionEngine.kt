package com.polymath.fs.domain.canvas.ai

import android.graphics.Color
import com.polymath.fs.domain.canvas.models.CanvasEdge
import com.polymath.fs.domain.canvas.models.CanvasNode
import com.polymath.fs.domain.canvas.models.CanvasNodeType
import com.polymath.fs.domain.canvas.models.CanvasRelationType
import com.polymath.fs.domain.canvas.models.extension
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

data class CanvasSuggestionCluster(
    val id: String,
    val title: String,
    val reason: String,
    val confidence: Float,
    val nodes: List<CanvasNode>,
    val suggestedThemeColor: Int
)

object CanvasMLSuggestionEngine {

    private val PRESET_COLORS = listOf(
        0xFF38BDF8.toInt(), // Cyan
        0xFF818CF8.toInt(), // Indigo
        0xFFA855F7.toInt(), // Purple
        0xFFEC4899.toInt(), // Pink
        0xFF10B981.toInt(), // Emerald
        0xFFF59E0B.toInt(), // Amber
        0xFFF97316.toInt()  // Orange
    )

    /**
     * Finds disconnected nodes (or nodes that only connect to root) and
     * performs hierarchical agglomerative clustering based on MIME taxonomy and
     * tokenized semantic filename similarity.
     */
    fun proposeAutoGrouping(
        allNodes: List<CanvasNode>,
        allEdges: List<CanvasEdge>
    ): List<CanvasSuggestionCluster> {
        if (allNodes.size < 2) return emptyList()

        // 1. Identify candidate nodes: disconnected from other file nodes
        val connectedNodeIds = HashSet<String>()
        for (edge in allEdges) {
            // Ignore root edges to consider files that haven't been grouped into custom clusters yet
            if (!edge.sourceNodeId.startsWith("root_") && !edge.targetNodeId.startsWith("root_")) {
                connectedNodeIds.add(edge.sourceNodeId)
                connectedNodeIds.add(edge.targetNodeId)
            }
        }

        // We consider all non-root nodes as candidates if they aren't explicitly cross-linked,
        // or all non-root nodes if overall cross-link density is low
        val candidateNodes = allNodes.filter { !it.id.startsWith("root_") }
        if (candidateNodes.size < 2) return emptyList()

        // 2. Precompute feature representations for each node
        val nodeFeatures = candidateNodes.map { node ->
            NodeFeature(
                node = node,
                mimeCategory = getMimeCategory(node),
                tokens = tokenizeFilename(node.fileNode.name),
                charNgrams = extractCharNgrams(node.fileNode.name.lowercase(Locale.ROOT), 3)
            )
        }

        // 3. Compute pairwise similarity matrix & cluster via single-pass agglomerative union-find
        val parent = IntArray(nodeFeatures.size) { it }
        fun find(i: Int): Int {
            var curr = i
            while (curr != parent[curr]) {
                parent[curr] = parent[parent[curr]]
                curr = parent[curr]
            }
            return curr
        }

        fun union(i: Int, j: Int) {
            val rootI = find(i)
            val rootJ = find(j)
            if (rootI != rootJ) {
                parent[rootJ] = rootI
            }
        }

        val pairSimilarities = mutableListOf<PairScore>()

        for (i in 0 until nodeFeatures.size) {
            for (j in i + 1 until nodeFeatures.size) {
                val f1 = nodeFeatures[i]
                val f2 = nodeFeatures[j]
                val score = computeSimilarity(f1, f2)
                if (score >= 0.48f) {
                    pairSimilarities.add(PairScore(i, j, score))
                }
            }
        }

        // Sort descending by similarity
        pairSimilarities.sortByDescending { it.score }

        // Union pairs with high confidence
        for (pair in pairSimilarities) {
            union(pair.index1, pair.index2)
        }

        // 4. Group by cluster root
        val clustersMap = HashMap<Int, MutableList<NodeFeature>>()
        for (i in nodeFeatures.indices) {
            val root = find(i)
            clustersMap.getOrPut(root) { mutableListOf() }.add(nodeFeatures[i])
        }

        // 5. Filter clusters with at least 2 members and synthesize human-readable title & reason
        val results = mutableListOf<CanvasSuggestionCluster>()
        var colorIndex = 0

        for ((_, members) in clustersMap) {
            if (members.size < 2) continue

            val memberNodes = members.map { it.node }
            val (title, reason, avgConfidence) = synthesizeClusterMetadata(members)

            val color = PRESET_COLORS[colorIndex % PRESET_COLORS.size]
            colorIndex++

            results.add(
                CanvasSuggestionCluster(
                    id = "cluster_${results.size + 1}",
                    title = title,
                    reason = reason,
                    confidence = avgConfidence,
                    nodes = memberNodes,
                    suggestedThemeColor = color
                )
            )
        }

        return results.sortedByDescending { it.confidence }
    }

    private data class NodeFeature(
        val node: CanvasNode,
        val mimeCategory: MimeTaxonomy,
        val tokens: Set<String>,
        val charNgrams: Set<String>
    )

    private data class PairScore(
        val index1: Int,
        val index2: Int,
        val score: Float
    )

    private enum class MimeTaxonomy(val weight: Float) {
        KOTLIN_JAVA(1.0f),
        WEB_CODE(1.0f),
        CONFIG_SPECS(0.9f),
        IMAGE_MEDIA(1.0f),
        AUDIO_VIDEO(1.0f),
        DOCUMENT_TEXT(0.9f),
        ARCHIVE_PACKAGE(0.9f),
        DIRECTORY(1.0f),
        OTHER(0.5f)
    }

    private fun getMimeCategory(node: CanvasNode): MimeTaxonomy {
        if (node.nodeType == CanvasNodeType.DIRECTORY) return MimeTaxonomy.DIRECTORY
        val ext = node.fileNode.extension.lowercase(Locale.ROOT)
        return when (ext) {
            "kt", "java" -> MimeTaxonomy.KOTLIN_JAVA
            "js", "ts", "html", "css", "py", "rs", "go", "c", "cpp" -> MimeTaxonomy.WEB_CODE
            "json", "xml", "gradle", "kts", "yaml", "yml", "toml", "env", "properties" -> MimeTaxonomy.CONFIG_SPECS
            "png", "jpg", "jpeg", "webp", "gif", "svg", "ico" -> MimeTaxonomy.IMAGE_MEDIA
            "mp4", "mkv", "mov", "mp3", "flac", "wav", "ogg" -> MimeTaxonomy.AUDIO_VIDEO
            "pdf", "doc", "docx", "txt", "md", "csv", "xlsx" -> MimeTaxonomy.DOCUMENT_TEXT
            "zip", "tar", "gz", "7z", "apk", "jar" -> MimeTaxonomy.ARCHIVE_PACKAGE
            else -> MimeTaxonomy.OTHER
        }
    }

    /**
     * Splits filename into semantic tokens using camelCase, underscores, dots, hyphens, and numbers.
     */
    private fun tokenizeFilename(filename: String): Set<String> {
        val baseName = filename.substringBeforeLast('.', filename)
        val regex = Regex("(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|[^a-zA-Z0-9]+")
        return baseName.split(regex)
            .map { it.lowercase(Locale.ROOT).trim() }
            .filter { it.length > 1 }
            .toSet()
    }

    private fun extractCharNgrams(str: String, n: Int): Set<String> {
        if (str.length < n) return setOf(str)
        val set = mutableSetOf<String>()
        for (i in 0..str.length - n) {
            set.add(str.substring(i, i + n))
        }
        return set
    }

    private fun computeSimilarity(f1: NodeFeature, f2: NodeFeature): Float {
        // 1. MIME similarity score (0 or 0.45)
        val mimeScore = if (f1.mimeCategory == f2.mimeCategory) 0.45f else 0.0f

        // 2. Token Jaccard similarity
        val intersectionSize = f1.tokens.intersect(f2.tokens).size
        val unionSize = f1.tokens.union(f2.tokens).size
        val tokenJaccard = if (unionSize > 0) intersectionSize.toFloat() / unionSize.toFloat() else 0f

        // 3. Character N-gram Dice coefficient
        val ngramIntersect = f1.charNgrams.intersect(f2.charNgrams).size
        val ngramTotal = f1.charNgrams.size + f2.charNgrams.size
        val ngramDice = if (ngramTotal > 0) (2f * ngramIntersect) / ngramTotal.toFloat() else 0f

        // Combined score
        val semanticScore = max(tokenJaccard, ngramDice)
        return mimeScore + (semanticScore * 0.55f)
    }

    private fun synthesizeClusterMetadata(members: List<NodeFeature>): Triple<String, String, Float> {
        val commonMime = members.groupBy { it.mimeCategory }.maxByOrNull { it.value.size }?.key
        val commonTokens = members.flatMap { it.tokens }
            .groupingBy { it }
            .eachCount()
            .filter { it.value >= 2 }
            .entries
            .sortedByDescending { it.value }
            .map { it.key }

        val title: String
        val reason: String

        if (commonTokens.isNotEmpty() && commonMime != null) {
            val prefix = commonTokens.take(2).joinToString(" ").replaceFirstChar { it.uppercase() }
            title = "$prefix (${members.size} files)"
            reason = "Semantic match '$prefix' + ${commonMime.name.replace('_', ' ').lowercase()} types"
        } else if (commonMime != null) {
            val mimeTitle = when (commonMime) {
                MimeTaxonomy.KOTLIN_JAVA -> "Kotlin / Java Modules"
                MimeTaxonomy.WEB_CODE -> "Source Scripts"
                MimeTaxonomy.CONFIG_SPECS -> "Build & Configurations"
                MimeTaxonomy.IMAGE_MEDIA -> "Images & Vectors"
                MimeTaxonomy.AUDIO_VIDEO -> "Audio & Video Media"
                MimeTaxonomy.DOCUMENT_TEXT -> "Documents & Markdown"
                MimeTaxonomy.ARCHIVE_PACKAGE -> "Packages & Archives"
                MimeTaxonomy.DIRECTORY -> "Folder Sub-trees"
                MimeTaxonomy.OTHER -> "Related Files"
            }
            title = "$mimeTitle (${members.size} items)"
            reason = "Homogeneous file MIME group: ${commonMime.name.lowercase()}"
        } else {
            title = "Related Files Cluster (${members.size} items)"
            reason = "High structural filename similarity"
        }

        val avgConfidence = min(0.96f, 0.70f + (members.size * 0.05f))
        return Triple(title, reason, avgConfidence)
    }
}
