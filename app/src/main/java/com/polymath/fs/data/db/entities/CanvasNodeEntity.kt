package com.polymath.fs.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "canvas_nodes")
data class CanvasNodeEntity(
    @PrimaryKey val filePath: String,
    val canvasId: String,
    val x: Float,
    val y: Float,
    val isPinned: Boolean = false,
    val customColor: Int? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
