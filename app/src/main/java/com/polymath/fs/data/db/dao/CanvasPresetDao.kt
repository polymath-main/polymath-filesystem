package com.polymath.fs.data.db.dao

import androidx.room.*
import com.polymath.fs.data.db.entities.CanvasPresetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CanvasPresetDao {

    @Query("SELECT * FROM canvas_presets ORDER BY isBuiltIn DESC, createdAt DESC")
    fun getAllPresets(): Flow<List<CanvasPresetEntity>>

    @Query("SELECT * FROM canvas_presets ORDER BY isBuiltIn DESC, createdAt DESC")
    suspend fun getAllPresetsOnce(): List<CanvasPresetEntity>

    @Query("SELECT * FROM canvas_presets WHERE id = :id LIMIT 1")
    suspend fun getPresetById(id: String): CanvasPresetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: CanvasPresetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPresets(presets: List<CanvasPresetEntity>)

    @Query("DELETE FROM canvas_presets WHERE id = :id AND isBuiltIn = 0")
    suspend fun deletePreset(id: String)
}
