package edu.cit.audioscholar.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import edu.cit.audioscholar.data.local.model.CloudCacheEntity

@Dao
interface CloudCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(cache: CloudCacheEntity)

    @Query("SELECT * FROM cloud_cache WHERE remoteRecordingId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): CloudCacheEntity?

    @Query("DELETE FROM cloud_cache WHERE remoteRecordingId = :remoteId")
    suspend fun deleteById(remoteId: String)

    @Query("DELETE FROM cloud_cache WHERE cacheTimestampMillis < :expirationTimeMillis")
    suspend fun deleteExpired(expirationTimeMillis: Long)
}
