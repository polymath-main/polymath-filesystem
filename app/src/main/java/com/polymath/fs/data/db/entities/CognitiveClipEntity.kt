package com.polymath.fs.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cognitive_clips")
data class CognitiveClipEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val content: String,
    val title: String,
    val charCount: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val tags: String = "",
    val isPinned: Boolean = false
)
