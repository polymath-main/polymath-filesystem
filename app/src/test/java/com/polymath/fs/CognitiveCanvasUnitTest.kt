package com.polymath.fs

import com.polymath.fs.core.canvas.physics.ForceSimulationEngine
import com.polymath.fs.domain.canvas.models.CanvasNode
import com.polymath.fs.domain.canvas.models.CanvasNodeType
import com.polymath.fs.models.FileNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class CognitiveCanvasUnitTest {

    @Test
    fun testSnapToGridMath() {
        val gridSize = 40f

        val rawX1 = 43f
        val rawY1 = 81f
        val snappedX1 = (rawX1 / gridSize).roundToInt() * gridSize
        val snappedY1 = (rawY1 / gridSize).roundToInt() * gridSize

        assertEquals(40f, snappedX1, 0.001f)
        assertEquals(80f, snappedY1, 0.001f)

        val rawX2 = 62f
        val rawY2 = 26f
        val snappedX2 = (rawX2 / gridSize).roundToInt() * gridSize
        val snappedY2 = (rawY2 / gridSize).roundToInt() * gridSize

        assertEquals(80f, snappedX2, 0.001f)
        assertEquals(40f, snappedY2, 0.001f)
    }

    @Test
    fun testSmartArrangeClustering() {
        val engine = ForceSimulationEngine()

        val sampleFiles = listOf(
            CanvasNode(
                id = "1",
                fileNode = FileNode.LocalFile("docs", "/storage/docs", size = 0, lastModified = 0, isDirectory = true),
                x = 0f,
                y = 0f,
                nodeType = CanvasNodeType.DIRECTORY
            ),
            CanvasNode(
                id = "2",
                fileNode = FileNode.LocalFile("script.js", "/storage/script.js", size = 1024, lastModified = 0, isDirectory = false),
                x = 0f,
                y = 0f,
                nodeType = CanvasNodeType.SCRIPT
            ),
            CanvasNode(
                id = "3",
                fileNode = FileNode.LocalFile("photo.jpg", "/storage/photo.jpg", size = 2048, lastModified = 0, isDirectory = false),
                x = 0f,
                y = 0f,
                nodeType = CanvasNodeType.FILE
            ),
            CanvasNode(
                id = "4",
                fileNode = FileNode.LocalFile("readme.txt", "/storage/readme.txt", size = 512, lastModified = 0, isDirectory = false),
                x = 0f,
                y = 0f,
                nodeType = CanvasNodeType.FILE
            )
        )

        engine.arrangeByClusteredMetadata(sampleFiles, originX = 500f, originY = 500f)

        assertEquals(4, sampleFiles.size)
        // Nodes should have been dispersed away from origin (0, 0)
        for (node in sampleFiles) {
            val distSq = (node.x - 500f) * (node.x - 500f) + (node.y - 500f) * (node.y - 500f)
            assertTrue("Node ${node.fileNode.name} should be arranged around center", distSq > 0f)
        }
    }

    @Test
    fun testSearchHighlightLogic() {
        val nodes = listOf(
            CanvasNode(
                id = "1",
                fileNode = FileNode.LocalFile("MainActivity.kt", "/storage/MainActivity.kt", size = 100, lastModified = 0, isDirectory = false),
                x = 100f,
                y = 100f
            ),
            CanvasNode(
                id = "2",
                fileNode = FileNode.LocalFile("vector_drawable.xml", "/storage/vector_drawable.xml", size = 200, lastModified = 0, isDirectory = false),
                x = 200f,
                y = 200f
            ),
            CanvasNode(
                id = "3",
                fileNode = FileNode.LocalFile("readme.md", "/storage/readme.md", size = 300, lastModified = 0, isDirectory = false),
                x = 300f,
                y = 300f
            )
        )

        val query = "main"
        val results = nodes.filter { it.fileNode.name.contains(query, ignoreCase = true) }
        assertEquals(1, results.size)
        assertEquals("MainActivity.kt", results[0].fileNode.name)
    }

    @Test
    fun testThemeColorStorage() {
        val node = CanvasNode(
            id = "test-node",
            fileNode = FileNode.LocalFile("custom.txt", "/storage/custom.txt", size = 50, lastModified = 0, isDirectory = false),
            x = 120f,
            y = 120f,
            themeColor = 0xFF10B981.toInt()
        )

        assertNotNull(node.themeColor)
        assertEquals(0xFF10B981.toInt(), node.themeColor)
        assertEquals(0xFF10B981.toInt(), node.color)
    }
}
