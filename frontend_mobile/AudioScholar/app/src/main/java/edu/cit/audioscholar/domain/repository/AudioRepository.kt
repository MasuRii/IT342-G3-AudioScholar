package edu.cit.audioscholar.domain.repository

import android.net.Uri
import kotlinx.coroutines.flow.Flow

sealed class UploadResult {
    data object Success : UploadResult()
    data class Error(val message: String) : UploadResult()
    data class Progress(val percentage: Int) : UploadResult()
    data object Loading : UploadResult()
}

interface AudioRepository {
    fun uploadAudioFile(
        fileUri: Uri,
        title: String?,
        description: String?
    ): Flow<UploadResult>
}