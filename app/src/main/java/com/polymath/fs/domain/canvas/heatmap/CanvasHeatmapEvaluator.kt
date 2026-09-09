package com.polymath.fs.domain.canvas.heatmap

import android.graphics.Color
import com.polymath.fs.domain.canvas.models.CanvasNode
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

object CanvasHeatmapEvaluator {

    /**
     * Calculates an activity score from 0.0 (dormant/cold) to 1.0 (active/blazing hot)
     * based on lastModified timestamp and neighborhood activity density.
     */
    fun computeHeatScores(nodes: List<CanvasNode>): Map<String, Float> {
        val now = System.currentTimeMillis()
        val baseScores = HashMap<String, Float>(nodes.size)

        for (node in nodes) {
            val modTime = node.fileNode.lastModified
            val ageMs = max(0L, now - modTime)
            val ageHours = (ageMs / (1000.0 * 3600.0)).toFloat()

            // Recency decay curve
            val recencyScore: Float = when {
                ageHours <= 1.0f -> 1.0f
                ageHours <= 24.0f -> (1.0f - (ageHours / 24.0f) * 0.25f) // 0.75 - 1.0
                ageHours <= 168.0f -> (0.75f - ((ageHours - 24.0f) / 144.0f) * 0.35f) // 0.40 - 0.75
                ageHours <= 720.0f -> (0.40f - ((ageHours - 168.0f) / 552.0f) * 0.25f) // 0.15 - 0.40
                else -> max(0.05f, (0.15f * exp((-ageHours / 2160.0).toDouble())).toFloat())
            }
            baseScores[node.id] = recencyScore
        }

        // Neighborhood thermal diffusion: files near other active files gain heat boost
        val finalScores = HashMap<String, Float>(nodes.size)
        val neighborRadiusSq = 250f * 250f

        for (i in nodes.indices) {
            val nodeA = nodes[i]
            var localHeatSum = baseScores[nodeA.id] ?: 0.1f
            var neighborBonus = 0f

            for (j in nodes.indices) {
                if (i == j) continue
                val nodeB = nodes[j]
                val dx = nodeB.x - nodeA.x
                val dy = nodeB.y - nodeA.y
                val distSq = dx * dx + dy * dy

                if (distSq < neighborRadiusSq) {
                    val proximity = 1.0f - (distSq / neighborRadiusSq)
                    val neighborHeat = baseScores[nodeB.id] ?: 0.0f
                    neighborBonus += (neighborHeat * proximity * 0.18f)
                }
            }

            finalScores[nodeA.id] = min(1.0f, localHeatSum + neighborBonus)
        }

        return finalScores
    }

    /**
     * Maps a heat score (0.0 to 1.0) to a thermal color:
     * 0.0 - 0.3: Deep Indigo / Cyan (#1E293B -> #0284C7)
     * 0.3 - 0.7: Amber / Sun Gold (#F59E0B)
     * 0.7 - 1.0: Neon Coral / Flaming Crimson (#EF4444 -> #FF0055)
     */
    fun getHeatColor(score: Float, alpha: Int = 180): Int {
        val clamped = max(0f, min(1f, score))
        val r: Int
        val g: Int
        val b: Int

        when {
            clamped < 0.35f -> {
                // Indigo to Cyan
                val t = clamped / 0.35f
                r = (30 + (2 - 30) * t).toInt()
                g = (41 + (132 - 41) * t).toInt()
                b = (59 + (199 - 59) * t).toInt()
            }
            clamped < 0.70f -> {
                // Cyan to Amber
                val t = (clamped - 0.35f) / 0.35f
                r = (2 + (245 - 2) * t).toInt()
                g = (132 + (158 - 132) * t).toInt()
                b = (199 + (11 - 199) * t).toInt()
            }
            else -> {
                // Amber to Flaming Crimson
                val t = (clamped - 0.70f) / 0.30f
                r = (245 + (255 - 245) * t).toInt()
                g = (158 + (0 - 158) * t).toInt()
                b = (11 + (85 - 11) * t).toInt()
            }
        }

        return Color.argb(alpha, r, g, b)
    }
}
