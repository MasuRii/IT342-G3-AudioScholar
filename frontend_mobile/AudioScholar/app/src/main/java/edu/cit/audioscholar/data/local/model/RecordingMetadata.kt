package edu.cit.audioscholar.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import edu.cit.audioscholar.data.local.db.Converters
import edu.cit.audioscholar.data.remote.dto.GlossaryItemDto
import edu.cit.audioscholar.data.remote.dto.RecommendationDto

@Entity(tableName = "recording_metadata")
data class RecordingMetadata(

    @PrimaryKey
    val filePath: String,

    val fileName: String,
    val title: String?,
    val description: String? = null,
    val timestampMillis: Long,
    val durationMillis: Long,
    val remoteRecordingId: String? = null,

    val cachedSummaryText: String?,
    val cachedGlossaryItems: List<GlossaryItemDto>?,
    val cachedRecommendations: List<RecommendationDto>?,
    val cacheTimestampMillis: Long?
)