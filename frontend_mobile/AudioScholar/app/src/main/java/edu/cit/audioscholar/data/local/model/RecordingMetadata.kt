package edu.cit.audioscholar.data.local.model

data class RecordingMetadata(
    val id: Long = 0,
    val filePath: String,
    val fileName: String,
    val title: String?,
    val timestampMillis: Long,
    val durationMillis: Long
)