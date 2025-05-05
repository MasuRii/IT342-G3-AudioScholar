package edu.cit.audioscholar.ui.library

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import edu.cit.audioscholar.data.local.file.InsufficientStorageException
import edu.cit.audioscholar.data.local.model.RecordingMetadata
import edu.cit.audioscholar.data.remote.dto.AudioMetadataDto
import edu.cit.audioscholar.domain.repository.LocalAudioRepository
import edu.cit.audioscholar.domain.repository.RemoteAudioRepository
import edu.cit.audioscholar.util.FileUtils
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

@Immutable
data class ImportDialogState(
    val fileUri: Uri,
    val originalFileName: String,
    var title: String = "",
    var description: String = ""
)

data class LibraryUiState(
    val isLoadingLocal: Boolean = false,
    val isLoadingCloud: Boolean = false,
    val localRecordings: List<RecordingMetadata> = emptyList(),
    val cloudRecordings: List<AudioMetadataDto> = emptyList(),
    val error: String? = null,
    val hasAttemptedCloudLoad: Boolean = false,

    val isMultiSelectActive: Boolean = false,
    val selectedRecordingIds: Set<String> = emptySet(),
    val showMultiDeleteConfirmation: Boolean = false,

    val importDialogState: ImportDialogState? = null,
    val isImporting: Boolean = false
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val localAudioRepository: LocalAudioRepository,
    private val remoteAudioRepository: RemoteAudioRepository,
    private val fileUtils: FileUtils
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val _eventChannel = Channel<LibraryViewEvent>()
    val eventFlow = _eventChannel.receiveAsFlow()

    val areAllLocalRecordingsSelected: StateFlow<Boolean> = uiState
        .map { state ->
            state.localRecordings.isNotEmpty() && state.selectedRecordingIds.size == state.localRecordings.size
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    init {
        loadLocalRecordings()
    }

    fun loadLocalRecordingsOnResume() {
        Log.d("LibraryViewModel", "loadLocalRecordingsOnResume called")
        loadLocalRecordings()
    }

    private fun loadLocalRecordings() {
        viewModelScope.launch {
            localAudioRepository.getLocalRecordings()
                .onStart {
                    if (!_uiState.value.isImporting) {
                        Log.d("LibraryViewModel", "Starting local recordings load (isLoadingLocal=true)")
                        _uiState.update { it.copy(isLoadingLocal = true, error = null) }
                    } else {
                        Log.d("LibraryViewModel", "loadLocalRecordings called during import, keeping isLoadingLocal=true")
                        _uiState.update { it.copy(isLoadingLocal = true, error = null) }
                    }
                }
                .catch { throwable ->
                    Log.e("LibraryViewModel", "Error loading local recordings", throwable)
                    _uiState.update {
                        it.copy(
                            isLoadingLocal = false,
                            isImporting = false,
                            error = it.error ?: "Failed to load local recordings: ${throwable.message}"
                        )
                    }
                }
                .collect { recordingsList ->
                    Log.d("LibraryViewModel", "Collected ${recordingsList.size} local recordings")
                    _uiState.update {
                        it.copy(
                            isLoadingLocal = false,
                            localRecordings = recordingsList,
                            isImporting = if(it.isImporting) false else it.isImporting
                        )
                    }
                }
        }
    }

    fun triggerCloudLoadIfNeeded() {
        val currentState = _uiState.value
        if (!currentState.isLoadingCloud && !currentState.hasAttemptedCloudLoad) {
            Log.d("LibraryViewModel", "triggerCloudLoadIfNeeded: Condition met, loading cloud recordings.")
            loadCloudRecordings()
        } else {
            Log.d("LibraryViewModel", "triggerCloudLoadIfNeeded: Condition not met (isLoadingCloud=${currentState.isLoadingCloud}, hasAttemptedCloudLoad=${currentState.hasAttemptedCloudLoad}). Skipping load.")
        }
    }

    fun forceRefreshCloudRecordings() {
        Log.d("LibraryViewModel", "forceRefreshCloudRecordings called.")
        _uiState.update { it.copy(isLoadingCloud = true, hasAttemptedCloudLoad = false, error = null) }
        loadCloudRecordings()
    }

    private fun loadCloudRecordings() {
        viewModelScope.launch {
            remoteAudioRepository.getCloudRecordings()
                .onStart {
                    Log.d("LibraryViewModel", "Starting cloud recordings load (actual fetch)")
                    _uiState.update { it.copy(isLoadingCloud = true, hasAttemptedCloudLoad = true) }
                }
                .catch { throwable ->
                    Log.e("LibraryViewModel", "Error loading cloud recordings (catch)", throwable)
                    _uiState.update {
                        it.copy(
                            isLoadingCloud = false,
                            error = it.error ?: "Failed to load cloud recordings: ${throwable.message}"
                        )
                    }
                }
                .collect { result ->
                    result.onSuccess { metadataList ->
                        Log.d("LibraryViewModel", "Collected ${metadataList.size} cloud recordings")
                        _uiState.update {
                            it.copy(
                                isLoadingCloud = false,
                                cloudRecordings = metadataList.sortedByDescending { dto -> dto.uploadTimestamp?.seconds ?: 0L }
                            )
                        }
                    }.onFailure { throwable ->
                        Log.e("LibraryViewModel", "Error loading cloud recordings (onFailure)", throwable)
                        _uiState.update {
                            it.copy(
                                isLoadingCloud = false,
                                error = it.error ?: "Failed to load cloud recordings: ${throwable.message}"
                            )
                        }
                    }
                }
        }
    }


    fun enterMultiSelectMode(initialFilePath: String) {
        _uiState.update {
            it.copy(
                isMultiSelectActive = true,
                selectedRecordingIds = setOf(initialFilePath)
            )
        }
        Log.d("LibraryViewModel", "Entered multi-select mode, selected: $initialFilePath")
    }

    fun exitMultiSelectMode() {
        _uiState.update {
            it.copy(
                isMultiSelectActive = false,
                selectedRecordingIds = emptySet()
            )
        }
        Log.d("LibraryViewModel", "Exited multi-select mode")
    }

    fun toggleSelection(filePath: String) {
        _uiState.update { currentState ->
            val currentSelection = currentState.selectedRecordingIds
            val newSelection = if (currentSelection.contains(filePath)) {
                currentSelection - filePath
            } else {
                currentSelection + filePath
            }
            val exitMode = newSelection.isEmpty() && currentState.isMultiSelectActive
            currentState.copy(
                selectedRecordingIds = newSelection,
                isMultiSelectActive = !exitMode
            )
        }
        Log.d("LibraryViewModel", "Toggled selection for: $filePath. New selection size: ${_uiState.value.selectedRecordingIds.size}")
    }

    fun selectAllLocal() {
        _uiState.update { currentState ->
            val allIds = currentState.localRecordings.map { it.filePath }.toSet()
            currentState.copy(selectedRecordingIds = allIds)
        }
        Log.d("LibraryViewModel", "Selected all ${_uiState.value.selectedRecordingIds.size} local recordings.")
    }

    fun deselectAllLocal() {
        _uiState.update { it.copy(selectedRecordingIds = emptySet()) }
        Log.d("LibraryViewModel", "Deselected all local recordings.")
    }

    fun requestMultiDeleteConfirmation() {
        if (_uiState.value.selectedRecordingIds.isNotEmpty()) {
            _uiState.update { it.copy(showMultiDeleteConfirmation = true) }
            Log.d("LibraryViewModel", "Requested multi-delete confirmation for ${_uiState.value.selectedRecordingIds.size} items.")
        } else {
            Log.w("LibraryViewModel", "Multi-delete requested but no items selected.")
        }
    }

    fun cancelMultiDelete() {
        _uiState.update { it.copy(showMultiDeleteConfirmation = false) }
        Log.d("LibraryViewModel", "Cancelled multi-delete confirmation.")
    }

    fun confirmMultiDelete() {
        val idsToDelete = _uiState.value.selectedRecordingIds.toList()
        if (idsToDelete.isEmpty()) {
            _uiState.update { it.copy(showMultiDeleteConfirmation = false) }
            return
        }

        _uiState.update { it.copy(showMultiDeleteConfirmation = false, isLoadingLocal = true) }
        Log.d("LibraryViewModel", "Confirming multi-delete for ${idsToDelete.size} items.")

        viewModelScope.launch {
            val success = localAudioRepository.deleteLocalRecordings(idsToDelete)
            _uiState.update { it.copy(isLoadingLocal = false) }
            if (success) {
                Log.i("LibraryViewModel", "Multi-delete successful via repository.")
                _uiState.update { it.copy(isMultiSelectActive = false, selectedRecordingIds = emptySet()) }
                loadLocalRecordings()
            } else {
                Log.e("LibraryViewModel", "Multi-delete failed via repository.")
                _uiState.update {
                    it.copy(
                        error = "Failed to delete some or all selected recordings.",
                        isMultiSelectActive = false,
                        selectedRecordingIds = emptySet()
                    )
                }
                loadLocalRecordings()
            }
        }
    }

    fun onImportAudioClicked() {
        Log.d("LibraryViewModel", "Import audio clicked, sending LaunchMultiFilePicker event.")
        viewModelScope.launch {
            _eventChannel.send(LibraryViewEvent.LaunchMultiFilePicker)
        }
    }

    fun onAudioFilesSelected(uris: List<Uri?>) {
        val validUris = uris.filterNotNull()
        Log.d("LibraryViewModel", "Received ${validUris.size} valid URIs from picker.")

        if (validUris.isEmpty()) {
            Log.w("LibraryViewModel", "No valid URIs selected.")
            return
        }

        if (validUris.size == 1) {
            val uri = validUris.first()
            val originalFileName = fileUtils.getFileName(uri) ?: "Unknown File"
            Log.d("LibraryViewModel", "Single file selected: $uri, name: $originalFileName. Showing import dialog.")
            _uiState.update {
                it.copy(importDialogState = ImportDialogState(fileUri = uri, originalFileName = originalFileName))
            }
        } else {
            Log.d("LibraryViewModel", "Multiple files selected (${validUris.size}). Importing directly.")
            importFiles(validUris)
        }
    }

    fun cancelImportDialog() {
        Log.d("LibraryViewModel", "Import dialog cancelled.")
        _uiState.update { it.copy(importDialogState = null) }
    }

    fun importFiles(uris: List<Uri>, title: String? = null, description: String? = null) {
        if (uris.isEmpty()) return

        _uiState.update { it.copy(isImporting = true, isLoadingLocal = true, importDialogState = null, error = null) }
        Log.d("LibraryViewModel", "Starting import for ${uris.size} files. Title: $title, Desc: $description")

        viewModelScope.launch {
            var successfulImports = 0
            val totalFiles = uris.size
            val failedFiles = mutableListOf<String>()

            for (uri in uris) {
                val originalFileName = fileUtils.getFileName(uri) ?: "file_${System.currentTimeMillis()}"
                try {
                    val fileTitle = if (totalFiles == 1) {
                        title?.takeIf { it.isNotBlank() } ?: fileUtils.getFileNameWithoutExtension(originalFileName)
                    } else {
                        fileUtils.getFileNameWithoutExtension(originalFileName)
                    }

                    Log.d("LibraryViewModel", "Importing file: $originalFileName with calculated title: '$fileTitle'")

                    val result = localAudioRepository.importAudioFile(
                        sourceUri = uri,
                        originalFileName = originalFileName,
                        title = fileTitle,
                        description = if (totalFiles == 1) description else null
                    )

                    if (result.isSuccess) {
                        successfulImports++
                        Log.i("LibraryViewModel", "Successfully imported: $originalFileName")
                    } else {
                        val error = result.exceptionOrNull()?.message ?: "Unknown error"
                        Log.e("LibraryViewModel", "Failed to import $originalFileName: $error")
                        failedFiles.add(originalFileName)
                    }
                } catch (e: Exception) {
                    Log.e("LibraryViewModel", "Exception during import of $originalFileName", e)
                    failedFiles.add(originalFileName)
                    val errorMsg = when(e) {
                        is InsufficientStorageException -> "Import failed for $originalFileName: Insufficient storage space."
                        is IOException -> "Import failed for $originalFileName: Could not read or write file."
                        is SecurityException -> "Import failed for $originalFileName: Permission denied."
                        else -> "Import failed for $originalFileName: ${e.message ?: "Unexpected error"}"
                    }
                    _eventChannel.send(LibraryViewEvent.ShowSnackbar(errorMsg))
                }
            }

            val snackbarMessage = when {
                successfulImports == totalFiles && totalFiles > 0 -> "Successfully imported $successfulImports file(s)."
                successfulImports > 0 -> "Imported $successfulImports of $totalFiles files. Failures: ${failedFiles.joinToString()}."
                totalFiles > 0 -> "Import failed for all $totalFiles files. ${failedFiles.joinToString()}"
                else -> "No files were imported."
            }
            if (totalFiles > 0) {
                _eventChannel.send(LibraryViewEvent.ShowSnackbar(snackbarMessage))
            }

            Log.d("LibraryViewModel", "Import process finished. Success: $successfulImports, Failed: ${failedFiles.size}")

            if (successfulImports > 0) {
                Log.d("LibraryViewModel", "Successful imports detected ($successfulImports). Reloading local recordings list.")
                loadLocalRecordings()
            } else {
                Log.d("LibraryViewModel", "No successful imports. Resetting loading/importing state.")
                _uiState.update { it.copy(isLoadingLocal = false, isImporting = false) }
            }
        }
    }

    fun consumeError() {
        _uiState.update { it.copy(error = null) }
    }

    fun onRecordingClicked(recording: Any) {
        if (_uiState.value.isMultiSelectActive) {
            when (recording) {
                is RecordingMetadata -> toggleSelection(recording.filePath)
                is AudioMetadataDto -> {
                    Log.w("LibraryViewModel", "Multi-select toggle attempted on cloud item (not implemented). ID: ${recording.recordingId}")
                }
                else -> Log.w("LibraryViewModel", "onRecordingClicked in multi-select mode called with unknown type: ${recording::class.java}")
            }
        } else {
            viewModelScope.launch {
                when (recording) {
                    is RecordingMetadata -> {
                        Log.d("LibraryViewModel", "Local recording clicked: ${recording.filePath}")
                        _eventChannel.send(LibraryViewEvent.NavigateToLocalDetails(recording.filePath))
                    }
                    is AudioMetadataDto -> {
                        val primaryId = recording.id
                        val recordingIdForDetails = recording.recordingId

                        if (!primaryId.isNullOrBlank() && recordingIdForDetails != null) {
                            Log.d("LibraryViewModel", "Cloud recording clicked: PrimaryID=${primaryId}, RecordingID=${recordingIdForDetails}, Title=${recording.title}")
                            _eventChannel.send(
                                LibraryViewEvent.NavigateToCloudDetails(
                                    id = primaryId,
                                    recordingId = recordingIdForDetails,
                                    title = recording.title ?: recording.fileName,
                                    fileName = recording.fileName ?: "Unknown Filename",
                                    timestampSeconds = recording.uploadTimestamp?.seconds,
                                    audioUrl = recording.audioUrl
                                )
                            )
                        } else if (recordingIdForDetails == null) {
                            Log.e("LibraryViewModel", "Cloud recording clicked, but recordingId is null. Title: ${recording.title}")
                            _eventChannel.send(LibraryViewEvent.ShowSnackbar("Cannot open details: Recording ID is missing."))
                        } else {
                            Log.e("LibraryViewModel", "Cloud recording clicked, but primary ID (id field) is null or blank. Cannot proceed with actions requiring it (like delete). Title: ${recording.title}, PrimaryID: '$primaryId'")
                            _eventChannel.send(LibraryViewEvent.ShowSnackbar("Cannot open details: Primary recording identifier is missing."))
                        }
                    }
                    else -> {
                        Log.w("LibraryViewModel", "onRecordingClicked called with unknown type: ${recording::class.java}")
                        _eventChannel.send(LibraryViewEvent.ShowSnackbar("Cannot open details for this item type."))
                    }
                }
            }
        }
    }
}

sealed class LibraryViewEvent {
    object LaunchMultiFilePicker : LibraryViewEvent()
    data class ShowSnackbar(val message: String) : LibraryViewEvent()
    data class NavigateToLocalDetails(val filePath: String) : LibraryViewEvent()
    data class NavigateToCloudDetails(
        val id: String,
        val recordingId: String,
        val title: String?,
        val fileName: String,
        val timestampSeconds: Long?,
        val audioUrl: String?
    ) : LibraryViewEvent()
}