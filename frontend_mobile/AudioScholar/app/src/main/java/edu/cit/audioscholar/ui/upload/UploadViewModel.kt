package edu.cit.audioscholar.ui.upload

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import edu.cit.audioscholar.domain.repository.AudioRepository
import edu.cit.audioscholar.domain.repository.UploadResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class UploadScreenState(
    val selectedFileName: String? = null,
    val selectedFileUri: Uri? = null,
    val isUploading: Boolean = false,
    val uploadMessage: String? = null,
    val progress: Int = 0
)

@HiltViewModel
class UploadViewModel @Inject constructor(
    private val application: Application,
    private val audioRepository: AudioRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UploadScreenState())
    val uiState: StateFlow<UploadScreenState> = _uiState.asStateFlow()

    fun onSelectFileClicked() {
        println("Select File Clicked - Requesting UI to launch picker")
        _uiState.update { it.copy(uploadMessage = null, progress = 0) }
    }

    fun onFileSelected(uri: Uri?) {
        if (uri != null) {
            var fileName: String? = null
            application.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex)
                    }
                }
            }
            if (fileName == null) {
                fileName = uri.path?.substringAfterLast('/') ?: "Unknown File"
            }

            _uiState.update {
                it.copy(
                    selectedFileName = fileName,
                    selectedFileUri = uri,
                    uploadMessage = null,
                    progress = 0
                )
            }
            println("File Selected: $fileName, Uri: $uri")
        } else {
            println("File selection cancelled.")
        }
    }


    fun onUploadClicked() {
        val currentUri = _uiState.value.selectedFileUri
        if (currentUri == null) {
            _uiState.update { it.copy(uploadMessage = "Error: No file selected.") }
            return
        }
        if (_uiState.value.isUploading) {
            return
        }

        println("Upload Clicked for file: ${_uiState.value.selectedFileName} (Uri: $currentUri)")

        audioRepository.uploadAudioFile(currentUri)
            .onEach { result ->
                when (result) {
                    is UploadResult.Loading -> {
                        _uiState.update {
                            it.copy(
                                isUploading = true,
                                uploadMessage = "Starting upload...",
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
                        _uiState.update {
                            it.copy(
                                isUploading = false,
                                uploadMessage = "Upload successful!",
                                selectedFileName = null,
                                selectedFileUri = null,
                                progress = 0
                            )
                        }
                    }
                    is UploadResult.Error -> {
                        _uiState.update {
                            it.copy(
                                isUploading = false,
                                uploadMessage = "Error: ${result.message}",
                                progress = 0
                            )
                        }
                    }
                }
            }
            .launchIn(viewModelScope)
    }
}