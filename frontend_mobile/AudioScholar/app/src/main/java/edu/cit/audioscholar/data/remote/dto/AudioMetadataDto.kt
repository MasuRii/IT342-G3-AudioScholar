package edu.cit.audioscholar.data.remote.dto

data class TimestampDto(
    val seconds: Long? = null,
    val nanos: Long? = null
)

data class AudioMetadataDto(
    val id: String? = null,
    val userId: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val contentType: String? = null,
    val title: String? = null,
    val description: String? = null,
    val nhostFileId: String? = null,
    val storageUrl: String? = null,
    val uploadTimestamp: TimestampDto? = null
)