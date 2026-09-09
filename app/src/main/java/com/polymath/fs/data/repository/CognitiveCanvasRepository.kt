package com.polymath.fs.data.repository

import com.polymath.fs.data.db.dao.CognitiveCanvasDao
import com.polymath.fs.data.db.entities.CanvasEdgeEntity
import com.polymath.fs.data.db.entities.CanvasNodeEntity
import kotlinx.coroutines.flow.Flow

class CognitiveCanvasRepository(
    private val canvasDao: CognitiveCanvasDao
) {
    fun getNodesFlow(canvasId: String): Flow<List<CanvasNodeEntity>> =
        canvasDao.getNodesForCanvas(canvasId)

    suspend fun getNodesOnce(canvasId: String): List<CanvasNodeEntity> =
        canvasDao.getNodesForCanvasOnce(canvasId)

    suspend fun getNodeByPath(filePath: String): CanvasNodeEntity? =
        canvasDao.getNodeByPath(filePath)

    suspend fun saveNode(node: CanvasNodeEntity) =
        canvasDao.insertOrUpdateNode(node)

    suspend fun saveNodes(nodes: List<CanvasNodeEntity>) =
        canvasDao.insertOrUpdateNodes(nodes)

    suspend fun updateCoordinates(filePath: String, x: Float, y: Float) =
        canvasDao.updateNodeCoordinates(filePath, x, y)

    suspend fun deleteNode(filePath: String) {
        canvasDao.deleteNode(filePath)
        canvasDao.deleteEdgesForNode(filePath)
    }

    fun getEdgesFlow(canvasId: String): Flow<List<CanvasEdgeEntity>> =
        canvasDao.getEdgesForCanvas(canvasId)

    suspend fun getEdgesOnce(canvasId: String): List<CanvasEdgeEntity> =
        canvasDao.getEdgesForCanvasOnce(canvasId)

    suspend fun saveEdge(edge: CanvasEdgeEntity) =
        canvasDao.insertOrUpdateEdge(edge)

    suspend fun saveEdges(edges: List<CanvasEdgeEntity>) =
        canvasDao.insertOrUpdateEdges(edges)

    suspend fun deleteEdge(id: String) =
        canvasDao.deleteEdge(id)

    suspend fun clearCanvas(canvasId: String) {
        canvasDao.clearNodesForCanvas(canvasId)
        canvasDao.clearEdgesForCanvas(canvasId)
    }
}
