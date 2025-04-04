package edu.cit.audioscholar.ui.library

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import edu.cit.audioscholar.data.local.model.RecordingMetadata
import edu.cit.audioscholar.data.remote.dto.AudioMetadataDto
import edu.cit.audioscholar.domain.repository.AudioRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val isLoadingLocal: Boolean = false,
    val isLoadingCloud: Boolean = false,
    val localRecordings: List<RecordingMetadata> = emptyList(),
    val cloudRecordings: List<AudioMetadataDto> = emptyList(),
    val error: String? = null,
    val recordingToDelete: RecordingMetadata? = null,
    val hasAttemptedCloudLoad: Boolean = false
)

@HiltViewModel
class LocalRecordingsViewModel @Inject constructor(
    private val audioRepository: AudioRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        loadLocalRecordings()
    }

    fun loadLocalRecordingsOnResume() {
        Log.d("LocalRecordingsVM", "loadLocalRecordingsOnResume called")
        loadLocalRecordings()
    }

    private fun loadLocalRecordings() {
        viewModelScope.launch {
            audioRepository.getLocalRecordings()
                .onStart {
                    Log.d("LocalRecordingsVM", "Starting local recordings load")
                    _uiState.update { it.copy(isLoadingLocal = true, error = null) }
                }
                .catch { throwable ->
                    Log.e("LocalRecordingsVM", "Error loading local recordings", throwable)
                    _uiState.update {
                        it.copy(
                            isLoadingLocal = false,
                            error = it.error ?: "Failed to load local recordings: ${throwable.message}"
                        )
                    }
                }
                .collect { recordingsList ->
                    Log.d("LocalRecordingsVM", "Collected ${recordingsList.size} local recordings")
                    _uiState.update {
                        it.copy(
                            isLoadingLocal = false,
                            localRecordings = recordingsList
                        )
                    }
                }
        }
    }

    fun triggerCloudLoadIfNeeded() {
        val currentState = _uiState.value
        if (!currentState.isLoadingCloud && !currentState.hasAttemptedCloudLoad) {
            Log.d("LocalRecordingsVM", "triggerCloudLoadIfNeeded: Condition met, loading cloud recordings.")
            loadCloudRecordings()
        } else {
            Log.d("LocalRecordingsVM", "triggerCloudLoadIfNeeded: Condition not met (isLoadingCloud=${currentState.isLoadingCloud}, hasAttemptedCloudLoad=${currentState.hasAttemptedCloudLoad}). Skipping load.")
        }
    }

    fun forceRefreshCloudRecordings() {
        Log.d("LocalRecordingsVM", "forceRefreshCloudRecordings called.")
        _uiState.update { it.copy(isLoadingCloud = true, hasAttemptedCloudLoad = false, error = null) }
        loadCloudRecordings()
    }

    private fun loadCloudRecordings() {
        viewModelScope.launch {
            audioRepository.getCloudRecordings()
                .onStart {
                    Log.d("LocalRecordingsVM", "Starting cloud recordings load (actual fetch)")
                    _uiState.update { it.copy(isLoadingCloud = true, hasAttemptedCloudLoad = true) }
                }
                .catch { throwable ->
                    Log.e("LocalRecordingsVM", "Error loading cloud recordings (catch)", throwable)
                    _uiState.update {
                        it.copy(
                            isLoadingCloud = false,
                            error = it.error ?: "Failed to load cloud recordings: ${throwable.message}"
                        )
                    }
                }
                .collect { result ->
                    result.onSuccess { metadataList ->
                        Log.d("LocalRecordingsVM", "Collected ${metadataList.size} cloud recordings")
                        _uiState.update {
                            it.copy(
                                isLoadingCloud = false,
                                cloudRecordings = metadataList.sortedByDescending { dto -> dto.uploadTimestamp?.seconds }
                            )
                        }
                    }.onFailure { throwable ->
                        Log.e("LocalRecordingsVM", "Error loading cloud recordings (onFailure)", throwable)
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


    fun requestDeleteConfirmation(metadata: RecordingMetadata) {
        _uiState.update { it.copy(recordingToDelete = metadata) }
    }

    fun cancelDelete() {
        _uiState.update { it.copy(recordingToDelete = null) }
    }

    fun confirmDelete() {
        val recording = _uiState.value.recordingToDelete ?: return
        _uiState.update { it.copy(recordingToDelete = null, isLoadingLocal = true) }

        viewModelScope.launch {
            val success = audioRepository.deleteLocalRecording(recording)
            if (success) {
                loadLocalRecordings()
            } else {
                _uiState.update {
                    it.copy(
                        isLoadingLocal = false,
                        error = "Failed to delete '${recording.fileName}'"
                    )
                }
            }
        }
    }

    fun consumeError() {
        _uiState.update { it.copy(error = null) }
    }
}