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

    @Test
    fun testSpringDamperPhysics() {
        val engine = ForceSimulationEngine()
        val node = CanvasNode(
            id = "test-node",
            fileNode = FileNode.LocalFile("test.kt", "/storage/test.kt", size = 100, lastModified = 0, isDirectory = false),
            x = 0f,
            y = 0f
        )

        // Apply drag spring towards (100, 100)
        engine.applyDragSpring(node, 100f, 100f, stiffness = 0.35f, dragDamping = 0.65f)

        // Node should have moved towards target
        assertTrue("Node X should increase towards target", node.x > 0f)
        assertTrue("Node Y should increase towards target", node.y > 0f)
        assertTrue("Node velocity should be positive", node.vx > 0f && node.vy > 0f)

        // Multiple steps should converge close to target
        for (i in 0 until 50) {
            engine.applyDragSpring(node, 100f, 100f, stiffness = 0.35f, dragDamping = 0.65f)
        }
        assertEquals(100f, node.x, 2.0f)
        assertEquals(100f, node.y, 2.0f)
    }

    @Test
    fun testWorkspaceSnapshotSerialization() {
        val canvas = com.polymath.fs.domain.canvas.models.CognitiveCanvas(
            id = "/storage/project",
            nodes = mutableListOf(
                CanvasNode(
                    id = "n1",
                    fileNode = FileNode.LocalFile("App.kt", "/storage/project/App.kt", size = 100, lastModified = 1000, isDirectory = false),
                    x = 150f,
                    y = 250f,
                    themeColor = 0xFF38BDF8.toInt()
                ),
                CanvasNode(
                    id = "n2",
                    fileNode = FileNode.LocalFile("Build.gradle", "/storage/project/Build.gradle", size = 200, lastModified = 1000, isDirectory = false),
                    x = 350f,
                    y = 450f,
                    themeColor = 0xFFA855F7.toInt()
                )
            ),
            edges = mutableListOf(
                com.polymath.fs.domain.canvas.models.CanvasEdge(
                    id = "e1",
                    sourceNodeId = "n1",
                    targetNodeId = "n2",
                    label = "Configures"
                )
            )
        )

        val snapshot = com.polymath.fs.domain.canvas.models.WorkspaceSnapshot.capture(canvas, "Initial Architecture")
        assertEquals("Initial Architecture", snapshot.label)
        assertEquals(2, snapshot.nodes.size)
        assertEquals(1, snapshot.edges.size)

        val json = snapshot.toJson()
        assertTrue("JSON should contain snapshot label", json.contains("Initial Architecture"))

        val restored = com.polymath.fs.domain.canvas.models.WorkspaceSnapshot.fromJson(json)
        assertNotNull(restored)
        assertEquals(snapshot.id, restored!!.id)
        assertEquals(snapshot.label, restored.label)
        assertEquals(2, restored.nodes.size)
        assertEquals(150f, restored.nodes[0].x, 0.001f)
        assertEquals(250f, restored.nodes[0].y, 0.001f)
    }

    @Test
    fun testActivityHeatmapScoring() {
        val now = System.currentTimeMillis()
        val hotNode = CanvasNode(
            id = "hot",
            fileNode = FileNode.LocalFile("recent.kt", "/storage/recent.kt", size = 100, lastModified = now - 60_000, isDirectory = false),
            x = 100f,
            y = 100f
        )
        val coldNode = CanvasNode(
            id = "cold",
            fileNode = FileNode.LocalFile("old.kt", "/storage/old.kt", size = 100, lastModified = now - 30L * 24 * 3600 * 1000, isDirectory = false),
            x = 800f,
            y = 800f
        )

        val scores = com.polymath.fs.domain.canvas.heatmap.CanvasHeatmapEvaluator.computeHeatScores(listOf(hotNode, coldNode))
        val hotScore = scores["hot"] ?: 0f
        val coldScore = scores["cold"] ?: 0f

        assertTrue("Recently modified node should have high activity score: $hotScore", hotScore >= 0.8f)
        assertTrue("Old modified node should have low activity score: $coldScore", coldScore <= 0.4f)
        assertTrue("Hot node should be hotter than cold node", hotScore > coldScore)
    }

    @Test
    fun testMLGroupingProposals() {
        val nodes = listOf(
            CanvasNode(
                id = "doc1",
                fileNode = FileNode.LocalFile("report_q1.pdf", "/storage/report_q1.pdf", size = 100, lastModified = 0, isDirectory = false),
                x = 100f,
                y = 100f
            ),
            CanvasNode(
                id = "doc2",
                fileNode = FileNode.LocalFile("report_q2.pdf", "/storage/report_q2.pdf", size = 100, lastModified = 0, isDirectory = false),
                x = 200f,
                y = 100f
            ),
            CanvasNode(
                id = "doc3",
                fileNode = FileNode.LocalFile("report_q3.pdf", "/storage/report_q3.pdf", size = 100, lastModified = 0, isDirectory = false),
                x = 300f,
                y = 100f
            ),
            CanvasNode(
                id = "img1",
                fileNode = FileNode.LocalFile("logo.png", "/storage/logo.png", size = 100, lastModified = 0, isDirectory = false),
                x = 800f,
                y = 800f
            )
        )

        val clusters = com.polymath.fs.domain.canvas.ai.CanvasMLSuggestionEngine.proposeAutoGrouping(
            allNodes = nodes,
            allEdges = emptyList()
        )

        assertTrue("Should detect auto-grouping cluster for report PDFs", clusters.isNotEmpty())
        val pdfCluster = clusters.firstOrNull { it.nodes.any { n -> n.fileNode.name.contains("report") } }
        assertNotNull(pdfCluster)
        assertTrue("Cluster should have at least 2 matching files", (pdfCluster?.nodes?.size ?: 0) >= 2)
    }
}
