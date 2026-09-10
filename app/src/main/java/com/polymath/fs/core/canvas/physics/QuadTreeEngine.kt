package com.polymath.fs.core.canvas.physics

import android.graphics.RectF
import com.polymath.fs.domain.canvas.models.CanvasNode
import kotlin.math.max
import kotlin.math.sqrt

class QuadTreeNode {
    val boundary = RectF()
    val nodeIndices = IntArray(4)
    var count = 0
    
    var isDivided = false
    var nwIdx = -1
    var neIdx = -1
    var swIdx = -1
    var seIdx = -1

    var centerX = 0f
    var centerY = 0f
    var totalMass = 0f

    fun reset() {
        count = 0
        isDivided = false
        nwIdx = -1
        neIdx = -1
        swIdx = -1
        seIdx = -1
        centerX = 0f
        centerY = 0f
        totalMass = 0f
    }
}

class QuadTreeEngine(private val maxNodes: Int = 2500) {
    private val poolSize = maxNodes * 4
    private val treePool = Array(poolSize) { QuadTreeNode() }
    private var poolPointer = 0

    fun clear() {
        poolPointer = 0
    }

    fun obtainNode(left: Float, top: Float, right: Float, bottom: Float): Int {
        if (poolPointer >= poolSize) return -1
        val idx = poolPointer++
        treePool[idx].reset()
        treePool[idx].boundary.set(left, top, right, bottom)
        return idx
    }

    fun insert(treeIdx: Int, fileNodes: List<CanvasNode>, nodeIdx: Int): Boolean {
        if (treeIdx == -1) return false
        val treeNode = treePool[treeIdx]
        val node = fileNodes[nodeIdx]

        if (!treeNode.boundary.contains(node.x, node.y)) return false

        // Incrementally update center of mass
        treeNode.centerX = (treeNode.centerX * treeNode.totalMass + node.x) / (treeNode.totalMass + 1f)
        treeNode.centerY = (treeNode.centerY * treeNode.totalMass + node.y) / (treeNode.totalMass + 1f)
        treeNode.totalMass += 1f

        // Prevent infinite stack overflow loop for identical coordinates
        for (i in 0 until treeNode.count) {
            val existingNode = fileNodes[treeNode.nodeIndices[i]]
            if (existingNode.x == node.x && existingNode.y == node.y) {
                node.x += (Math.random().toFloat() - 0.5f) * 0.05f
                node.y += (Math.random().toFloat() - 0.5f) * 0.05f
            }
        }

        if (treeNode.count < 4 && !treeNode.isDivided) {
            treeNode.nodeIndices[treeNode.count++] = nodeIdx
            return true
        }

        if (!treeNode.isDivided) {
            subdivide(treeIdx, fileNodes)
        }

        return (insert(treeNode.nwIdx, fileNodes, nodeIdx) ||
                insert(treeNode.neIdx, fileNodes, nodeIdx) ||
                insert(treeNode.swIdx, fileNodes, nodeIdx) ||
                insert(treeNode.seIdx, fileNodes, nodeIdx))
    }

    private fun subdivide(parentIdx: Int, fileNodes: List<CanvasNode>) {
        if (parentIdx == -1) return
        val p = treePool[parentIdx]
        val halfW = p.boundary.width() / 2f
        val halfH = p.boundary.height() / 2f
        
        p.nwIdx = obtainNode(p.boundary.left, p.boundary.top, p.boundary.left + halfW, p.boundary.top + halfH)
        p.neIdx = obtainNode(p.boundary.left + halfW, p.boundary.top, p.boundary.right, p.boundary.top + halfH)
        p.swIdx = obtainNode(p.boundary.left, p.boundary.top + halfH, p.boundary.left + halfW, p.boundary.bottom)
        p.seIdx = obtainNode(p.boundary.left + halfW, p.boundary.top + halfH, p.boundary.right, p.boundary.bottom)
        p.isDivided = true

        // Push existing nodes down
        for (i in 0 until p.count) {
            val nIdx = p.nodeIndices[i]
            // Remove mass contribution from parent? No, Barnes-Hut keeps mass aggregated at all levels.
            // We just route them to leaves.
            insert(p.nwIdx, fileNodes, nIdx) || insert(p.neIdx, fileNodes, nIdx) ||
            insert(p.swIdx, fileNodes, nIdx) || insert(p.seIdx, fileNodes, nIdx)
        }
        p.count = 0 // Parent no longer directly holds node indices
    }

    fun computeRepulsionForces(treeIdx: Int, nodeA: CanvasNode, theta: Float, fileNodes: List<CanvasNode>) {
        if (treeIdx == -1) return
        val treeNode = treePool[treeIdx]
        if (treeNode.totalMass == 0f) return

        // If it's a leaf node with nodes, compute direct repulsion
        if (!treeNode.isDivided) {
            for (i in 0 until treeNode.count) {
                val nodeB = fileNodes[treeNode.nodeIndices[i]]
                if (nodeA.id == nodeB.id) continue // Skip self

                var dx = nodeB.x - nodeA.x
                var dy = nodeB.y - nodeA.y
                
                if (dx == 0f && dy == 0f) {
                    dx = 0.1f  
                    dy = 0.1f
                }
                val distSq = max(dx * dx + dy * dy, 1f)
                val minDist = nodeA.radius + nodeB.radius + 20f
                val effectiveDistSq = max(distSq, minDist * minDist)
                
                val force = 32768f / effectiveDistSq
                val inverseDist = 1f / sqrt(effectiveDistSq)

                val fx = dx * inverseDist * force
                val fy = dy * inverseDist * force

                if (!nodeA.isPinned) {
                    nodeA.vx -= fx
                    nodeA.vy -= fy
                }
            }
        } else {
            // Internal node: Barnes-Hut approximation check
            val dx = treeNode.centerX - nodeA.x
            val dy = treeNode.centerY - nodeA.y
            val distSq = max(dx * dx + dy * dy, 1f)
            val width = treeNode.boundary.width()

            if ((width * width) / distSq < (theta * theta)) {
                // Far enough away, treat as single massive body
                val minDistSq = 400f // Approx bounds
                val effectiveDistSq = max(distSq, minDistSq)
                val force = (32768f * treeNode.totalMass) / effectiveDistSq
                val inverseDist = 1f / sqrt(effectiveDistSq)

                if (!nodeA.isPinned) {
                    nodeA.vx -= dx * inverseDist * force
                    nodeA.vy -= dy * inverseDist * force
                }
            } else {
                // Too close, recurse down
                computeRepulsionForces(treeNode.nwIdx, nodeA, theta, fileNodes)
                computeRepulsionForces(treeNode.neIdx, nodeA, theta, fileNodes)
                computeRepulsionForces(treeNode.swIdx, nodeA, theta, fileNodes)
                computeRepulsionForces(treeNode.seIdx, nodeA, theta, fileNodes)
            }
        }
    }
}
