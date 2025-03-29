package edu.cit.audioscholar.ui.recording // Adjust package name as needed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.TimeUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

// Define a simple UI state data class (optional but good practice)
data class RecordingUiState(
    val isRecording: Boolean = false,
    val elapsedTimeMillis: Long = 0L,
    val elapsedTimeFormatted: String = "00:00:00"
    // Add other state properties here later (e.g., error messages, save status)
)

@HiltViewModel
class RecordingViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(RecordingUiState())
    val uiState: StateFlow<RecordingUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var recordingStartTime: Long = 0L

    fun toggleRecording() {
        val currentState = _uiState.value.isRecording
        if (currentState) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        // TODO: Add actual MediaRecorder start logic here later
        // TODO: Handle permissions check before starting

        _uiState.update { it.copy(isRecording = true) }
        startTimer()
        println("DEBUG: ViewModel - Start Recording Action") // Temporary Log
    }

    private fun stopRecording() {
        // TODO: Add actual MediaRecorder stop & save logic here later

        _uiState.update { it.copy(isRecording = false) }
        stopTimer()
        // Optionally reset timer immediately or wait for save confirmation
        resetTimerDisplay()
        println("DEBUG: ViewModel - Stop Recording Action") // Temporary Log
    }

    private fun startTimer() {
        stopTimer() // Ensure any existing timer is stopped
        recordingStartTime = System.currentTimeMillis()
        timerJob = viewModelScope.launch(Dispatchers.Main) { // Use Main dispatcher for UI updates
            while (isActive) { // Loop while the coroutine is active
                val elapsedMillis = System.currentTimeMillis() - recordingStartTime
                _uiState.update {
                    it.copy(
                        elapsedTimeMillis = elapsedMillis,
                        elapsedTimeFormatted = formatElapsedTime(elapsedMillis)
                    )
                }
                delay(1000L) // Update every second
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        println("DEBUG: ViewModel - Timer Stopped") // Temporary Log
    }

    private fun resetTimerDisplay() {
        // Reset the timer part of the state after stopping
        _uiState.update {
            it.copy(
                elapsedTimeMillis = 0L,
                elapsedTimeFormatted = formatElapsedTime(0L)
            )
        }
    }

    private fun formatElapsedTime(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    override fun onCleared() {
        super.onCleared()
        stopTimer() // Ensure timer is stopped if ViewModel is cleared
        // TODO: Add cleanup logic if recording is in progress when ViewModel is cleared (e.g., discard recording)
        println("DEBUG: ViewModel - onCleared") // Temporary Log
    }
}