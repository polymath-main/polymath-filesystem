package com.polymath.fs.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "search_index")
data class SearchIndexEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val path: String,
    val name: String,
    val isDirectory: Boolean
)
