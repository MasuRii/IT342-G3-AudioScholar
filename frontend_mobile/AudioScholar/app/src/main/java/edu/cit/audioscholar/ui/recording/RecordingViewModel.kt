package edu.cit.audioscholar.ui.recording

import android.Manifest
import android.app.Application
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import edu.cit.audioscholar.data.local.model.RecordingMetadata
import edu.cit.audioscholar.service.RecordingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class RecordingUiState(
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val elapsedTimeMillis: Long = 0L,
    val elapsedTimeFormatted: String = "00:00:00",
    val permissionGranted: Boolean = false,
    val requiresPermissionRationale: Boolean = false,
    val error: String? = null,
    val recordingSavedMessage: String? = null,
    val showTitleDialog: Boolean = false,
    val notificationPermissionGranted: Boolean = true,
    val supportsPauseResume: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N,
    val showStopConfirmationDialog: Boolean = false,
    val showCancelConfirmationDialog: Boolean = false
)

@HiltViewModel
class RecordingViewModel @Inject constructor(
    private val application: Application,
    private val gson: Gson
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecordingUiState())
    val uiState: StateFlow<RecordingUiState> = _uiState.asStateFlow()

    private var finishedRecordingPath: String? = null
    private var finishedRecordingDuration: Long = 0L

    private val localBroadcastManager = LocalBroadcastManager.getInstance(application)
    private val recordingStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == RecordingService.BROADCAST_ACTION_STATUS_UPDATE) {
                val isRecording = intent.getBooleanExtra(RecordingService.EXTRA_IS_RECORDING, false)
                val isPaused = intent.getBooleanExtra(RecordingService.EXTRA_IS_PAUSED, false)
                val elapsedTime = intent.getLongExtra(RecordingService.EXTRA_ELAPSED_TIME_MILLIS, 0L)
                val errorMessage = intent.getStringExtra(RecordingService.EXTRA_ERROR_MESSAGE)
                val finishedPath = intent.getStringExtra(RecordingService.EXTRA_RECORDING_FINISHED_PATH)
                val finishedDuration = intent.getLongExtra(RecordingService.EXTRA_RECORDING_FINISHED_DURATION, 0L)
                val isCancelled = intent.getBooleanExtra(RecordingService.EXTRA_RECORDING_CANCELLED, false)

                Log.d("RecordingViewModel", "Broadcast received: isRecording=$isRecording, isPaused=$isPaused, elapsed=$elapsedTime, error=$errorMessage, finishedPath=$finishedPath, cancelled=$isCancelled")

                val wasStopConfirmationShowing = _uiState.value.showStopConfirmationDialog

                _uiState.update { currentState ->
                    currentState.copy(
                        isRecording = isRecording,
                        isPaused = isPaused,
                        elapsedTimeMillis = elapsedTime,
                        elapsedTimeFormatted = formatElapsedTime(elapsedTime),
                        error = errorMessage ?: if (currentState.error != null && finishedPath == null && !isCancelled) null else currentState.error,
                        showStopConfirmationDialog = if (wasStopConfirmationShowing && isPaused && !currentState.isPaused) true else currentState.showStopConfirmationDialog
                    )
                }

                if (errorMessage != null) {
                    _uiState.update { it.copy(error = errorMessage, showTitleDialog = false, isRecording = false, isPaused = false, showStopConfirmationDialog = false, showCancelConfirmationDialog = false) }
                } else if (isCancelled) {
                    _uiState.update { it.copy(
                        isRecording = false,
                        isPaused = false,
                        showTitleDialog = false,
                        elapsedTimeMillis = 0L,
                        elapsedTimeFormatted = formatElapsedTime(0L),
                        recordingSavedMessage = "Recording cancelled.",
                        showStopConfirmationDialog = false,
                        showCancelConfirmationDialog = false
                    ) }
                } else if (finishedPath != null) {
                    finishedRecordingPath = finishedPath
                    finishedRecordingDuration = finishedDuration
                    _uiState.update { it.copy(showTitleDialog = true, isRecording = false, isPaused = false, showStopConfirmationDialog = false, showCancelConfirmationDialog = false) }
                } else if (!isRecording) {
                    resetTimerDisplayIfNotSaving()
                    if (!uiState.value.showTitleDialog) {
                        _uiState.update { it.copy(showStopConfirmationDialog = false, showCancelConfirmationDialog = false) }
                    }
                }
            }
        }
    }

    init {
        checkInitialPermissions()
        registerReceiver()
    }

    private fun checkInitialPermissions() {
        val audioGranted = ContextCompat.checkSelfPermission(
            application, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                application, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        _uiState.update {
            it.copy(
                permissionGranted = audioGranted,
                notificationPermissionGranted = notificationGranted
            )
        }
        Log.d("RecordingViewModel", "Initial Permissions - Audio: $audioGranted, Notifications: $notificationGranted")
    }

    private fun registerReceiver() {
        val intentFilter = IntentFilter(RecordingService.BROADCAST_ACTION_STATUS_UPDATE)
        localBroadcastManager.registerReceiver(recordingStatusReceiver, intentFilter)
        Log.d("RecordingViewModel", "BroadcastReceiver registered.")
    }

    private fun unregisterReceiver() {
        try {
            localBroadcastManager.unregisterReceiver(recordingStatusReceiver)
            Log.d("RecordingViewModel", "Receiver already unregistered or never registered.")
        } catch (e: IllegalArgumentException) {
            Log.w("RecordingViewModel", "Receiver already unregistered or never registered.")
        }
    }

    fun onPermissionResult(granted: Boolean, permissionType: String) {
        when (permissionType) {
            Manifest.permission.RECORD_AUDIO -> {
                _uiState.update {
                    it.copy(
                        permissionGranted = granted,
                        error = if (granted && it.error?.contains("permission", ignoreCase = true) == true) null else it.error
                    )
                }
                if (!granted) {
                    _uiState.update { it.copy(error = "Audio permission is required to record.") }
                }
            }
            Manifest.permission.POST_NOTIFICATIONS -> {
                _uiState.update { it.copy(notificationPermissionGranted = granted) }
            }
        }
        Log.d("RecordingViewModel", "Permission Result - Type: $permissionType, Granted: $granted")
    }


    fun startRecording() {
        if (_uiState.value.isRecording) {
            Log.w("RecordingViewModel", "Start called but already recording.")
            return
        }
        if (!_uiState.value.permissionGranted) {
            _uiState.update { it.copy(error = "Microphone permission required.") }
            return
        }
        _uiState.update { it.copy(error = null, recordingSavedMessage = null, showStopConfirmationDialog = false, showCancelConfirmationDialog = false) }
        sendServiceCommand(RecordingService.ACTION_START_RECORDING)
    }

    fun requestStopConfirmation() {
        val currentState = _uiState.value
        if (!currentState.isRecording) {
            Log.w("RecordingViewModel", "Stop confirmation requested but not recording.")
            return
        }

        if (!currentState.isPaused && currentState.supportsPauseResume) {
            Log.d("RecordingViewModel", "Pausing recording before showing stop confirmation.")
            sendServiceCommand(RecordingService.ACTION_PAUSE_RECORDING)
        }

        _uiState.update { it.copy(showStopConfirmationDialog = true) }
    }

    fun confirmStopRecording() {
        _uiState.update { it.copy(showStopConfirmationDialog = false) }
        sendServiceCommand(RecordingService.ACTION_STOP_RECORDING)
    }

    fun dismissStopConfirmation() {
        _uiState.update { it.copy(showStopConfirmationDialog = false) }
    }


    fun pauseRecording() {
        val currentState = _uiState.value
        if (currentState.showStopConfirmationDialog || currentState.showCancelConfirmationDialog) {
            Log.w("RecordingViewModel", "Pause called while confirmation dialog is showing.")
            return
        }
        if (!currentState.isRecording || currentState.isPaused) {
            Log.w("RecordingViewModel", "Pause called but not recording or already paused.")
            return
        }
        if (!currentState.supportsPauseResume) {
            _uiState.update { it.copy(error = "Pause/Resume not supported on this Android version.") }
            return
        }
        sendServiceCommand(RecordingService.ACTION_PAUSE_RECORDING)
    }

    fun resumeRecording() {
        val currentState = _uiState.value
        if (currentState.showStopConfirmationDialog || currentState.showCancelConfirmationDialog) {
            Log.w("RecordingViewModel", "Resume called while confirmation dialog is showing.")
            return
        }
        if (!currentState.isRecording || !currentState.isPaused) {
            Log.w("RecordingViewModel", "Resume called but not recording or not paused.")
            return
        }
        if (!currentState.supportsPauseResume) {
            _uiState.update { it.copy(error = "Pause/Resume not supported on this Android version.") }
            return
        }
        sendServiceCommand(RecordingService.ACTION_RESUME_RECORDING)
    }

    fun requestCancelConfirmation() {
        if (!_uiState.value.isRecording) {
            Log.w("RecordingViewModel", "Cancel confirmation requested but not recording.")
            return
        }
        _uiState.update { it.copy(showCancelConfirmationDialog = true) }
    }

    fun confirmCancelRecording() {
        _uiState.update { it.copy(showCancelConfirmationDialog = false) }
        sendServiceCommand(RecordingService.ACTION_CANCEL_RECORDING)
    }

    fun dismissCancelConfirmation() {
        _uiState.update { it.copy(showCancelConfirmationDialog = false) }
    }


    private fun sendServiceCommand(action: String) {
        val intent = Intent(application, RecordingService::class.java).apply {
            this.action = action
        }
        Log.d("RecordingViewModel", "Sending command to service: $action")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                application.startForegroundService(intent)
            } else {
                application.startService(intent)
            }
        } catch (e: Exception) {
            Log.e("RecordingViewModel", "Failed to send command '$action' to RecordingService: ${e.message}", e)
            _uiState.update { it.copy(error = "Could not communicate with recording service. ${e.message}") }
        }
    }

    fun finalizeRecording(title: String?) {
        val savedFilePath = finishedRecordingPath ?: run {
            Log.e("RecordingViewModel", "Cannot finalize recording: Finished path is null.")
            _uiState.update { it.copy(showTitleDialog = false, error = "Error saving recording details.") }
            return
        }
        val savedFile = File(savedFilePath)
        if (!savedFile.exists()) {
            Log.e("RecordingViewModel", "Cannot finalize recording: File does not exist at path $savedFilePath")
            _uiState.update { it.copy(showTitleDialog = false, error = "Error: Recording file not found.") }
            return
        }

        val savedFileName = savedFile.name
        val timestamp = System.currentTimeMillis()

        val metadata = RecordingMetadata(
            filePath = savedFilePath,
            fileName = savedFileName,
            title = title?.takeIf { it.isNotBlank() },
            timestampMillis = timestamp,
            durationMillis = finishedRecordingDuration
        )

        Log.d("RecordingViewModel", "Finalizing recording metadata: $metadata")

        viewModelScope.launch(Dispatchers.IO) {
            var metadataSavedSuccessfully = false
            var finalErrorMessage: String? = null
            try {
                val metadataJson = gson.toJson(metadata)
                val metadataFileName = savedFileName.substringBeforeLast('.', savedFileName) + ".json"
                val metadataFile = File(savedFile.parent, metadataFileName)

                FileOutputStream(metadataFile).use { outputStream ->
                    outputStream.write(metadataJson.toByteArray())
                }
                metadataSavedSuccessfully = true
                Log.d("RecordingViewModel", "Metadata saved successfully to: ${metadataFile.absolutePath}")

            } catch (e: IOException) {
                Log.e("RecordingViewModel", "Failed to save metadata JSON file", e)
                finalErrorMessage = "Failed to save recording metadata."
            } catch (e: Exception) {
                Log.e("RecordingViewModel", "Unexpected error saving metadata JSON", e)
                finalErrorMessage = "An unexpected error occurred while saving."
            }

            launch(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        recordingSavedMessage = if (metadataSavedSuccessfully) "Recording '$savedFileName' saved." else null,
                        showTitleDialog = false,
                        error = finalErrorMessage ?: it.error
                    )
                }
                resetTimerDisplayIfNotSaving()
            }
        }

        finishedRecordingPath = null
        finishedRecordingDuration = 0L
    }

    private fun formatElapsedTime(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun resetTimerDisplayIfNotSaving() {
        if (!_uiState.value.isRecording && !_uiState.value.showTitleDialog) {
            _uiState.update {
                it.copy(
                    elapsedTimeMillis = 0L,
                    elapsedTimeFormatted = formatElapsedTime(0L)
                )
            }
            Log.d("RecordingViewModel", "Timer Display Reset")
        }
    }

    fun consumeSavedMessage() {
        _uiState.update { it.copy(recordingSavedMessage = null) }
        Log.d("RecordingViewModel", "Consumed saved message.")
    }

    fun consumeErrorMessage() {
        _uiState.update { it.copy(error = null) }
        Log.d("RecordingViewModel", "Consumed error message.")
    }

    override fun onCleared() {
        super.onCleared()
        unregisterReceiver()
        Log.d("RecordingViewModel", "ViewModel Cleared.")
    }
}