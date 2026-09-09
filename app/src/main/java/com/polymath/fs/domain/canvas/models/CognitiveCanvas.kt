package com.polymath.fs.domain.canvas.models

import com.polymath.fs.models.FileNode
import java.util.UUID

/**
 * CognitiveCanvas manages the spatial domain model, node graph,
 * file-to-node mapping, edge relations, and coordinate layout boundaries.
 */
data class CognitiveCanvas(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Cognitive Canvas",
    val rootPath: String = "",
    val nodes: MutableList<CanvasNode> = mutableListOf(),
    val edges: MutableList<CanvasEdge> = mutableListOf(),
    val viewport: CanvasViewport = CanvasViewport()
) {
    fun findNodeById(id: String): CanvasNode? {
        return nodes.find { it.id == id }
    }

    fun findNodeByPath(path: String): CanvasNode? {
        return nodes.find { it.fileNode.path == path }
    }

    fun addFileNode(fileNode: FileNode, parentPath: String? = null): CanvasNode {
        val node = CanvasNode(
            id = fileNode.path,
            fileNode = fileNode
        )
        nodes.add(node)

        if (parentPath != null) {
            val parentNode = findNodeByPath(parentPath)
            if (parentNode != null) {
                edges.add(
                    CanvasEdge(
                        sourceNodeId = parentNode.id,
                        targetNodeId = node.id,
                        relationType = CanvasRelationType.PARENT_CHILD
                    )
                )
            }
        }
        return node
    }

    fun linkNodes(sourceId: String, targetId: String, relationType: CanvasRelationType, label: String = ""): CanvasEdge {
        val edge = CanvasEdge(
            sourceNodeId = sourceId,
            targetNodeId = targetId,
            relationType = relationType,
            label = label
        )
        edges.add(edge)
        return edge
    }

    fun clear() {
        nodes.clear()
        edges.clear()
    }
}
