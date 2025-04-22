package edu.cit.audioscholar.ui.library

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import edu.cit.audioscholar.data.local.model.RecordingMetadata
import edu.cit.audioscholar.data.remote.dto.AudioMetadataDto
import edu.cit.audioscholar.domain.repository.LocalAudioRepository
import edu.cit.audioscholar.domain.repository.RemoteAudioRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val isLoadingLocal: Boolean = false,
    val isLoadingCloud: Boolean = false,
    val localRecordings: List<RecordingMetadata> = emptyList(),
    val cloudRecordings: List<AudioMetadataDto> = emptyList(),
    val error: String? = null,
    val hasAttemptedCloudLoad: Boolean = false,

    val isMultiSelectActive: Boolean = false,
    val selectedRecordingIds: Set<String> = emptySet(),
    val showMultiDeleteConfirmation: Boolean = false
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val localAudioRepository: LocalAudioRepository,
    private val remoteAudioRepository: RemoteAudioRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

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
                    Log.d("LibraryViewModel", "Starting local recordings load")
                    _uiState.update { it.copy(isLoadingLocal = true, error = null) }
                }
                .catch { throwable ->
                    Log.e("LibraryViewModel", "Error loading local recordings", throwable)
                    _uiState.update {
                        it.copy(
                            isLoadingLocal = false,
                            error = it.error ?: "Failed to load local recordings: ${throwable.message}"
                        )
                    }
                }
                .collect { recordingsList ->
                    Log.d("LibraryViewModel", "Collected ${recordingsList.size} local recordings")
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
            if (success) {
                Log.i("LibraryViewModel", "Multi-delete successful via repository.")
                _uiState.update { it.copy(isMultiSelectActive = false, selectedRecordingIds = emptySet()) }
                loadLocalRecordings()
            } else {
                Log.e("LibraryViewModel", "Multi-delete failed via repository.")
                _uiState.update {
                    it.copy(
                        isLoadingLocal = false,
                        error = "Failed to delete some or all selected recordings.",
                        isMultiSelectActive = false,
                        selectedRecordingIds = emptySet()
                    )
                }
            }
        }
    }

    fun consumeError() {
        _uiState.update { it.copy(error = null) }
    }
}