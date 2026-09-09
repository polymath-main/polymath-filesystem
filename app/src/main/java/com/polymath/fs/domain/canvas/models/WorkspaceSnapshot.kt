package com.polymath.fs.domain.canvas.models

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class NodeSnapshotData(
    val filePath: String,
    val x: Float,
    val y: Float,
    val isPinned: Boolean,
    val themeColor: Int?
)

data class EdgeSnapshotData(
    val id: String,
    val sourcePath: String,
    val targetPath: String,
    val relationType: String,
    val label: String,
    val weight: Float
)

data class WorkspaceSnapshot(
    val id: String = UUID.randomUUID().toString(),
    val canvasPath: String,
    val timestamp: Long = System.currentTimeMillis(),
    val label: String,
    val nodes: List<NodeSnapshotData>,
    val edges: List<EdgeSnapshotData>
) {
    fun toJson(): String {
        val rootObj = JSONObject()
        rootObj.put("id", id)
        rootObj.put("canvasPath", canvasPath)
        rootObj.put("timestamp", timestamp)
        rootObj.put("label", label)

        val nodesArr = JSONArray()
        for (n in nodes) {
            val nObj = JSONObject()
            nObj.put("path", n.filePath)
            nObj.put("x", n.x.toDouble())
            nObj.put("y", n.y.toDouble())
            nObj.put("pinned", n.isPinned)
            if (n.themeColor != null) {
                nObj.put("color", n.themeColor)
            }
            nodesArr.put(nObj)
        }
        rootObj.put("nodes", nodesArr)

        val edgesArr = JSONArray()
        for (e in edges) {
            val eObj = JSONObject()
            eObj.put("id", e.id)
            eObj.put("source", e.sourcePath)
            eObj.put("target", e.targetPath)
            eObj.put("type", e.relationType)
            eObj.put("label", e.label)
            eObj.put("weight", e.weight.toDouble())
            edgesArr.put(eObj)
        }
        rootObj.put("edges", edgesArr)

        return rootObj.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): WorkspaceSnapshot? {
            return try {
                val root = JSONObject(jsonStr)
                val id = root.optString("id", UUID.randomUUID().toString())
                val canvasPath = root.getString("canvasPath")
                val timestamp = root.optLong("timestamp", System.currentTimeMillis())
                val label = root.optString("label", "Snapshot")

                val nodesList = mutableListOf<NodeSnapshotData>()
                val nodesArr = root.optJSONArray("nodes")
                if (nodesArr != null) {
                    for (i in 0 until nodesArr.length()) {
                        val nObj = nodesArr.getJSONObject(i)
                        nodesList.add(
                            NodeSnapshotData(
                                filePath = nObj.getString("path"),
                                x = nObj.getDouble("x").toFloat(),
                                y = nObj.getDouble("y").toFloat(),
                                isPinned = nObj.optBoolean("pinned", false),
                                themeColor = if (nObj.has("color")) nObj.getInt("color") else null
                            )
                        )
                    }
                }

                val edgesList = mutableListOf<EdgeSnapshotData>()
                val edgesArr = root.optJSONArray("edges")
                if (edgesArr != null) {
                    for (i in 0 until edgesArr.length()) {
                        val eObj = edgesArr.getJSONObject(i)
                        edgesList.add(
                            EdgeSnapshotData(
                                id = eObj.getString("id"),
                                sourcePath = eObj.getString("source"),
                                targetPath = eObj.getString("target"),
                                relationType = eObj.optString("type", CanvasRelationType.USER_LINK.name),
                                label = eObj.optString("label", ""),
                                weight = eObj.optDouble("weight", 1.0).toFloat()
                            )
                        )
                    }
                }

                WorkspaceSnapshot(
                    id = id,
                    canvasPath = canvasPath,
                    timestamp = timestamp,
                    label = label,
                    nodes = nodesList,
                    edges = edgesList
                )
            } catch (e: Exception) {
                null
            }
        }

        fun capture(canvas: CognitiveCanvas, label: String): WorkspaceSnapshot {
            val nodeSnapshots = canvas.nodes.map { node ->
                NodeSnapshotData(
                    filePath = node.fileNode.path,
                    x = node.x,
                    y = node.y,
                    isPinned = node.isPinned,
                    themeColor = node.themeColor
                )
            }

            val edgeSnapshots = canvas.edges.map { edge ->
                EdgeSnapshotData(
                    id = edge.id,
                    sourcePath = edge.sourceNodeId,
                    targetPath = edge.targetNodeId,
                    relationType = edge.relationType.name,
                    label = edge.label,
                    weight = edge.weight
                )
            }

            return WorkspaceSnapshot(
                canvasPath = canvas.rootPath,
                label = label,
                nodes = nodeSnapshots,
                edges = edgeSnapshots
            )
        }
    }
}
