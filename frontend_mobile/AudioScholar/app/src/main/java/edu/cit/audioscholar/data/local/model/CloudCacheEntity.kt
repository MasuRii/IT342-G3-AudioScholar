package edu.cit.audioscholar.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cloud_cache")
data class CloudCacheEntity(
    @PrimaryKey val remoteRecordingId: String,
    val summaryText: String?,
    val glossaryJson: String?,
    val recommendationsJson: String?,
    val cacheTimestampMillis: Long
)
