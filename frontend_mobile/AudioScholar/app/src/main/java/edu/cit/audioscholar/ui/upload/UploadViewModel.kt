package edu.cit.audioscholar.ui.upload

import android.app.Application
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import edu.cit.audioscholar.R
import edu.cit.audioscholar.data.local.file.RecordingFileHandler
import edu.cit.audioscholar.data.local.model.RecordingMetadata
import edu.cit.audioscholar.domain.repository.RemoteAudioRepository
import edu.cit.audioscholar.domain.repository.UploadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
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
    private val remoteAudioRepository: RemoteAudioRepository,
    private val recordingFileHandler: RecordingFileHandler,
    private val gson: Gson
) : ViewModel() {

    companion object {
        private const val TAG = "UploadViewModel"
        private val SUPPORTED_MIME_TYPES = setOf(
            "audio/wav", "audio/x-wav",
            "audio/mp3", "audio/mpeg",
            "audio/aiff", "audio/x-aiff",
            "audio/aac", "audio/mp4",
            "audio/ogg",
            "audio/flac", "audio/x-flac"
        )
        private const val MAX_FILE_SIZE_BYTES = 500 * 1024 * 1024
        private const val FILENAME_DATE_FORMAT = "yyyy-MM-dd_HH-mm-ss"
        private const val FILENAME_PREFIX = "Recording_"
        private const val FILENAME_EXTENSION_METADATA = ".json"
    }

    fun consumeUploadMessage() {
        _uiState.update { it.copy(uploadMessage = null) }
    }

    private val _uiState = MutableStateFlow(UploadScreenState())
    val uiState: StateFlow<UploadScreenState> = _uiState.asStateFlow()

    fun onSelectFileClicked() {
        Log.d(TAG, "Select File Clicked - Requesting UI to launch picker")
        _uiState.update { it.copy(uploadMessage = null, progress = 0, isUploadEnabled = false, selectedFileName = null, selectedFileUri = null, title = "", description = "") }
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

                Log.d(TAG, "File Selected: Name=$fileName, Size=$fileSize, Type=$fileMimeType, Uri=$uri")

                val validationError = validateFile(uri, fileMimeType, fileSize)

                _uiState.update {
                    it.copy(
                        selectedFileName = fileName,
                        selectedFileUri = uri,
                        uploadMessage = validationError,
                        progress = 0,
                        isUploadEnabled = validationError == null,
                        title = "",
                        description = ""
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error getting file details: ${e.message}", e)
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
            Log.d(TAG, "File selection cancelled.")
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
            Log.w(TAG, "Validation Error: Unsupported MIME Type: $mimeType")
            return application.getString(R.string.upload_error_unsupported_format, mimeType ?: "unknown")
        }

        if (size == null) {
            Log.w(TAG, "Validation Warning: Could not determine file size for Uri $uri.")
        } else {
            if (size == 0L) {
                Log.e(TAG, "Validation Error: File appears to be empty (0 bytes). Uri: $uri")
                return application.getString(R.string.upload_error_empty_file)
            }
            if (size > MAX_FILE_SIZE_BYTES) {
                Log.e(TAG, "Validation Error: File size ($size bytes) exceeds limit ($MAX_FILE_SIZE_BYTES bytes). Uri: $uri")
                val sizeInMB = size / (1024.0 * 1024.0)
                val maxSizeInMB = MAX_FILE_SIZE_BYTES / (1024.0 * 1024.0)
                return application.getString(R.string.upload_error_size_exceeded, String.format("%.2f", sizeInMB), String.format("%.1f", maxSizeInMB))
            }
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
            Log.e(TAG, "Upload Error: No file URI present.")
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
                Log.e(TAG, "Upload Error: Pre-upload validation failed - $validationError")
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error re-validating file details before upload: ${e.message}", e)
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
            Log.i(TAG, "Upload Info: Upload already in progress.")
            return
        }

        Log.i(TAG, "Upload Clicked: Starting upload for file: ${currentState.selectedFileName} (Uri: $currentUri)")
        Log.d(TAG, "With Title: '$currentTitle', Description: '$currentDescription'")

        val uriToUpload = currentUri

        remoteAudioRepository.uploadAudioFile(
            fileUri = uriToUpload,
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
                        Log.v(TAG, "Upload Progress: ${result.percentage}%")
                        _uiState.update {
                            it.copy(
                                isUploading = true,
                                progress = result.percentage,
                                uploadMessage = application.getString(R.string.upload_info_progress, result.percentage)
                            )
                        }
                    }
                    is UploadResult.Success -> {
                        Log.i(TAG, "Upload Successful for Uri: $uriToUpload")
                        viewModelScope.launch {
                            var localCopySuccess = false
                            var savedFile: File? = null

                            Log.d(TAG, "Attempting to save a local copy of the uploaded audio file...")
                            val copyResult = withContext(Dispatchers.IO) {
                                recordingFileHandler.copyUriToLocalRecordings(uriToUpload)
                            }
                            copyResult.onSuccess { file ->
                                Log.i(TAG, "Successfully saved local audio copy at: ${file.absolutePath}")
                                localCopySuccess = true
                                savedFile = file
                            }.onFailure { exception ->
                                Log.e(TAG, "Failed to save local copy of uploaded audio file.", exception)
                            }

                            if (localCopySuccess && savedFile != null) {
                                Log.d(TAG, "Attempting to save local metadata for the copied file...")
                                val metadataSaveResult = withContext(Dispatchers.IO) {
                                    saveMetadataForFile(savedFile!!, currentTitle.takeIf { it.isNotEmpty() })
                                }
                                metadataSaveResult.onSuccess {
                                    Log.i(TAG, "Successfully saved local metadata JSON.")
                                }.onFailure { exception ->
                                    Log.e(TAG, "Failed to save local metadata JSON.", exception)
                                }
                            }

                            _uiState.update {
                                it.copy(
                                    isUploading = false,
                                    uploadMessage = application.getString(R.string.upload_success_message),
                                    selectedFileName = null,
                                    selectedFileUri = null,
                                    title = "",
                                    description = "",
                                    progress = 100,
                                    isUploadEnabled = false
                                )
                            }
                        }
                    }
                    is UploadResult.Error -> {
                        Log.e(TAG, "Upload Error: ${result.message}")
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

    private suspend fun saveMetadataForFile(audioFile: File, titleFromUi: String?): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val fileName = audioFile.name
            val filePath = audioFile.absolutePath

            val baseNameWithTimestamp = fileName.substringBeforeLast('.')
            val timestampString = baseNameWithTimestamp.removePrefix(FILENAME_PREFIX)
            val dateFormat = SimpleDateFormat(FILENAME_DATE_FORMAT, Locale.US)
            val timestampMillis = try {
                dateFormat.parse(timestampString)?.time ?: audioFile.lastModified()
            } catch (e: ParseException) {
                Log.w(TAG, "Could not parse timestamp from filename: $fileName for metadata", e)
                audioFile.lastModified()
            }

            var durationMillis = 0L
            var retriever: MediaMetadataRetriever? = null
            try {
                retriever = MediaMetadataRetriever()
                retriever.setDataSource(filePath)
                durationMillis = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get duration for metadata file: $fileName", e)
            } finally {
                try {
                    retriever?.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing MediaMetadataRetriever in saveMetadataForFile", e)
                }
            }

            val metadata = RecordingMetadata(
                id = timestampMillis,
                filePath = filePath,
                fileName = fileName,
                title = titleFromUi,
                timestampMillis = timestampMillis,
                durationMillis = durationMillis
            )

            val metadataJson = gson.toJson(metadata)
            val metadataFileName = baseNameWithTimestamp + FILENAME_EXTENSION_METADATA
            val metadataFile = File(audioFile.parent, metadataFileName)

            FileOutputStream(metadataFile).use { outputStream ->
                outputStream.write(metadataJson.toByteArray())
            }
            Result.success(Unit)
        } catch (e: IOException) {
            Log.e(TAG, "IOException saving metadata JSON for $audioFile", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error saving metadata JSON for $audioFile", e)
            Result.failure(e)
        }
    }
}