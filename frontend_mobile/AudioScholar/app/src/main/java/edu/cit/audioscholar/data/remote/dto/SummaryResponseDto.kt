package edu.cit.audioscholar.data.remote.dto

import com.google.gson.annotations.SerializedName

data class SummaryResponseDto(
    @SerializedName("summaryId")
    val summaryId: String? = null,

    @SerializedName("recordingId")
    val recordingId: String? = null,

    @SerializedName("keyPoints")
    val keyPoints: List<String>? = null,

    @SerializedName("topics")
    val topics: List<String>? = null,

    @SerializedName("glossary")
    val glossary: List<GlossaryItemDto>? = null,

    @SerializedName("formattedSummaryText")
    val formattedSummaryText: String? = null,

    @SerializedName("createdAt")
    val createdAt: String? = null
)