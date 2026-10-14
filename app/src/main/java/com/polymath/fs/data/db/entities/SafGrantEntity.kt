package com.polymath.fs.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saf_grants")
data class SafGrantEntity(
    @PrimaryKey val treeUri: String,
    val authority: String,
    val treeDocId: String,
    val label: String,
    val writable: Boolean,
    val addedAt: Long
)
