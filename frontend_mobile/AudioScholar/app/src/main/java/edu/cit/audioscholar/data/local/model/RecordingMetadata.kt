package edu.cit.audioscholar.data.local.model

import androidx.room.*
import edu.cit.audioscholar.data.local.db.Converters
import edu.cit.audioscholar.data.remote.dto.GlossaryItemDto
import edu.cit.audioscholar.data.remote.dto.RecommendationDto

@Entity(tableName = "recording_metadata")
@TypeConverters(Converters::class)
data class RecordingMetadata(

    @PrimaryKey
    val filePath: String,

    val id: String? = null,
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