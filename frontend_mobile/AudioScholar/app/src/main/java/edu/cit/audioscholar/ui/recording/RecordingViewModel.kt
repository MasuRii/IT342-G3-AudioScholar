package edu.cit.audioscholar.ui.recording

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import edu.cit.audioscholar.data.local.file.RecordingFileHandler
import edu.cit.audioscholar.data.local.model.RecordingMetadata
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.FileOutputStream
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
    val recordingFilePath: String? = null,
    val showTitleDialog: Boolean = false
)


@HiltViewModel
class RecordingViewModel @Inject constructor(
    private val application: Application,
    private val recordingFileHandler: RecordingFileHandler,
    private val gson: Gson
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecordingUiState())
    val uiState: StateFlow<RecordingUiState> = _uiState.asStateFlow()

    private var mediaRecorder: MediaRecorder? = null
    private var currentRecordingFile: File? = null
    private var finalDurationMillis: Long = 0L

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
            requestStopRecording()
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
                                    recordingFilePath = null,
                                    showTitleDialog = false
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

    private fun requestStopRecording() {
        if (!_uiState.value.isRecording) {
            println("DEBUG: Stop recording requested but not currently recording.")
            return
        }

        println("DEBUG: ViewModel - Requesting stop recording...")
        stopTimer()
        finalDurationMillis = _uiState.value.elapsedTimeMillis

        viewModelScope.launch(Dispatchers.IO) {
            try {
                mediaRecorder?.apply {
                    stop()
                    println("DEBUG: MediaRecorder stopped.")
                }

                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            isRecording = false,
                            showTitleDialog = true
                        )
                    }
                    println("DEBUG: ViewModel - Show title dialog requested.")
                }
            } catch (e: IllegalStateException) {
                handleError("Failed to stop MediaRecorder properly: ${e.message}", e)
                releaseMediaRecorder()
                withContext(Dispatchers.Main) { resetTimerDisplay() }
            } catch (e: RuntimeException) {
                handleError("Runtime error stopping recording: ${e.message}", e)
                releaseMediaRecorder()
                withContext(Dispatchers.Main) { resetTimerDisplay() }
            } catch (e: Exception) {
                handleError("An unexpected error occurred during requestStopRecording: ${e.message}", e)
                releaseMediaRecorder()
                withContext(Dispatchers.Main) { resetTimerDisplay() }
            }
        }
    }

    fun finalizeRecording(title: String?) {
        val savedAudioFile = currentRecordingFile ?: run {
            handleError("Cannot finalize recording: Recording file is null.")
            releaseMediaRecorder()
            _uiState.update { it.copy(showTitleDialog = false) }
            return
        }
        val savedFilePath = savedAudioFile.absolutePath
        val savedFileName = savedAudioFile.name
        val timestamp = System.currentTimeMillis()

        val metadata = RecordingMetadata(
            filePath = savedFilePath,
            fileName = savedFileName,
            title = title?.takeIf { it.isNotBlank() },
            timestampMillis = timestamp,
            durationMillis = finalDurationMillis
        )

        println("DEBUG: ViewModel - Finalizing recording...")
        Log.d("RecordingMetadata", "Preparing Metadata: $metadata")

        viewModelScope.launch(Dispatchers.IO) {
            var metadataSavedSuccessfully = false
            try {
                val metadataJson = gson.toJson(metadata)
                val metadataFileName = savedFileName.substringBeforeLast('.') + ".json"
                val metadataFile = File(savedAudioFile.parent, metadataFileName)

                FileOutputStream(metadataFile).use { outputStream ->
                    outputStream.write(metadataJson.toByteArray())
                }
                metadataSavedSuccessfully = true
                Log.d("RecordingMetadata", "Metadata saved successfully to: ${metadataFile.absolutePath}")

            } catch (e: IOException) {
                Log.e("RecordingMetadata", "Failed to save metadata JSON file", e)
            } catch (e: Exception) {
                Log.e("RecordingMetadata", "Unexpected error saving metadata JSON", e)
            }

            releaseMediaRecorder()
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        recordingFilePath = savedFilePath,
                        showTitleDialog = false,
                        error = null
                    )
                }
                resetTimerDisplay()
                println("DEBUG: ViewModel - Recording finalized. File: $savedFilePath, Title: '${metadata.title ?: "None"}', Metadata Saved: $metadataSavedSuccessfully")
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
        if (!_uiState.value.isRecording && !_uiState.value.showTitleDialog) {
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
                release()
                println("DEBUG: MediaRecorder released successfully.")
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
        if (mediaRecorder != null) {
            releaseMediaRecorder()
        }
        viewModelScope.launch(Dispatchers.Main) {
            _uiState.update { it.copy(error = message, isRecording = false, showTitleDialog = false) }
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
        if (mediaRecorder != null) {
            println("WARN: ViewModel cleared while MediaRecorder might be active or unreleased. Attempting cleanup.")
            stopTimer()
            releaseMediaRecorder()
        }
        println("DEBUG: ViewModel - onCleared")
    }
}