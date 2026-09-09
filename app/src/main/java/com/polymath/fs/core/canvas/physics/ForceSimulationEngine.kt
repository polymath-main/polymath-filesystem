package com.polymath.fs.core.canvas.physics

import com.polymath.fs.domain.canvas.models.CanvasEdge
import com.polymath.fs.domain.canvas.models.CanvasNode
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
    private val springStiffness: Float = 0.04f,
    private val restingLength: Float = 140f,
    private val centerGravityStrength: Float = 0.002f,
    private val damping: Float = 0.85f,
    private val maxVelocity: Float = 40f
) {

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

        // 2. Hooke's Law Spring attraction for connected edges
        for (edge in edges) {
            val source = nodeMap[edge.sourceNodeId] ?: continue
            val target = nodeMap[edge.targetNodeId] ?: continue

            val dx = target.x - source.x
            val dy = target.y - source.y
            val dist = sqrt(max(dx * dx + dy * dy, 1f))

            val displacement = dist - (restingLength * edge.weight)
            val springForce = displacement * springStiffness

            val fx = (dx / dist) * springForce
            val fy = (dy / dist) * springForce

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
}
