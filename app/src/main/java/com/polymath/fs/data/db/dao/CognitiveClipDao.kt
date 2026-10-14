package com.polymath.fs.data.db.dao

import androidx.room.*
import com.polymath.fs.data.db.entities.CognitiveClipEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CognitiveClipDao {

    @Query("SELECT * FROM cognitive_clips ORDER BY isPinned DESC, timestamp DESC")
    fun getAllClips(): Flow<List<CognitiveClipEntity>>

    @Query("SELECT * FROM cognitive_clips WHERE content LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%' ORDER BY isPinned DESC, timestamp DESC")
    fun searchClips(query: String): Flow<List<CognitiveClipEntity>>

    @Query("SELECT * FROM cognitive_clips WHERE id = :id LIMIT 1")
    suspend fun getClipById(id: Long): CognitiveClipEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(clip: CognitiveClipEntity): Long

    @Update
    suspend fun update(clip: CognitiveClipEntity)

    @Delete
    suspend fun delete(clip: CognitiveClipEntity)

    @Query("DELETE FROM cognitive_clips WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM cognitive_clips")
    suspend fun clearAll()
}
