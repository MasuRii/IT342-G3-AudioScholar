package edu.cit.audioscholar.data.remote.dto

import com.google.gson.annotations.SerializedName

data class RecommendationDto(
    @SerializedName("recommendationId")
    val recommendationId: String? = null,

    @SerializedName("videoId")
    val videoId: String? = null,

    @SerializedName("title")
    val title: String? = null,

    @SerializedName("descriptionSnippet")
    val descriptionSnippet: String? = null,

    @SerializedName("thumbnailUrl")
    val thumbnailUrl: String? = null,

    @SerializedName("recordingId")
    val recordingId: String? = null,

    @SerializedName("createdAt")
    val createdAt: String? = null
)