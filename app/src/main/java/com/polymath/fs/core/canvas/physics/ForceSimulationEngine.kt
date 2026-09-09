package com.polymath.fs.core.canvas.physics

import com.polymath.fs.domain.canvas.models.CanvasEdge
import com.polymath.fs.domain.canvas.models.CanvasNode
import com.polymath.fs.domain.canvas.models.CanvasNodeType
import com.polymath.fs.domain.canvas.models.extension
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * High-performance, stable 2D force-directed physics layout engine.
 * Computes Coulomb electrostatic repulsion, Hooke spring edge attraction,
 * central gravity towards workspace anchor, and velocity damping.
 */
class ForceSimulationEngine(
    private val repulsionStrength: Float = 25000f,
    private val springStiffness: Float = 0.055f,
    private val springDamping: Float = 0.12f,
    private val restingLength: Float = 140f,
    private val centerGravityStrength: Float = 0.002f,
    private val damping: Float = 0.88f,
    private val maxVelocity: Float = 45f
) {

    fun applyDragSpring(
        node: CanvasNode,
        targetX: Float,
        targetY: Float,
        stiffness: Float = 0.35f,
        dragDamping: Float = 0.65f
    ) {
        val dx = targetX - node.x
        val dy = targetY - node.y

        // Spring acceleration towards finger
        val ax = dx * stiffness
        val ay = dy * stiffness

        node.vx = (node.vx + ax) * dragDamping
        node.vy = (node.vy + ay) * dragDamping

        node.x += node.vx
        node.y += node.vy
    }

    fun step(
        nodes: List<CanvasNode>,
        edges: List<CanvasEdge>,
        centerX: Float = 0f,
        centerY: Float = 0f
    ): Boolean {
        if (nodes.isEmpty()) return false

        val nodeMap = HashMap<String, CanvasNode>(nodes.size)
        for (node in nodes) {
            nodeMap[node.id] = node
        }

        var totalKineticEnergy = 0f

        // 1. Coulomb Repulsion between all node pairs
        val nodeCount = nodes.size
        for (i in 0 until nodeCount) {
            val nodeA = nodes[i]
            for (j in i + 1 until nodeCount) {
                val nodeB = nodes[j]

                val dx = nodeB.x - nodeA.x
                val dy = nodeB.y - nodeA.y
                val distSq = dx * dx + dy * dy
                val dist = sqrt(max(distSq, 1f))
                val minDist = nodeA.radius + nodeB.radius + 20f

                val effectiveDist = max(dist, minDist)
                val force = repulsionStrength / (effectiveDist * effectiveDist)

                val fx = (dx / dist) * force
                val fy = (dy / dist) * force

                if (!nodeA.isPinned) {
                    nodeA.vx -= fx
                    nodeA.vy -= fy
                }
                if (!nodeB.isPinned) {
                    nodeB.vx += fx
                    nodeB.vy += fy
                }
            }
        }

        // 2. Hooke's Law Spring-Damper for connected edges
        for (edge in edges) {
            val source = nodeMap[edge.sourceNodeId] ?: continue
            val target = nodeMap[edge.targetNodeId] ?: continue

            val dx = target.x - source.x
            val dy = target.y - source.y
            val dist = sqrt(max(dx * dx + dy * dy, 1f))

            val displacement = dist - (restingLength * edge.weight)
            val springForce = displacement * springStiffness

            // Relative velocity damping along the edge unit vector
            val nx = dx / dist
            val ny = dy / dist
            val relVx = target.vx - source.vx
            val relVy = target.vy - source.vy
            val normalDampingForce = (relVx * nx + relVy * ny) * springDamping

            val totalForce = springForce + normalDampingForce
            val fx = nx * totalForce
            val fy = ny * totalForce

            if (!source.isPinned) {
                source.vx += fx
                source.vy += fy
            }
            if (!target.isPinned) {
                target.vx -= fx
                target.vy -= fy
            }
        }

        // 3. Central Gravity towards canvas anchor & Velocity Integration
        for (node in nodes) {
            if (node.isPinned) {
                node.vx = 0f
                node.vy = 0f
                continue
            }

            val gravDx = centerX - node.x
            val gravDy = centerY - node.y
            node.vx += gravDx * centerGravityStrength
            node.vy += gravDy * centerGravityStrength

            node.vx *= damping
            node.vy *= damping

            val speedSq = node.vx * node.vx + node.vy * node.vy
            if (speedSq > maxVelocity * maxVelocity) {
                val speed = sqrt(speedSq)
                node.vx = (node.vx / speed) * maxVelocity
                node.vy = (node.vy / speed) * maxVelocity
            }

            node.x += node.vx
            node.y += node.vy

            totalKineticEnergy += (node.vx * node.vx + node.vy * node.vy)
        }

        return totalKineticEnergy > 0.5f
    }

    fun initializePositions(nodes: List<CanvasNode>, originX: Float = 0f, originY: Float = 0f) {
        val count = nodes.size
        if (count == 0) return

        val radiusStep = 180f
        val angleStep = (2 * Math.PI / min(count, 12)).toFloat()

        for (i in 0 until count) {
            val layer = (i / 12) + 1
            val angle = (i % 12) * angleStep
            val distance = layer * radiusStep

            nodes[i].x = originX + (kotlin.math.cos(angle.toDouble()) * distance).toFloat()
            nodes[i].y = originY + (kotlin.math.sin(angle.toDouble()) * distance).toFloat()
            nodes[i].vx = 0f
            nodes[i].vy = 0f
        }
    }

    /**
     * Organizes nodes into spatial clusters based on file metadata, extensions, and directory paths.
     * Computes distinct radial cluster anchors and seeds nodes in clustered orbits
     * for force-directed convergence.
     */
    fun arrangeByClusteredMetadata(nodes: List<CanvasNode>, originX: Float = 0f, originY: Float = 0f) {
        if (nodes.isEmpty()) return

        // Categorize nodes by metadata cluster
        val clusters = nodes.groupBy { node ->
            if (node.nodeType == CanvasNodeType.DIRECTORY) {
                "Directories"
            } else {
                val ext = node.fileNode.extension.lowercase()
                when (ext) {
                    "kt", "java", "js", "ts", "py", "c", "cpp", "rs", "go", "html", "xml", "json", "sh" -> "Code & Scripts"
                    "png", "jpg", "jpeg", "webp", "gif", "svg", "ico" -> "Images & Vectors"
                    "mp4", "mkv", "mov", "webm", "mp3", "flac", "wav", "ogg" -> "Audio & Video"
                    "pdf", "doc", "docx", "txt", "md", "csv", "xlsx", "epub" -> "Documents"
                    "zip", "tar", "gz", "7z", "rar", "apk", "bin" -> "Archives & Packages"
                    else -> "Other Files"
                }
            }
        }

        val clusterList = clusters.entries.toList()
        val numClusters = clusterList.size
        val clusterRadius = max(320f, numClusters * 95f)

        clusterList.forEachIndexed { clusterIndex, entry ->
            val clusterAngle = (clusterIndex * (2 * Math.PI / numClusters)).toFloat()
            val clusterCenterX = originX + (kotlin.math.cos(clusterAngle.toDouble()) * clusterRadius).toFloat()
            val clusterCenterY = originY + (kotlin.math.sin(clusterAngle.toDouble()) * clusterRadius).toFloat()

            val memberNodes = entry.value
            val localRadiusStep = 90f

            memberNodes.forEachIndexed { memberIndex, node ->
                if (!node.isPinned) {
                    val localAngle = (memberIndex * 0.9f)
                    val localDist = ((memberIndex / 6) + 1) * localRadiusStep
                    val targetX = clusterCenterX + (kotlin.math.cos(localAngle.toDouble()) * localDist).toFloat()
                    val targetY = clusterCenterY + (kotlin.math.sin(localAngle.toDouble()) * localDist).toFloat()

                    node.x = targetX
                    node.y = targetY
                    node.vx = 0f
                    node.vy = 0f
                }
            }
        }
    }
}
