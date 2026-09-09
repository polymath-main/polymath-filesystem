package com.polymath.fs.data.repository

import com.polymath.fs.data.db.dao.CanvasPresetDao
import com.polymath.fs.data.db.dao.CognitiveCanvasDao
import com.polymath.fs.data.db.dao.WorkspaceSnapshotDao
import com.polymath.fs.data.db.entities.CanvasEdgeEntity
import com.polymath.fs.data.db.entities.CanvasNodeEntity
import com.polymath.fs.data.db.entities.CanvasPresetEntity
import com.polymath.fs.data.db.entities.WorkspaceSnapshotEntity
import com.polymath.fs.domain.canvas.models.CanvasPreset
import kotlinx.coroutines.flow.Flow

class CognitiveCanvasRepository(
    private val canvasDao: CognitiveCanvasDao,
    private val snapshotDao: WorkspaceSnapshotDao? = null,
    private val presetDao: CanvasPresetDao? = null
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

    suspend fun updateThemeColor(filePath: String, themeColor: Int?) =
        canvasDao.updateNodeThemeColor(filePath, themeColor)

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

    suspend fun deleteEdgesByIds(ids: List<String>) {
        if (ids.isNotEmpty()) {
            canvasDao.deleteEdgesByIds(ids)
        }
    }

    suspend fun clearCanvas(canvasId: String) {
        canvasDao.clearNodesForCanvas(canvasId)
        canvasDao.clearEdgesForCanvas(canvasId)
    }

    // --- Workspace Snapshots ---

    fun getSnapshotsFlow(canvasPath: String): Flow<List<WorkspaceSnapshotEntity>>? =
        snapshotDao?.getSnapshotsForCanvas(canvasPath)

    suspend fun getSnapshotsOnce(canvasPath: String): List<WorkspaceSnapshotEntity> =
        snapshotDao?.getSnapshotsForCanvasOnce(canvasPath) ?: emptyList()

    suspend fun saveSnapshot(snapshot: WorkspaceSnapshotEntity) {
        snapshotDao?.insertSnapshot(snapshot)
        snapshotDao?.pruneSnapshots(snapshot.canvasPath, keepCount = 25)
    }

    suspend fun deleteSnapshot(id: String) {
        snapshotDao?.deleteSnapshot(id)
    }

    // --- Canvas Presets ---

    fun getPresetsFlow(): Flow<List<CanvasPresetEntity>>? =
        presetDao?.getAllPresets()

    suspend fun getPresetsOnce(): List<CanvasPresetEntity> =
        presetDao?.getAllPresetsOnce() ?: emptyList()

    suspend fun savePreset(preset: CanvasPresetEntity) {
        presetDao?.insertPreset(preset)
    }

    suspend fun deletePreset(id: String) {
        presetDao?.deletePreset(id)
    }

    suspend fun seedBuiltInPresetsIfEmpty() {
        if (presetDao == null) return
        val existing = presetDao.getAllPresetsOnce()
        if (existing.isEmpty()) {
            val builtIns = CanvasPreset.BUILT_IN_PRESETS.map {
                CanvasPresetEntity(
                    id = it.id,
                    name = it.name,
                    description = it.description,
                    isBuiltIn = true,
                    layoutType = it.layoutType.name,
                    createdAt = it.createdAt,
                    templateJson = it.templateJson
                )
            }
            presetDao.insertPresets(builtIns)
        }
    }
}

