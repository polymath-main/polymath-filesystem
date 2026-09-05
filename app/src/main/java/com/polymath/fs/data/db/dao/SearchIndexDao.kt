package com.polymath.fs.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.polymath.fs.data.db.entities.SearchIndexEntity

@Dao
interface SearchIndexDao {
    @Query("SELECT * FROM search_index WHERE name LIKE '%' || :query || '%'")
    suspend fun search(query: String): List<SearchIndexEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<SearchIndexEntity>)

    @Query("DELETE FROM search_index")
    suspend fun clearAll(): Int
}
