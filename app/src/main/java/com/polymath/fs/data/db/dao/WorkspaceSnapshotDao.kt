package com.polymath.fs.data.db.dao

import androidx.room.*
import com.polymath.fs.data.db.entities.WorkspaceSnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkspaceSnapshotDao {

    @Query("SELECT * FROM canvas_workspace_snapshots WHERE canvasPath = :canvasPath ORDER BY timestamp DESC")
    fun getSnapshotsForCanvas(canvasPath: String): Flow<List<WorkspaceSnapshotEntity>>

    @Query("SELECT * FROM canvas_workspace_snapshots WHERE canvasPath = :canvasPath ORDER BY timestamp DESC")
    suspend fun getSnapshotsForCanvasOnce(canvasPath: String): List<WorkspaceSnapshotEntity>

    @Query("SELECT * FROM canvas_workspace_snapshots WHERE id = :id LIMIT 1")
    suspend fun getSnapshotById(id: String): WorkspaceSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: WorkspaceSnapshotEntity)

    @Query("DELETE FROM canvas_workspace_snapshots WHERE id = :id")
    suspend fun deleteSnapshot(id: String)

    @Query("DELETE FROM canvas_workspace_snapshots WHERE canvasPath = :canvasPath")
    suspend fun clearSnapshotsForCanvas(canvasPath: String)

    @Query("DELETE FROM canvas_workspace_snapshots WHERE canvasPath = :canvasPath AND id NOT IN (SELECT id FROM canvas_workspace_snapshots WHERE canvasPath = :canvasPath ORDER BY timestamp DESC LIMIT :keepCount)")
    suspend fun pruneSnapshots(canvasPath: String, keepCount: Int = 20)
}
