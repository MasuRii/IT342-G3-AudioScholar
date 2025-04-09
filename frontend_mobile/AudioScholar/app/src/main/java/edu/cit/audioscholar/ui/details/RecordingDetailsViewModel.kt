package edu.cit.audioscholar.ui.details

import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import edu.cit.audioscholar.R
import edu.cit.audioscholar.data.local.model.RecordingMetadata
import edu.cit.audioscholar.domain.repository.AudioRepository
import edu.cit.audioscholar.service.PlaybackManager
import edu.cit.audioscholar.service.PlaybackState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject


private fun formatDurationMillis(durationMillis: Long): String {
    if (durationMillis <= 0) return "00:00"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMillis) % 60
    return String.format("%02d:%02d", minutes, seconds)
}

private fun formatTimestampMillis(timestampMillis: Long): String {
    if (timestampMillis <= 0) return "Unknown date"
    val date = Date(timestampMillis)
    val format = SimpleDateFormat("MMM dd, yyyy, hh:mm a", Locale.getDefault())
    return format.format(date)
}


@HiltViewModel
class RecordingDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val audioRepository: AudioRepository,
    private val playbackManager: PlaybackManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecordingDetailsUiState())
    val uiState: StateFlow<RecordingDetailsUiState> = _uiState.asStateFlow()

    private val _triggerFilePicker = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val triggerFilePicker: SharedFlow<Unit> = _triggerFilePicker.asSharedFlow()

    private val recordingId: String = savedStateHandle.get<String>("recordingId") ?: ""
    private val decodedFilePath: String = Uri.decode(recordingId)

    init {
        Log.d("DetailsViewModel", "Initializing for recordingId: $recordingId, decodedPath: $decodedFilePath")
        if (decodedFilePath.isNotEmpty()) {
            loadRecordingDetails()
            loadMockRecommendations()
            fetchMockSummaryAndNotes()
            observePlaybackState()
        } else {
            _uiState.update { it.copy(isLoading = false, error = "Recording ID not found.") }
        }
    }

    private fun loadRecordingDetails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            audioRepository.getRecordingMetadata(decodedFilePath)
                .catch { e ->
                    Log.e("DetailsViewModel", "Error loading details for $decodedFilePath", e)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Failed to load recording details: ${e.message}"
                        )
                    }
                }
                .collect { result ->
                    result.onSuccess { metadata ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                title = metadata.title ?: metadata.fileName,
                                dateCreated = formatTimestampMillis(metadata.timestampMillis),
                                durationMillis = metadata.durationMillis,
                                durationFormatted = formatDurationMillis(metadata.durationMillis),
                                filePath = metadata.filePath,
                                error = null
                            )
                        }
                    }.onFailure { e ->
                        Log.e("DetailsViewModel", "Failed result loading details for $decodedFilePath", e)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = "Failed to load recording details: ${e.message}"
                            )
                        }
                    }
                }
        }
    }

    private fun observePlaybackState() {
        playbackManager.playbackState
            .onEach { playbackState ->
                _uiState.update { currentState ->
                    val currentDuration = if (playbackState.totalDurationMs > 0) playbackState.totalDurationMs else currentState.durationMillis
                    val progress = if (currentDuration > 0) (playbackState.currentPositionMs.toFloat() / currentDuration).coerceIn(0f, 1f) else 0f

                    currentState.copy(
                        isPlaying = playbackState.isPlaying,
                        currentPositionMillis = playbackState.currentPositionMs,
                        durationMillis = currentDuration,
                        durationFormatted = formatDurationMillis(currentDuration),
                        currentPositionFormatted = formatDurationMillis(playbackState.currentPositionMs),
                        playbackProgress = progress,
                        error = playbackState.error ?: if(playbackState.isPlaying || !currentState.isPlaying) null else currentState.error
                    )
                }
                if(playbackState.error != null) {
                    playbackManager.consumeError()
                }
            }.launchIn(viewModelScope)
    }


    private fun loadMockRecommendations() {
        viewModelScope.launch {
            delay(500)
            _uiState.update {
                it.copy(
                    youtubeRecommendations = listOf(
                        MockYouTubeVideo("vid1", R.drawable.ic_youtubeplaceholder_quantum, "Introduction to Quantum Mechanics"),
                        MockYouTubeVideo("vid2", R.drawable.ic_youtubeplaceholder_superposition, "Superposition Explained Simply"),
                        MockYouTubeVideo("vid3", R.drawable.ic_youtubeplaceholder_entanglement, "The Mystery of Entanglement")
                    )
                )
            }
        }
    }

    private fun fetchMockSummaryAndNotes() {
        viewModelScope.launch {
            _uiState.update { it.copy(summaryStatus = SummaryStatus.PROCESSING, summaryText = "AI summary is being generated...", aiNotesText = "") }
            delay(3000)
            val success = Math.random() > 0.3
            if (success) {
                _uiState.update { it.copy(
                    summaryStatus = SummaryStatus.READY,
                    summaryText = "This lecture covered the main topics of quantum physics, including superposition and entanglement. Key formulas were discussed, and potential applications in computing were highlighted.",
                    aiNotesText = """
                        - Quantum Superposition: Particles exist in multiple states at once.
                        - Entanglement: Linked particles share the same fate, regardless of distance.
                        - Key Formula: Schrödinger equation (Hψ = Eψ).
                        - Applications: Quantum computing, cryptography.
                    """.trimIndent()
                ) }
            } else {
                _uiState.update { it.copy(
                    summaryStatus = SummaryStatus.FAILED,
                    summaryText = "Failed to generate summary. Please try again later.",
                    aiNotesText = ""
                ) }
            }
        }
    }

    fun onPlayPauseToggle() {
        val currentState = _uiState.value
        val newIsPlaying = !currentState.isPlaying

        if (currentState.filePath.isEmpty()) {
            Log.w("DetailsViewModel", "Play/Pause toggle attempted but file path is empty.")
            _uiState.update { it.copy(error = "Cannot play: File path not loaded.") }
            return
        }

        if (newIsPlaying) {
            if (playbackManager.playbackState.value.totalDurationMs <= 0) {
                Log.d("DetailsViewModel", "Player not ready or needs preparation. Calling prepareAndPlay.")
                playbackManager.prepareAndPlay(currentState.filePath)
            } else {
                Log.d("DetailsViewModel", "Player seems ready. Calling play.")
                playbackManager.play()
            }
        } else {
            Log.d("DetailsViewModel", "Calling pause.")
            playbackManager.pause()
        }
    }

    fun onSeek(progress: Float) {
        val currentDuration = playbackManager.playbackState.value.totalDurationMs.takeIf { it > 0 } ?: _uiState.value.durationMillis
        if (currentDuration <= 0) {
            Log.w("DetailsViewModel", "Seek attempted but duration is unknown.")
            return
        }
        val newPositionMillis = (progress * currentDuration).toLong()
        playbackManager.seekTo(newPositionMillis)
        _uiState.update {
            it.copy(
                currentPositionMillis = newPositionMillis,
                currentPositionFormatted = formatDurationMillis(newPositionMillis),
                playbackProgress = progress
            )
        }
    }

    fun onCopySummaryAndNotes() {
        val state = _uiState.value
        if (state.summaryStatus == SummaryStatus.READY) {
            val combinedText = "Summary:\n${state.summaryText}\n\nAI Notes:\n${state.aiNotesText}"
            Log.d("DetailsViewModel", "Copy Summary & Notes clicked. Text prepared.")
            _uiState.update { it.copy(textToCopy = combinedText, infoMessage = "Summary and notes ready to copy.") }
        } else {
            Log.w("DetailsViewModel", "Copy attempt failed: Summary not ready.")
            _uiState.update { it.copy(error = "Summary and notes are not available to copy.") }
        }
    }

    fun consumeTextToCopy() {
        _uiState.update { it.copy(textToCopy = null) }
    }

    fun requestAttachPowerPoint() {
        Log.d("DetailsViewModel", "Attach PowerPoint requested. Triggering file picker.")
        viewModelScope.launch {
            _triggerFilePicker.tryEmit(Unit)
        }
    }

    fun setAttachedPowerPointFile(fileName: String?) {
        if (fileName != null) {
            Log.d("DetailsViewModel", "Setting attached PowerPoint: $fileName")
            _uiState.update { it.copy(attachedPowerPoint = fileName, infoMessage = "PowerPoint attached: $fileName") }
        } else {
            _uiState.update { it.copy(error = "Failed to select PowerPoint file.") }
        }
    }

    fun detachPowerPoint() {
        Log.d("DetailsViewModel", "Detach PowerPoint clicked.")
        _uiState.update { it.copy(attachedPowerPoint = null, infoMessage = "PowerPoint detached.") }
    }


    fun onWatchYouTubeVideo(video: MockYouTubeVideo) {
        Log.d("DetailsViewModel", "Watch YouTube clicked: ${video.title}")
        _uiState.update { it.copy(infoMessage = "Mock: Opening video ${video.title}") }
    }

    fun requestDelete() {
        _uiState.update { it.copy(showDeleteConfirmation = true) }
    }

    fun cancelDelete() {
        _uiState.update { it.copy(showDeleteConfirmation = false) }
    }

    fun confirmDelete() {
        playbackManager.pause()
        _uiState.update { it.copy(showDeleteConfirmation = false, isLoading = true) }
        viewModelScope.launch {
            if (decodedFilePath.isEmpty()) {
                Log.e("DetailsViewModel", "Cannot delete: File path is empty.")
                _uiState.update { it.copy(isLoading = false, error = "Cannot delete recording: file path unknown.") }
                return@launch
            }

            val metadataToDelete = RecordingMetadata(
                id = 0,
                filePath = decodedFilePath,
                fileName = File(decodedFilePath).name,
                title = _uiState.value.title,
                timestampMillis = 0,
                durationMillis = _uiState.value.durationMillis
            )

            val success = audioRepository.deleteLocalRecording(metadataToDelete)

            if (success) {
                Log.d("DetailsViewModel", "Deletion successful for: $decodedFilePath")
                playbackManager.releasePlayer()
            } else {
                Log.e("DetailsViewModel", "Error deleting file: $decodedFilePath")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to delete recording."
                    )
                }
            }
        }
    }

    fun consumeError() {
        _uiState.update { it.copy(error = null) }
    }
    fun consumeInfoMessage() {
        _uiState.update { it.copy(infoMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        playbackManager.releasePlayer()
        Log.d("DetailsViewModel", "ViewModel cleared, player released.")
    }
}