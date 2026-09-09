package com.polymath.fs.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "canvas_presets")
data class CanvasPresetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val isBuiltIn: Boolean = false,
    val layoutType: String,
    val createdAt: Long = System.currentTimeMillis(),
    val templateJson: String = "{}"
)
