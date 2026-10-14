package com.polymath.fs.data.remote

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RemoteProfileDao {
    @Query("SELECT * FROM remote_profiles ORDER BY label ASC")
    fun getAllProfiles(): Flow<List<RemoteProfileEntity>>

    @Query("SELECT * FROM remote_profiles ORDER BY label ASC")
    fun getAllProfilesSync(): List<RemoteProfileEntity>

    @Query("SELECT * FROM remote_profiles WHERE id = :id LIMIT 1")
    suspend fun getProfile(id: String): RemoteProfileEntity?

    @Query("SELECT * FROM remote_profiles WHERE id = :id LIMIT 1")
    fun getProfileSync(id: String): RemoteProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: RemoteProfileEntity)

    @Query("DELETE FROM remote_profiles WHERE id = :id")
    suspend fun deleteProfile(id: String): Int
}
