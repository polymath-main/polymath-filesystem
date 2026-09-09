package com.polymath.fs.data.db.dao

import androidx.room.*
import com.polymath.fs.data.db.entities.CanvasEdgeEntity
import com.polymath.fs.data.db.entities.CanvasNodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CognitiveCanvasDao {

    @Query("SELECT * FROM canvas_nodes WHERE canvasId = :canvasId")
    fun getNodesForCanvas(canvasId: String): Flow<List<CanvasNodeEntity>>

    @Query("SELECT * FROM canvas_nodes WHERE canvasId = :canvasId")
    suspend fun getNodesForCanvasOnce(canvasId: String): List<CanvasNodeEntity>

    @Query("SELECT * FROM canvas_nodes WHERE filePath = :filePath LIMIT 1")
    suspend fun getNodeByPath(filePath: String): CanvasNodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateNode(node: CanvasNodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateNodes(nodes: List<CanvasNodeEntity>)

    @Query("UPDATE canvas_nodes SET x = :x, y = :y, updatedAt = :updatedAt WHERE filePath = :filePath")
    suspend fun updateNodeCoordinates(filePath: String, x: Float, y: Float, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE canvas_nodes SET themeColor = :themeColor, updatedAt = :updatedAt WHERE filePath = :filePath")
    suspend fun updateNodeThemeColor(filePath: String, themeColor: Int?, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM canvas_nodes WHERE filePath = :filePath")
    suspend fun deleteNode(filePath: String)

    @Query("DELETE FROM canvas_nodes WHERE canvasId = :canvasId")
    suspend fun clearNodesForCanvas(canvasId: String)

    @Query("SELECT * FROM canvas_edges WHERE canvasId = :canvasId")
    fun getEdgesForCanvas(canvasId: String): Flow<List<CanvasEdgeEntity>>

    @Query("SELECT * FROM canvas_edges WHERE canvasId = :canvasId")
    suspend fun getEdgesForCanvasOnce(canvasId: String): List<CanvasEdgeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateEdge(edge: CanvasEdgeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateEdges(edges: List<CanvasEdgeEntity>)

    @Query("DELETE FROM canvas_edges WHERE id = :id")
    suspend fun deleteEdge(id: String)

    @Query("DELETE FROM canvas_edges WHERE canvasId = :canvasId")
    suspend fun clearEdgesForCanvas(canvasId: String)

    @Query("DELETE FROM canvas_edges WHERE sourcePath = :filePath OR targetPath = :filePath")
    suspend fun deleteEdgesForNode(filePath: String)
}
