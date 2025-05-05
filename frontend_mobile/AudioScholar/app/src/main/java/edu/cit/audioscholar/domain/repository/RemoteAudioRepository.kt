package edu.cit.audioscholar.domain.repository

import edu.cit.audioscholar.data.remote.dto.*
import kotlinx.coroutines.flow.Flow
import java.io.File

sealed class UploadResult {
    data class Success(val metadata: AudioMetadataDto?) : UploadResult()
    data class Error(val message: String) : UploadResult()
    data class Progress(val percentage: Int) : UploadResult()
    data object Loading : UploadResult()
}

interface RemoteAudioRepository {
    fun uploadAudioFile(
        audioFile: File,
        powerpointFile: File? = null,
        title: String?,
        description: String?
    ): Flow<UploadResult>

    fun getCloudRecordings(): Flow<Result<List<AudioMetadataDto>>>

    fun getSummary(recordingId: String): Flow<Result<SummaryResponseDto>>

    fun getRecommendations(recordingId: String): Flow<Result<List<RecommendationDto>>>

    fun getCloudRecordingDetails(recordingId: String): Flow<Result<AudioMetadataDto>>

    fun deleteCloudRecording(metadataId: String): Flow<Result<Unit>>

}