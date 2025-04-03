package edu.cit.audioscholar.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import edu.cit.audioscholar.data.local.model.RecordingMetadata
import edu.cit.audioscholar.domain.repository.AudioRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LocalRecordingsUiState(
    val isLoading: Boolean = false,
    val recordings: List<RecordingMetadata> = emptyList(),
    val error: String? = null,
    val recordingToDelete: RecordingMetadata? = null
)

@HiltViewModel
class LocalRecordingsViewModel @Inject constructor(
    private val audioRepository: AudioRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LocalRecordingsUiState())
    val uiState: StateFlow<LocalRecordingsUiState> = _uiState.asStateFlow()

    init {
        loadRecordings()
    }

    fun loadRecordings() {
        viewModelScope.launch {
            audioRepository.getLocalRecordings()
                .onStart {
                    _uiState.update { it.copy(isLoading = it.recordings.isEmpty(), error = null) }
                }
                .catch { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Failed to load recordings: ${throwable.message}"
                        )
                    }
                }
                .collect { recordingsList ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            recordings = recordingsList
                        )
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
        _uiState.update { it.copy(recordingToDelete = null, isLoading = true) }

        viewModelScope.launch {
            val success = audioRepository.deleteLocalRecording(recording)
            if (success) {
                loadRecordings()
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
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