package edu.cit.audioscholar.domain.repository

import android.net.Uri
import edu.cit.audioscholar.data.local.model.RecordingMetadata
import edu.cit.audioscholar.data.remote.dto.AudioMetadataDto
import kotlinx.coroutines.flow.Flow

sealed class UploadResult {
    data object Success : UploadResult()
    data class Error(val message: String) : UploadResult()
    data class Progress(val percentage: Int) : UploadResult()
    data object Loading : UploadResult()
}

interface AudioRepository {
    fun getRecordingMetadata(filePath: String): Flow<Result<RecordingMetadata>>
    fun uploadAudioFile(
        fileUri: Uri,
        title: String?,
        description: String?
    ): Flow<UploadResult>

    fun getLocalRecordings(): Flow<List<RecordingMetadata>>

    suspend fun deleteLocalRecording(metadata: RecordingMetadata): Boolean

    fun getCloudRecordings(): Flow<Result<List<AudioMetadataDto>>>
}