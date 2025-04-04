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
    val elapsedTimeMillis: Long = 0L,
    val elapsedTimeFormatted: String = "00:00:00",
    val permissionGranted: Boolean = false,
    val requiresPermissionRationale: Boolean = false,
    val error: String? = null,
    val recordingSavedMessage: String? = null,
    val showTitleDialog: Boolean = false,
    val notificationPermissionGranted: Boolean = true
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
                val elapsedTime = intent.getLongExtra(RecordingService.EXTRA_ELAPSED_TIME_MILLIS, 0L)
                val errorMessage = intent.getStringExtra(RecordingService.EXTRA_ERROR_MESSAGE)
                val finishedPath = intent.getStringExtra(RecordingService.EXTRA_RECORDING_FINISHED_PATH)
                val finishedDuration = intent.getLongExtra(RecordingService.EXTRA_RECORDING_FINISHED_DURATION, 0L)

                Log.d("RecordingViewModel", "Broadcast received: isRecording=$isRecording, elapsed=$elapsedTime, error=$errorMessage, finishedPath=$finishedPath")

                _uiState.update { currentState ->
                    currentState.copy(
                        isRecording = isRecording,
                        elapsedTimeMillis = elapsedTime,
                        elapsedTimeFormatted = formatElapsedTime(elapsedTime),
                        error = errorMessage ?: currentState.error
                    )
                }

                if (errorMessage != null) {
                    _uiState.update { it.copy(error = errorMessage) }
                    if (!isRecording) {
                        _uiState.update { it.copy(showTitleDialog = false) }
                    }
                }

                if (finishedPath != null) {
                    finishedRecordingPath = finishedPath
                    finishedRecordingDuration = finishedDuration
                    if (errorMessage == null) {
                        _uiState.update { it.copy(showTitleDialog = true, isRecording = false) }
                    }
                } else if (!isRecording && errorMessage == null) {
                    resetTimerDisplayIfNotSaving()
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
            Log.d("RecordingViewModel", "BroadcastReceiver unregistered.")
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
                        error = if (granted) null else it.error
                    )
                }
                if (!granted) {
                    _uiState.update { it.copy(error = "Audio permission is required to record.") }
                }
            }
            Manifest.permission.POST_NOTIFICATIONS -> {
                _uiState.update { it.copy(notificationPermissionGranted = granted) }
                if (!granted) {
                    _uiState.update { it.copy(error = "Notification permission denied. Progress updates might not be shown.") }
                }
            }
        }
        Log.d("RecordingViewModel", "Permission Result - Type: $permissionType, Granted: $granted")
    }


    fun toggleRecording() {
        if (_uiState.value.isRecording) {
            sendServiceCommand(RecordingService.ACTION_STOP_RECORDING)
        } else {
            if (!_uiState.value.permissionGranted) {
                _uiState.update { it.copy(error = "Microphone permission required.") }
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !_uiState.value.notificationPermissionGranted) {
                _uiState.update { it.copy(error = "Notification permission recommended for background recording updates.") }
            }

            _uiState.update { it.copy(error = null, recordingSavedMessage = null) }
            sendServiceCommand(RecordingService.ACTION_START_RECORDING)
        }
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
            Log.e("RecordingViewModel", "Failed to start RecordingService: ${e.message}", e)
            _uiState.update { it.copy(error = "Could not start recording service. ${e.message}") }
        }
    }

    fun finalizeRecording(title: String?) {
        val savedFilePath = finishedRecordingPath ?: run {
            Log.e("RecordingViewModel", "Cannot finalize recording: Finished path is null.")
            _uiState.update { it.copy(showTitleDialog = false, error = "Error saving recording details.") }
            return
        }
        val savedFile = File(savedFilePath)
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
                val metadataFileName = savedFileName.substringBeforeLast('.') + ".json"
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