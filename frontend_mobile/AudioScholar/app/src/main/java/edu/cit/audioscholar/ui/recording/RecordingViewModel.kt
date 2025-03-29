// File: java/edu/cit/audioscholar/ui/recording/RecordingViewModel.kt

package edu.cit.audioscholar.ui.recording

import android.Manifest // Required for permission constant
import android.app.Application
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import edu.cit.audioscholar.data.local.file.RecordingFileHandler // Import your handler
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

// --- UI State Data Class ---
data class RecordingUiState(
    val isRecording: Boolean = false,
    val elapsedTimeMillis: Long = 0L,
    val elapsedTimeFormatted: String = "00:00:00",
    val permissionGranted: Boolean = false,
    val requiresPermissionRationale: Boolean = false,
    val error: String? = null,
    val recordingFilePath: String? = null // To show confirmation/path after saving
)

@HiltViewModel
class RecordingViewModel @Inject constructor(
    // Inject Application context (needed for MediaRecorder on S+ and permissions)
    private val application: Application,
    // Inject your RecordingFileHandler
    private val recordingFileHandler: RecordingFileHandler
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecordingUiState())
    val uiState: StateFlow<RecordingUiState> = _uiState.asStateFlow()

    // MediaRecorder instance
    private var mediaRecorder: MediaRecorder? = null
    private var currentRecordingFile: File? = null // Keep track of the file being written

    // Timer variables
    private var timerJob: Job? = null
    private var recordingStartTime: Long = 0L

    init {
        checkInitialPermission()
    }

    // --- Permission Handling ---

    private fun checkInitialPermission() {
        val granted = ContextCompat.checkSelfPermission(
            application,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        _uiState.update { it.copy(permissionGranted = granted) }
        println("DEBUG: Initial permission granted: $granted")
    }

    // Call this from your Composable after requesting permission
    fun onPermissionResult(granted: Boolean, shouldShowRationale: Boolean) {
        _uiState.update {
            it.copy(
                permissionGranted = granted,
                requiresPermissionRationale = !granted && shouldShowRationale,
                // Clear permission-related error if granted, otherwise set permanent denial message if needed
                error = if (granted) null else if (!shouldShowRationale) "Audio permission permanently denied. Please enable it in settings." else it.error
            )
        }
        println("DEBUG: Permission result - Granted: $granted, Rationale: $shouldShowRationale")
    }

    // --- Recording Control ---

    fun toggleRecording() {
        if (_uiState.value.isRecording) {
            stopRecording()
        } else {
            // Clear previous errors/file paths before attempting to start
            // Keep permission status as is
            _uiState.update { it.copy(error = null, recordingFilePath = null) }
            startRecording()
        }
    }

    private fun startRecording() {
        if (!_uiState.value.permissionGranted) {
            // Call non-suspend handleError
            handleError("Microphone permission required to start recording.")
            println("DEBUG: Start recording blocked - permission not granted.")
            // The UI should prompt the user to grant permission via the button's logic
            return
        }

        // Check if already recording (safety check)
        if (_uiState.value.isRecording) {
            // Call non-suspend handleError
            handleError("Recording already in progress.")
            return
        }

        println("DEBUG: ViewModel - Attempting to start recording...")
        viewModelScope.launch(Dispatchers.IO) { // Use IO dispatcher for file/media operations
            try {
                // Create MediaRecorder instance
                val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(application)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }

                // Use RecordingFileHandler to configure and get the output file
                recordingFileHandler.setupMediaRecorderOutputFile(recorder)
                    .onSuccess { outputFile ->
                        currentRecordingFile = outputFile // Store the file reference
                        mediaRecorder = recorder // Store the recorder instance

                        // Prepare and start the recorder
                        recorder.prepare()
                        recorder.start()

                        // Update UI state on the main thread
                        withContext(Dispatchers.Main) {
                            _uiState.update {
                                it.copy(
                                    isRecording = true,
                                    elapsedTimeMillis = 0L, // Reset timer display
                                    elapsedTimeFormatted = formatElapsedTime(0L),
                                    error = null, // Clear previous errors
                                    recordingFilePath = null // Clear previous file path
                                )
                            }
                            startTimer() // Start the timer on the main thread
                            println("DEBUG: ViewModel - Recording started successfully. File: ${outputFile.absolutePath}")
                        }
                    }
                    .onFailure { exception ->
                        // Handle errors from setupMediaRecorderOutputFile
                        // Call non-suspend handleError
                        handleError("Failed to setup recording: ${exception.message}", exception)
                        releaseMediaRecorder() // Clean up recorder if setup failed
                    }

            } catch (e: IOException) {
                // Call non-suspend handleError
                handleError("MediaRecorder prepare() failed: ${e.message}", e)
                releaseMediaRecorder()
            } catch (e: IllegalStateException) {
                // Call non-suspend handleError
                handleError("MediaRecorder start() failed: ${e.message}", e)
                releaseMediaRecorder()
            } catch (e: SecurityException) {
                // Call non-suspend handleError
                handleError("Security error during recording setup. Check permissions.", e)
                releaseMediaRecorder()
            } catch (e: Exception) {
                // Call non-suspend handleError
                handleError("An unexpected error occurred during startRecording: ${e.message}", e)
                releaseMediaRecorder()
            }
        }
    }

    private fun stopRecording() {
        if (!_uiState.value.isRecording) {
            // Call non-suspend handleError
            handleError("No recording in progress to stop.") // A1 Stop Error
            return
        }

        println("DEBUG: ViewModel - Attempting to stop recording...")
        // Stop the timer first (runs on Main thread)
        stopTimer()

        viewModelScope.launch(Dispatchers.IO) { // Use IO for potential file finalization
            try {
                mediaRecorder?.apply {
                    stop()
                    // release() is called in releaseMediaRecorder()
                }
                val savedFilePath = currentRecordingFile?.absolutePath
                println("DEBUG: ViewModel - Recording stopped successfully. File saved: $savedFilePath")

                // Update UI state on the main thread
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            isRecording = false,
                            recordingFilePath = savedFilePath, // Show the path
                            error = null // Clear any previous transient errors
                        )
                    }
                }
                // TODO: Handle Insufficient Local Storage Error on Save (A2 Stop)
                // This typically manifests as an IOException during prepare() or sometimes stop().

            } catch (e: IllegalStateException) {
                // Call non-suspend handleError
                handleError("Failed to stop MediaRecorder properly: ${e.message}", e)
                // State update (isRecording=false) is handled within handleError now
            } catch (e: RuntimeException) { // stop() can throw RuntimeException
                // Call non-suspend handleError
                handleError("Runtime error stopping recording: ${e.message}", e)
            } catch (e: Exception) {
                // Call non-suspend handleError
                handleError("An unexpected error occurred during stopRecording: ${e.message}", e)
            } finally {
                // Ensure recorder is released regardless of success/failure
                releaseMediaRecorder()
                // Reset timer display after stopping and releasing (needs Main thread)
                withContext(Dispatchers.Main) { resetTimerDisplay() }
            }
        }
    }

    // --- Timer Logic ---

    private fun startTimer() {
        stopTimer() // Ensure any previous timer is stopped
        recordingStartTime = System.currentTimeMillis()
        timerJob = viewModelScope.launch(Dispatchers.Main) { // Timer updates UI, run on Main
            println("DEBUG: ViewModel - Timer Started")
            while (isActive) { // Coroutine scope manages cancellation
                if (!_uiState.value.isRecording) {
                    println("DEBUG: ViewModel - Timer loop exiting (isRecording is false)")
                    break // Exit loop if recording stopped externally
                }
                val elapsedMillis = System.currentTimeMillis() - recordingStartTime
                // Update state only if still recording
                if (_uiState.value.isRecording) {
                    _uiState.update {
                        it.copy(
                            elapsedTimeMillis = elapsedMillis,
                            elapsedTimeFormatted = formatElapsedTime(elapsedMillis)
                        )
                    }
                }
                delay(1000L) // Update every second
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
        // Only reset if not currently recording
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

    // --- Helper Functions ---

    private fun releaseMediaRecorder() {
        println("DEBUG: Attempting to release MediaRecorder.")
        mediaRecorder?.apply {
            try {
                // No need to call stop() again here
                // reset() // Optional: Usually not needed if creating a new instance each time
                release()
                println("DEBUG: MediaRecorder released successfully.")
            } catch (e: Exception) {
                System.err.println("Error releasing MediaRecorder: ${e.message}")
                e.printStackTrace()
            }
        }
        mediaRecorder = null // Nullify the reference
        currentRecordingFile = null // Nullify file reference
    }

    /**
     * Handles errors by logging and updating the UI state on the Main thread.
     * Sets isRecording to false when an error occurs during recording operations.
     */
    private fun handleError(message: String, throwable: Throwable? = null) {
        System.err.println("RecordingViewModel Error: $message") // Log to console/Logcat
        throwable?.printStackTrace()
        // Launch a coroutine on the Main dispatcher to update the UI state
        viewModelScope.launch(Dispatchers.Main) {
            _uiState.update { it.copy(error = message, isRecording = false) } // Stop recording state on error
        }
    }

    // --- Lifecycle ---

    // Ensure resources are released if ViewModel is destroyed
    override fun onCleared() {
        super.onCleared()
        if (_uiState.value.isRecording) {
            println("WARN: ViewModel cleared while recording was active. Attempting cleanup.")
            stopTimer() // Stop timer immediately
            releaseMediaRecorder() // Release recorder
        }
        println("DEBUG: ViewModel - onCleared")
    }
}