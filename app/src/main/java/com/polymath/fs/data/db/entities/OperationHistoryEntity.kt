package com.polymath.fs.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "operation_history")
data class OperationHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val operationType: String,
    val sourcePath: String,
    val destPath: String?,
    val timestamp: Long,
    val isSuccess: Boolean
)
