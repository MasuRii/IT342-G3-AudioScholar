package edu.cit.audioscholar.ui.recording

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

data class RecordingUiState(
    val isRecording: Boolean = false,
    val elapsedTimeMillis: Long = 0L,
    val elapsedTimeFormatted: String = "00:00:00"
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

        _uiState.update { it.copy(isRecording = true) }
        startTimer()
        println("DEBUG: ViewModel - Start Recording Action")
    }

    private fun stopRecording() {

        _uiState.update { it.copy(isRecording = false) }
        stopTimer()
        resetTimerDisplay()
        println("DEBUG: ViewModel - Stop Recording Action")
    }

    private fun startTimer() {
        stopTimer()
        recordingStartTime = System.currentTimeMillis()
        timerJob = viewModelScope.launch(Dispatchers.Main) {
            while (isActive) {
                val elapsedMillis = System.currentTimeMillis() - recordingStartTime
                _uiState.update {
                    it.copy(
                        elapsedTimeMillis = elapsedMillis,
                        elapsedTimeFormatted = formatElapsedTime(elapsedMillis)
                    )
                }
                delay(1000L)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        println("DEBUG: ViewModel - Timer Stopped")
    }

    private fun resetTimerDisplay() {
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
        stopTimer()
        println("DEBUG: ViewModel - onCleared")
    }
}