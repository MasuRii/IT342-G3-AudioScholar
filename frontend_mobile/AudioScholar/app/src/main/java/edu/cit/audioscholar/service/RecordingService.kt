package edu.cit.audioscholar.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import dagger.hilt.android.AndroidEntryPoint
import edu.cit.audioscholar.R
import edu.cit.audioscholar.data.local.file.RecordingFileHandler
import edu.cit.audioscholar.ui.main.MainActivity
import kotlinx.coroutines.*
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class RecordingService : Service() {

    @Inject lateinit var recordingFileHandler: RecordingFileHandler

    private var mediaRecorder: MediaRecorder? = null
    private var currentRecordingFile: File? = null
    private var recordingStartTime: Long = 0L
    private var timerJob: Job? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private lateinit var notificationManager: NotificationManager

    companion object {
        const val ACTION_START_RECORDING = "edu.cit.audioscholar.ACTION_START_RECORDING"
        const val ACTION_STOP_RECORDING = "edu.cit.audioscholar.ACTION_STOP_RECORDING"

        const val BROADCAST_ACTION_STATUS_UPDATE = "edu.cit.audioscholar.BROADCAST_ACTION_STATUS_UPDATE"
        const val EXTRA_IS_RECORDING = "EXTRA_IS_RECORDING"
        const val EXTRA_ELAPSED_TIME_MILLIS = "EXTRA_ELAPSED_TIME_MILLIS"
        const val EXTRA_ERROR_MESSAGE = "EXTRA_ERROR_MESSAGE"
        const val EXTRA_RECORDING_FINISHED_PATH = "EXTRA_RECORDING_FINISHED_PATH"
        const val EXTRA_RECORDING_FINISHED_DURATION = "EXTRA_RECORDING_FINISHED_DURATION"


        const val NOTIFICATION_CHANNEL_ID = "RecordingChannel"
        const val NOTIFICATION_ID = 101
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        Log.d("RecordingService", "Service Created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("RecordingService", "onStartCommand received: ${intent?.action}")
        when (intent?.action) {
            ACTION_START_RECORDING -> {
                startRecording()
            }
            ACTION_STOP_RECORDING -> {
                stopRecording()
            }
        }
        return START_STICKY
    }

    private fun startRecording() {
        if (mediaRecorder != null) {
            Log.w("RecordingService", "Start recording called but already recording.")
            broadcastError("Recording already in progress.")
            return
        }

        Log.d("RecordingService", "Attempting to start recording...")
        serviceScope.launch {
            var recorderInstance: MediaRecorder? = null
            try {
                recorderInstance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(applicationContext)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }

                recordingFileHandler.setupMediaRecorderOutputFile(recorderInstance)
                    .onSuccess { outputFile ->
                        Log.d("RecordingService", "File handler setup successful.")
                        mediaRecorder = recorderInstance
                        currentRecordingFile = outputFile

                        recorderInstance.prepare()
                        Log.d("RecordingService", "MediaRecorder prepared.")
                        recorderInstance.start()
                        Log.d("RecordingService", "MediaRecorder started.")

                        recordingStartTime = System.currentTimeMillis()
                        startForeground(NOTIFICATION_ID, createNotification(formatElapsedTime(0L)))
                        startTimer()
                        broadcastStatusUpdate(isRecording = true, elapsedTimeMillis = 0L)
                        Log.d("RecordingService", "Recording started successfully. File: ${outputFile.absolutePath}")
                    }
                    .onFailure { exception ->
                        handleError("Failed to setup recording file: ${exception.message}", exception)
                        recorderInstance?.release()
                    }

            } catch (e: IOException) {
                handleError("MediaRecorder setup failed (IO): ${e.message}", e)
                recorderInstance?.release()
            } catch (e: IllegalStateException) {
                handleError("MediaRecorder state error during start: ${e.message}", e)
                recorderInstance?.release()
            } catch (e: SecurityException) {
                handleError("Security error during recording setup. Check permissions.", e)
                recorderInstance?.release()
            } catch (e: Exception) {
                handleError("An unexpected error occurred during startRecording: ${e.message}", e)
                recorderInstance?.release()
            }
        }
    }

    private fun stopRecording() {
        if (mediaRecorder == null) {
            Log.w("RecordingService", "Stop recording called but not recording.")
            return
        }

        Log.d("RecordingService", "Attempting to stop recording...")
        stopTimer()
        val finalDuration = System.currentTimeMillis() - recordingStartTime
        val fileToSave = currentRecordingFile

        serviceScope.launch {
            try {
                mediaRecorder?.apply {
                    stop()
                    Log.d("RecordingService", "MediaRecorder stopped.")
                }
                releaseMediaRecorder()

                if (fileToSave != null) {
                    Log.d("RecordingService", "Recording finished. Path: ${fileToSave.absolutePath}, Duration: $finalDuration")
                    broadcastRecordingFinished(fileToSave.absolutePath, finalDuration)
                } else {
                    handleError("Recording stopped but file path was null.")
                }

            } catch (e: IllegalStateException) {
                handleError("Failed to stop MediaRecorder properly: ${e.message}", e)
                releaseMediaRecorder()
            } catch (e: RuntimeException) {
                handleError("Runtime error stopping recording: ${e.message}", e)
                releaseMediaRecorder()
            } catch (e: Exception) {
                handleError("An unexpected error occurred during stopRecording: ${e.message}", e)
                releaseMediaRecorder()
            } finally {
                withContext(Dispatchers.Main) {
                    stopForeground(true)
                    stopSelf()
                }
                broadcastStatusUpdate(isRecording = false, elapsedTimeMillis = finalDuration)
                Log.d("RecordingService", "Service stopping.")
            }
        }
    }

    private fun startTimer() {
        stopTimer()
        timerJob = serviceScope.launch(Dispatchers.Main) {
            Log.d("RecordingService", "Timer Started")
            while (isActive && mediaRecorder != null) {
                val elapsedMillis = System.currentTimeMillis() - recordingStartTime
                updateNotification(formatElapsedTime(elapsedMillis))
                broadcastStatusUpdate(isRecording = true, elapsedTimeMillis = elapsedMillis)
                delay(1000L)
            }
            Log.d("RecordingService", "Timer loop finished.")
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        Log.d("RecordingService", "Timer Stopped/Cancelled")
    }

    private fun createNotification(contentText: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, RecordingService::class.java).apply {
            action = ACTION_STOP_RECORDING
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title_recording))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(R.drawable.ic_stop, getString(R.string.notification_action_stop), stopPendingIntent)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val notification = createNotification(contentText)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun formatElapsedTime(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun releaseMediaRecorder() {
        Log.d("RecordingService", "Attempting to release MediaRecorder.")
        mediaRecorder?.apply {
            try {
                reset()
                release()
                Log.d("RecordingService", "MediaRecorder released successfully.")
            } catch (e: Exception) {
                Log.e("RecordingService", "Error releasing MediaRecorder: ${e.message}", e)
            }
        }
        mediaRecorder = null
        currentRecordingFile = null
    }

    private fun handleError(message: String, throwable: Throwable? = null) {
        Log.e("RecordingService", "Error: $message", throwable)
        releaseMediaRecorder()
        broadcastError(message)
        serviceScope.launch(Dispatchers.Main) {
            stopForeground(true)
            stopSelf()
        }
    }

    private fun broadcastStatusUpdate(isRecording: Boolean, elapsedTimeMillis: Long) {
        val intent = Intent(BROADCAST_ACTION_STATUS_UPDATE).apply {
            putExtra(EXTRA_IS_RECORDING, isRecording)
            putExtra(EXTRA_ELAPSED_TIME_MILLIS, elapsedTimeMillis)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun broadcastError(errorMessage: String) {
        val intent = Intent(BROADCAST_ACTION_STATUS_UPDATE).apply {
            putExtra(EXTRA_IS_RECORDING, false)
            putExtra(EXTRA_ERROR_MESSAGE, errorMessage)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun broadcastRecordingFinished(filePath: String, durationMillis: Long) {
        val intent = Intent(BROADCAST_ACTION_STATUS_UPDATE).apply {
            putExtra(EXTRA_IS_RECORDING, false)
            putExtra(EXTRA_RECORDING_FINISHED_PATH, filePath)
            putExtra(EXTRA_RECORDING_FINISHED_DURATION, durationMillis)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }


    override fun onDestroy() {
        super.onDestroy()
        Log.d("RecordingService", "Service Destroyed")
        releaseMediaRecorder()
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}