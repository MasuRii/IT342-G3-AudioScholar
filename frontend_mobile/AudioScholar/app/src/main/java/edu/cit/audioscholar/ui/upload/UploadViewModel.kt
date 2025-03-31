package edu.cit.audioscholar.ui.upload

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import edu.cit.audioscholar.R
import edu.cit.audioscholar.domain.repository.AudioRepository
import edu.cit.audioscholar.domain.repository.UploadResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import java.util.Locale
import javax.inject.Inject

data class UploadScreenState(
    val selectedFileName: String? = null,
    val selectedFileUri: Uri? = null,
    val title: String = "",
    val description: String = "",
    val isUploading: Boolean = false,
    val uploadMessage: String? = null,
    val progress: Int = 0,
    val isUploadEnabled: Boolean = false
)

@HiltViewModel
class UploadViewModel @Inject constructor(
    private val application: Application,
    private val audioRepository: AudioRepository
) : ViewModel() {

    companion object {
        private val SUPPORTED_MIME_TYPES = setOf(
            "audio/wav", "audio/x-wav",
            "audio/mp3", "audio/mpeg",
            "audio/aiff", "audio/x-aiff",
            "audio/aac", "audio/mp4",
            "audio/ogg",
            "audio/flac", "audio/x-flac"
        )
        private const val MAX_FILE_SIZE_BYTES = 500 * 1024 * 1024
    }

    fun consumeUploadMessage() {
        _uiState.update { it.copy(uploadMessage = null) }
    }

    private val _uiState = MutableStateFlow(UploadScreenState())
    val uiState: StateFlow<UploadScreenState> = _uiState.asStateFlow()

    fun onSelectFileClicked() {
        println("Select File Clicked - Requesting UI to launch picker")
        _uiState.update { it.copy(uploadMessage = null, progress = 0) }
    }

    fun onFileSelected(uri: Uri?) {
        if (uri != null) {
            var fileName: String? = null
            var fileSize: Long? = null
            var fileMimeType: String? = null

            try {
                application.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            fileName = cursor.getString(nameIndex)
                        }
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                            fileSize = cursor.getLong(sizeIndex)
                        }
                    }
                }
                if (fileName == null) {
                    fileName = uri.path?.substringAfterLast('/') ?: "Unknown File"
                }
                fileMimeType = application.contentResolver.getType(uri)

                println("File Selected: Name=$fileName, Size=$fileSize, Type=$fileMimeType, Uri=$uri")

                val validationError = validateFile(uri, fileMimeType, fileSize)

                _uiState.update {
                    it.copy(
                        selectedFileName = fileName,
                        selectedFileUri = uri,
                        uploadMessage = validationError,
                        progress = 0,
                        isUploadEnabled = validationError == null,
                    )
                }

            } catch (e: Exception) {
                println("Error getting file details: ${e.message}")
                _uiState.update {
                    it.copy(
                        selectedFileName = "Error",
                        selectedFileUri = uri,
                        uploadMessage = application.getString(R.string.upload_error_read_details),
                        progress = 0,
                        isUploadEnabled = false,
                        title = "",
                        description = ""
                    )
                }
            }
        } else {
            println("File selection cancelled.")
            _uiState.update {
                it.copy(
                    selectedFileName = null,
                    selectedFileUri = null,
                    uploadMessage = null,
                    progress = 0,
                    isUploadEnabled = false
                )
            }
        }
    }

    private fun validateFile(uri: Uri?, mimeType: String?, size: Long?): String? {
        if (uri == null) {
            return application.getString(R.string.upload_error_no_file)
        }

        val lowerCaseMimeType = mimeType?.lowercase(Locale.ROOT)
        if (lowerCaseMimeType == null || lowerCaseMimeType !in SUPPORTED_MIME_TYPES) {
            println("Validation Error: Unsupported MIME Type: $mimeType")
            return application.getString(R.string.upload_error_unsupported_format, mimeType ?: "unknown")
        }

        if (size == null) {
            println("Validation Warning: Could not determine file size.")
            return application.getString(R.string.upload_error_determine_size)
        }
        if (size == 0L) {
            println("Validation Error: File appears to be empty.")
            return application.getString(R.string.upload_error_empty_file)
        }
        if (size > MAX_FILE_SIZE_BYTES) {
            println("Validation Error: File size ($size bytes) exceeds limit ($MAX_FILE_SIZE_BYTES bytes).")
            val sizeInMB = size / (1024.0 * 1024.0)
            val maxSizeInMB = MAX_FILE_SIZE_BYTES / (1024.0 * 1024.0)
            return application.getString(R.string.upload_error_size_exceeded, sizeInMB, maxSizeInMB)
        }

        return null
    }

    fun onTitleChanged(newTitle: String) {
        _uiState.update { it.copy(title = newTitle) }
    }

    fun onDescriptionChanged(newDescription: String) {
        _uiState.update { it.copy(description = newDescription) }
    }

    fun onUploadClicked() {
        val currentState = _uiState.value
        val currentUri = currentState.selectedFileUri
        val currentTitle = currentState.title.trim()
        val currentDescription = currentState.description.trim()

        if (currentUri == null) {
            _uiState.update { it.copy(uploadMessage = application.getString(R.string.upload_error_no_file)) }
            println("Upload Error: No file URI present.")
            return
        }

        var fileSize: Long? = null
        var fileMimeType: String? = null
        try {
            application.contentResolver.query(currentUri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }
            }
            fileMimeType = application.contentResolver.getType(currentUri)

            val validationError = validateFile(currentUri, fileMimeType, fileSize)
            if (validationError != null) {
                _uiState.update { it.copy(uploadMessage = validationError, isUploading = false) }
                println("Upload Error: Validation failed - $validationError")
                return
            }

        } catch (e: Exception) {
            println("Error re-validating file details before upload: ${e.message}")
            _uiState.update {
                it.copy(
                    uploadMessage = application.getString(R.string.upload_error_reverify_details),
                    isUploading = false,
                    isUploadEnabled = false
                )
            }
            return
        }

        if (currentState.isUploading) {
            println("Upload Info: Upload already in progress.")
            return
        }

        println("Upload Clicked: Starting upload for file: ${currentState.selectedFileName} (Uri: $currentUri)")
        println("With Title: '$currentTitle', Description: '$currentDescription'")

        audioRepository.uploadAudioFile(
            fileUri = currentUri,
            title = currentTitle.takeIf { it.isNotEmpty() },
            description = currentDescription.takeIf { it.isNotEmpty() }
        )
            .onEach { result ->
                when (result) {
                    is UploadResult.Loading -> {
                        _uiState.update {
                            it.copy(
                                isUploading = true,
                                uploadMessage = application.getString(R.string.upload_info_starting),
                                progress = 0
                            )
                        }
                    }
                    is UploadResult.Progress -> {
                        println("VM: Received Progress: ${result.percentage}%")
                        _uiState.update {
                            it.copy(
                                isUploading = true,
                                progress = result.percentage,
                                uploadMessage = "Uploading: ${result.percentage}%"
                            )
                        }
                    }
                    is UploadResult.Success -> {
                        println("VM: Upload Successful")
                        _uiState.update {
                            it.copy(
                                isUploading = false,
                                uploadMessage = application.getString(R.string.upload_success_message),
                                selectedFileName = null,
                                selectedFileUri = null,
                                title = "",
                                description = "",
                                progress = 0,
                                isUploadEnabled = false
                            )
                        }
                    }
                    is UploadResult.Error -> {
                        println("VM: Upload Error: ${result.message}")
                        _uiState.update {
                            it.copy(
                                isUploading = false,
                                uploadMessage = application.getString(R.string.upload_error_generic_prefix, result.message),
                                progress = 0,
                                isUploadEnabled = true
                            )
                        }
                    }
                }
            }
            .launchIn(viewModelScope)
    }
}