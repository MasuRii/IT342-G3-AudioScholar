package edu.cit.audioscholar.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import edu.cit.audioscholar.data.local.model.RecordingMetadata
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingMetadataDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(metadata: RecordingMetadata)

    @Query("SELECT * FROM recording_metadata ORDER BY timestampMillis DESC")
    fun getAllRecordings(): Flow<List<RecordingMetadata>>

    @Query("SELECT * FROM recording_metadata WHERE filePath = :filePath LIMIT 1")
    fun getRecordingByPath(filePath: String): Flow<RecordingMetadata?>

    @Query("DELETE FROM recording_metadata WHERE filePath = :filePath")
    suspend fun deleteRecordingByPath(filePath: String)

    @Query("DELETE FROM recording_metadata WHERE filePath IN (:filePaths)")
    suspend fun deleteRecordingsByPaths(filePaths: List<String>): Int

    @Query("UPDATE recording_metadata SET title = :newTitle WHERE filePath = :filePath")
    suspend fun updateTitle(filePath: String, newTitle: String): Int

    @Query("UPDATE recording_metadata SET remoteRecordingId = :remoteId, cacheTimestampMillis = :timestamp WHERE filePath = :filePath")
    suspend fun updateRemoteIdAndCacheTime(filePath: String, remoteId: String, timestamp: Long?): Int

    @Query("UPDATE recording_metadata SET cachedSummaryText = :summaryText, cachedGlossaryItems = :glossaryItemsJson, cacheTimestampMillis = :timestamp WHERE remoteRecordingId = :remoteId")
    suspend fun updateCachedSummary(remoteId: String, summaryText: String?, glossaryItemsJson: String?, timestamp: Long): Int

    @Query("UPDATE recording_metadata SET cachedRecommendations = :recommendationsJson, cacheTimestampMillis = :timestamp WHERE remoteRecordingId = :remoteId")
    suspend fun updateCachedRecommendations(remoteId: String, recommendationsJson: String?, timestamp: Long): Int
}
