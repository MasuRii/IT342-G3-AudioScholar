package edu.cit.audioscholar.ui.recording

import android.Manifest
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

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
    val supportsPauseResume: Boolean = true,
    val showStopConfirmationDialog: Boolean = false,
    val showCancelConfirmationDialog: Boolean = false,
    val currentAmplitude: Float = 0f
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
                val amplitude = intent.getFloatExtra(RecordingService.EXTRA_CURRENT_AMPLITUDE, 0f)

                Log.d("RecordingViewModel", "Broadcast received: isRecording=$isRecording, isPaused=$isPaused, elapsed=$elapsedTime, amplitude=$amplitude, error=$errorMessage, finishedPath=$finishedPath, cancelled=$isCancelled")

                val wasStopConfirmationShowing = _uiState.value.showStopConfirmationDialog

                _uiState.update { currentState ->
                    currentState.copy(
                        isRecording = isRecording,
                        isPaused = isPaused,
                        elapsedTimeMillis = elapsedTime,
                        elapsedTimeFormatted = formatElapsedTime(elapsedTime),
                        currentAmplitude = if (isRecording && !isPaused) amplitude else 0f,
                        error = errorMessage ?: if (currentState.error != null && finishedPath == null && !isCancelled) null else currentState.error,
                        showStopConfirmationDialog = if (wasStopConfirmationShowing && isPaused && !currentState.isPaused) true else currentState.showStopConfirmationDialog
                    )
                }

                if (errorMessage != null) {
                    _uiState.update { it.copy(
                        error = errorMessage,
                        showTitleDialog = false,
                        isRecording = false,
                        isPaused = false,
                        currentAmplitude = 0f,
                        showStopConfirmationDialog = false,
                        showCancelConfirmationDialog = false
                    ) }
                } else if (isCancelled) {
                    _uiState.update { it.copy(
                        isRecording = false,
                        isPaused = false,
                        showTitleDialog = false,
                        elapsedTimeMillis = 0L,
                        elapsedTimeFormatted = formatElapsedTime(0L),
                        currentAmplitude = 0f,
                        recordingSavedMessage = "Recording cancelled.",
                        showStopConfirmationDialog = false,
                        showCancelConfirmationDialog = false
                    ) }
                } else if (finishedPath != null) {
                    finishedRecordingPath = finishedPath
                    finishedRecordingDuration = finishedDuration
                    _uiState.update { it.copy(
                        showTitleDialog = true,
                        isRecording = false,
                        isPaused = false,
                        currentAmplitude = 0f,
                        showStopConfirmationDialog = false,
                        showCancelConfirmationDialog = false
                    ) }
                } else if (!isRecording && !isPaused) {
                    resetTimerDisplayIfNotSaving()
                    if (!uiState.value.showTitleDialog) {
                        _uiState.update { it.copy(
                            showStopConfirmationDialog = false,
                            showCancelConfirmationDialog = false,
                            currentAmplitude = 0f
                        ) }
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
            Log.d("RecordingViewModel", "BroadcastReceiver unregistered.")
        } catch (e: IllegalArgumentException) {
            Log.w("RecordingViewModel", "Receiver already unregistered or error during unregister: ${e.message}")
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
            Log.e("RecordingViewModel", "Start recording failed: Permission not granted.")
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

        if (!currentState.isPaused) {
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
        val currentUiState = _uiState.value

        if (!currentUiState.showCancelConfirmationDialog) {
            Log.w("RecordingViewModel", "confirmCancelRecording called but showCancelConfirmationDialog is false. Possible duplicate action or state mismatch. Ignoring.")
            return
        }

        _uiState.update {
            it.copy(
                showCancelConfirmationDialog = false,
                isRecording = if (currentUiState.isRecording) false else it.isRecording,
                isPaused = if (currentUiState.isRecording) false else it.isPaused,
                currentAmplitude = if (currentUiState.isRecording) 0f else it.currentAmplitude,
                elapsedTimeMillis = if (currentUiState.isRecording) 0L else it.elapsedTimeMillis,
                elapsedTimeFormatted = if (currentUiState.isRecording) formatElapsedTime(0L) else it.elapsedTimeFormatted
            )
        }

        if (currentUiState.isRecording) {
            Log.d("RecordingViewModel", "Proceeding to send ACTION_CANCEL_RECORDING. UI state has been optimistically updated.")
            sendServiceCommand(RecordingService.ACTION_CANCEL_RECORDING)
        } else {
            Log.w("RecordingViewModel", "confirmCancelRecording: 'isRecording' was already false (before optimistic update) when cancel was confirmed. Not sending command to service. Dialog dismissed.")
        }
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
        
        val finalTitle = title ?: run {
            val dateFormat = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
            "Recording ${dateFormat.format(Date(timestamp))}"
        }

        val description: String? = null

        val metadata = RecordingMetadata(
            filePath = savedFilePath,
            fileName = savedFileName,
            title = finalTitle,
            description = description,
            timestampMillis = timestamp,
            durationMillis = finishedRecordingDuration,
            remoteRecordingId = null,
            cachedSummaryText = null,
            cachedGlossaryItems = null,
            cachedRecommendations = null,
            cacheTimestampMillis = null
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
                        recordingSavedMessage = if (metadataSavedSuccessfully) "Recording '${metadata.title ?: savedFileName}' saved." else null,
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
                    elapsedTimeFormatted = formatElapsedTime(0L),
                    currentAmplitude = 0f
                )
            }
            Log.d("RecordingViewModel", "Timer Display Reset")
        }
    }

    fun consumeSavedMessage() {
        if (_uiState.value.recordingSavedMessage != null) {
            _uiState.update { it.copy(recordingSavedMessage = null) }
            Log.d("RecordingViewModel", "Consumed saved message.")
        }
    }

    fun consumeErrorMessage() {
        if (_uiState.value.error != null) {
            _uiState.update { it.copy(error = null) }
            Log.d("RecordingViewModel", "Consumed error message.")
        }
    }

    override fun onCleared() {
        super.onCleared()
        unregisterReceiver()
        Log.d("RecordingViewModel", "ViewModel Cleared.")
    }
}