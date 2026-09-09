package com.polymath.fs.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "canvas_edges")
data class CanvasEdgeEntity(
    @PrimaryKey val id: String,
    val canvasId: String,
    val sourcePath: String,
    val targetPath: String,
    val relationType: String = "PARENT_CHILD",
    val label: String = "",
    val weight: Float = 1.0f
)
