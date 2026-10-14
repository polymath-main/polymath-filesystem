package com.polymath.fs.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.polymath.fs.data.db.entities.SafGrantEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SafGrantDao {
    @Query("SELECT * FROM saf_grants ORDER BY addedAt DESC")
    fun getAllGrants(): Flow<List<SafGrantEntity>>

    @Query("SELECT * FROM saf_grants ORDER BY addedAt DESC")
    fun getAllGrantsSync(): List<SafGrantEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGrant(grant: SafGrantEntity)

    @Query("DELETE FROM saf_grants WHERE treeUri = :treeUri")
    suspend fun deleteGrant(treeUri: String): Int
}
