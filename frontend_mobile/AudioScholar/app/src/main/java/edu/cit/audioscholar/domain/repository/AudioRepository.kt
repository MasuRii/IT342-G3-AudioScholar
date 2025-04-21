package edu.cit.audioscholar.domain.repository

import android.net.Uri
import edu.cit.audioscholar.data.local.model.RecordingMetadata
import edu.cit.audioscholar.data.remote.dto.AudioMetadataDto
import edu.cit.audioscholar.data.remote.dto.AuthResponse
import edu.cit.audioscholar.data.remote.dto.FirebaseTokenRequest
import edu.cit.audioscholar.data.remote.dto.GitHubCodeRequest
import edu.cit.audioscholar.data.remote.dto.LoginRequest
import edu.cit.audioscholar.data.remote.dto.RegistrationRequest
import edu.cit.audioscholar.util.Resource
import kotlinx.coroutines.flow.Flow

sealed class UploadResult {
    data object Success : UploadResult()
    data class Error(val message: String) : UploadResult()
    data class Progress(val percentage: Int) : UploadResult()
    data object Loading : UploadResult()
}

typealias AuthResult = Resource<AuthResponse>


interface AudioRepository {
    fun getRecordingMetadata(filePath: String): Flow<Result<RecordingMetadata>>
    fun uploadAudioFile(
        fileUri: Uri,
        title: String?,
        description: String?
    ): Flow<UploadResult>

    fun getLocalRecordings(): Flow<List<RecordingMetadata>>

    suspend fun deleteLocalRecording(metadata: RecordingMetadata): Boolean
    suspend fun deleteLocalRecordings(filePaths: List<String>): Boolean

    fun getCloudRecordings(): Flow<Result<List<AudioMetadataDto>>>

    suspend fun updateRecordingTitle(filePath: String, newTitle: String): Boolean

    suspend fun registerUser(request: RegistrationRequest): AuthResult

    suspend fun loginUser(request: LoginRequest): Resource<AuthResponse>

    suspend fun verifyFirebaseToken(request: FirebaseTokenRequest): Resource<AuthResponse>

    suspend fun verifyGoogleToken(request: FirebaseTokenRequest): Resource<AuthResponse>

    suspend fun verifyGitHubCode(request: GitHubCodeRequest): Resource<AuthResponse>
}