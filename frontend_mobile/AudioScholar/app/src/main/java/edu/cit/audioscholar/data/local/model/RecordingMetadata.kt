package edu.cit.audioscholar.data.local.model

import edu.cit.audioscholar.data.remote.dto.GlossaryItemDto
import edu.cit.audioscholar.data.remote.dto.RecommendationDto

data class RecordingMetadata(
    val id: Long = 0,
    val filePath: String,
    val fileName: String,
    val title: String?,
    val description: String? = null,
    val timestampMillis: Long,
    val durationMillis: Long,
    val remoteRecordingId: String? = null,

    val cachedSummaryText: String? = null,
    val cachedGlossaryItems: List<GlossaryItemDto>? = null,
    val cachedRecommendations: List<RecommendationDto>? = null,
    val cacheTimestampMillis: Long? = null
)