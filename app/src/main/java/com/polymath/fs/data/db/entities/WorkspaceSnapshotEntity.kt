package com.polymath.fs.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "canvas_workspace_snapshots")
data class WorkspaceSnapshotEntity(
    @PrimaryKey val id: String,
    val canvasPath: String,
    val timestamp: Long = System.currentTimeMillis(),
    val label: String,
    val nodeCount: Int,
    val edgeCount: Int,
    val snapshotJson: String
)
