package edu.cit.audioscholar.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import edu.cit.audioscholar.data.local.dao.CloudCacheDao
import edu.cit.audioscholar.data.local.dao.RecordingMetadataDao
import edu.cit.audioscholar.data.local.model.CloudCacheEntity
import edu.cit.audioscholar.data.local.model.RecordingMetadata

@Database(
    entities = [
        RecordingMetadata::class,
        CloudCacheEntity::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordingMetadataDao(): RecordingMetadataDao
    abstract fun cloudCacheDao(): CloudCacheDao

}
