package com.polymath.fs.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.polymath.fs.data.db.entities.OperationHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OperationHistoryDao {
    @Query("SELECT * FROM operation_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<OperationHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: OperationHistoryEntity)

    @Query("DELETE FROM operation_history")
    suspend fun clearHistory()
}
