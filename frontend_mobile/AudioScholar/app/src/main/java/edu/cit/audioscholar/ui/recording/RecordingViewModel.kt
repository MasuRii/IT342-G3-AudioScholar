package edu.cit.audioscholar.ui.recording

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import edu.cit.audioscholar.data.local.file.RecordingFileHandler
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class RecordingUiState(
    val isRecording: Boolean = false,
    val elapsedTimeMillis: Long = 0L,
    val elapsedTimeFormatted: String = "00:00:00",
    val permissionGranted: Boolean = false,
    val requiresPermissionRationale: Boolean = false,
    val error: String? = null,
    val recordingFilePath: String? = null
)

@HiltViewModel
class RecordingViewModel @Inject constructor(
    private val application: Application,
    private val recordingFileHandler: RecordingFileHandler
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecordingUiState())
    val uiState: StateFlow<RecordingUiState> = _uiState.asStateFlow()

    private var mediaRecorder: MediaRecorder? = null
    private var currentRecordingFile: File? = null

    private var timerJob: Job? = null
    private var recordingStartTime: Long = 0L

    init {
        checkInitialPermission()
    }

    private fun checkInitialPermission() {
        val granted = ContextCompat.checkSelfPermission(
            application,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        _uiState.update { it.copy(permissionGranted = granted) }
        println("DEBUG: Initial permission granted: $granted")
    }

    fun onPermissionResult(granted: Boolean, shouldShowRationale: Boolean) {
        _uiState.update {
            it.copy(
                permissionGranted = granted,
                requiresPermissionRationale = !granted && shouldShowRationale,
                error = if (granted) null else if (!shouldShowRationale && !granted) "Audio permission permanently denied. Please enable it in settings." else it.error
            )
        }
        println("DEBUG: Permission result - Granted: $granted, Rationale: $shouldShowRationale")
    }


    fun toggleRecording() {
        if (_uiState.value.isRecording) {
            stopRecording()
        } else {
            _uiState.update { it.copy(error = null, recordingFilePath = null) }
            startRecording()
        }
    }

    private fun startRecording() {
        if (!_uiState.value.permissionGranted) {
            handleError("Microphone permission required to start recording.")
            return
        }
        if (_uiState.value.isRecording) {
            handleError("Recording already in progress.")
            return
        }

        println("DEBUG: ViewModel - Attempting to start recording...")
        viewModelScope.launch(Dispatchers.IO) {
            var recorderInstance: MediaRecorder? = null
            try {
                recorderInstance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(application)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }

                recordingFileHandler.setupMediaRecorderOutputFile(recorderInstance)
                    .onSuccess { outputFile ->
                        println("DEBUG: File handler setup successful.")

                        mediaRecorder = recorderInstance
                        currentRecordingFile = outputFile

                        recorderInstance.prepare()
                        println("DEBUG: MediaRecorder prepared.")
                        recorderInstance.start()
                        println("DEBUG: MediaRecorder started.")

                        withContext(Dispatchers.Main) {
                            _uiState.update {
                                it.copy(
                                    isRecording = true,
                                    elapsedTimeMillis = 0L,
                                    elapsedTimeFormatted = formatElapsedTime(0L),
                                    error = null,
                                    recordingFilePath = null
                                )
                            }
                            startTimer()
                            println("DEBUG: ViewModel - Recording started successfully on UI thread. File: ${outputFile.absolutePath}")
                        }
                    }
                    .onFailure { exception ->
                        handleError("Failed to setup recording file: ${exception.message}", exception)
                        recorderInstance?.release()
                    }

            } catch (e: IOException) {
                handleError("MediaRecorder setup failed (IO): ${e.message}", e)
                recorderInstance?.release()
                mediaRecorder = null
                currentRecordingFile = null
            } catch (e: IllegalStateException) {
                handleError("MediaRecorder state error during start: ${e.message}", e)
                recorderInstance?.release()
                mediaRecorder = null
                currentRecordingFile = null
            } catch (e: SecurityException) {
                handleError("Security error during recording setup. Check permissions.", e)
                recorderInstance?.release()
                mediaRecorder = null
                currentRecordingFile = null
            } catch (e: Exception) {
                handleError("An unexpected error occurred during startRecording: ${e.message}", e)
                recorderInstance?.release()
                mediaRecorder = null
                currentRecordingFile = null
            }
        }
    }

    private fun stopRecording() {
        if (!_uiState.value.isRecording) {
            println("DEBUG: Stop recording called but not currently recording.")
            return
        }

        println("DEBUG: ViewModel - Attempting to stop recording...")
        stopTimer()

        viewModelScope.launch(Dispatchers.IO) {
            var savedFilePath: String? = null
            try {
                mediaRecorder?.apply {
                    stop()
                }
                savedFilePath = currentRecordingFile?.absolutePath
                println("DEBUG: ViewModel - Recording stopped successfully. File saved: $savedFilePath")

                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            isRecording = false,
                            recordingFilePath = savedFilePath,
                            error = null
                        )
                    }
                }
            } catch (e: IOException) {
                handleError("Failed to save recording. Insufficient storage space or I/O error.", e)
            } catch (e: IllegalStateException) {
                handleError("Failed to stop MediaRecorder properly: ${e.message}", e)
            } catch (e: RuntimeException) {
                handleError("Runtime error stopping recording: ${e.message}", e)
            } catch (e: Exception) {
                handleError("An unexpected error occurred during stopRecording: ${e.message}", e)
            } finally {
                releaseMediaRecorder()
                withContext(Dispatchers.Main) { resetTimerDisplay() }
            }
        }
    }

    private fun startTimer() {
        stopTimer()
        recordingStartTime = System.currentTimeMillis()
        timerJob = viewModelScope.launch(Dispatchers.Main) {
            println("DEBUG: ViewModel - Timer Started")
            while (isActive && _uiState.value.isRecording) {
                val elapsedMillis = System.currentTimeMillis() - recordingStartTime
                if (_uiState.value.isRecording) {
                    _uiState.update {
                        it.copy(
                            elapsedTimeMillis = elapsedMillis,
                            elapsedTimeFormatted = formatElapsedTime(elapsedMillis)
                        )
                    }
                } else {
                    println("DEBUG: ViewModel - Timer loop exiting (isRecording became false)")
                    break
                }
                delay(1000L)
            }
            println("DEBUG: ViewModel - Timer loop finished.")
        }
    }


    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        println("DEBUG: ViewModel - Timer Stopped/Cancelled")
    }

    private fun resetTimerDisplay() {
        if (!_uiState.value.isRecording) {
            _uiState.update {
                it.copy(
                    elapsedTimeMillis = 0L,
                    elapsedTimeFormatted = formatElapsedTime(0L)
                )
            }
            println("DEBUG: ViewModel - Timer Display Reset")
        }
    }

    private fun formatElapsedTime(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun releaseMediaRecorder() {
        println("DEBUG: Attempting to release MediaRecorder.")
        mediaRecorder?.apply {
            try {
                reset()
                release()
                println("DEBUG: MediaRecorder reset and released successfully.")
            } catch (e: Exception) {
                System.err.println("Error releasing MediaRecorder: ${e.message}")
                e.printStackTrace()
            }
        }
        mediaRecorder = null
        currentRecordingFile = null
    }

    private fun handleError(message: String, throwable: Throwable? = null) {
        System.err.println("RecordingViewModel Error: $message")
        throwable?.printStackTrace()
        viewModelScope.launch(Dispatchers.Main) {
            _uiState.update { it.copy(error = message, isRecording = false) }
            stopTimer()
            resetTimerDisplay()
        }
    }

    fun consumeSavedMessage() {
        _uiState.update { it.copy(recordingFilePath = null) }
        println("DEBUG: Consumed saved message.")
    }

    fun consumeErrorMessage() {
        _uiState.update { it.copy(error = null) }
        println("DEBUG: Consumed error message.")
    }

    override fun onCleared() {
        super.onCleared()
        if (_uiState.value.isRecording) {
            println("WARN: ViewModel cleared while recording was active. Attempting cleanup.")
            stopTimer()
            releaseMediaRecorder()
        }
        println("DEBUG: ViewModel - onCleared")
    }
}