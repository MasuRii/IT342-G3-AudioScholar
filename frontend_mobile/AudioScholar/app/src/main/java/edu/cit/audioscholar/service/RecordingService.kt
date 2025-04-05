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
    private var timeWhenPaused: Long = 0L
    private var timerJob: Job? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var isPaused: Boolean = false

    private lateinit var notificationManager: NotificationManager

    companion object {
        const val ACTION_START_RECORDING = "edu.cit.audioscholar.ACTION_START_RECORDING"
        const val ACTION_STOP_RECORDING = "edu.cit.audioscholar.ACTION_STOP_RECORDING"
        const val ACTION_PAUSE_RECORDING = "edu.cit.audioscholar.ACTION_PAUSE_RECORDING"
        const val ACTION_RESUME_RECORDING = "edu.cit.audioscholar.ACTION_RESUME_RECORDING"
        const val ACTION_CANCEL_RECORDING = "edu.cit.audioscholar.ACTION_CANCEL_RECORDING"

        const val BROADCAST_ACTION_STATUS_UPDATE = "edu.cit.audioscholar.BROADCAST_ACTION_STATUS_UPDATE"
        const val EXTRA_IS_RECORDING = "EXTRA_IS_RECORDING"
        const val EXTRA_IS_PAUSED = "EXTRA_IS_PAUSED"
        const val EXTRA_ELAPSED_TIME_MILLIS = "EXTRA_ELAPSED_TIME_MILLIS"
        const val EXTRA_ERROR_MESSAGE = "EXTRA_ERROR_MESSAGE"
        const val EXTRA_RECORDING_FINISHED_PATH = "EXTRA_RECORDING_FINISHED_PATH"
        const val EXTRA_RECORDING_FINISHED_DURATION = "EXTRA_RECORDING_FINISHED_DURATION"
        const val EXTRA_RECORDING_CANCELLED = "EXTRA_RECORDING_CANCELLED"


        const val NOTIFICATION_CHANNEL_ID = "RecordingChannel"
        const val NOTIFICATION_ID = 101
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        Log.d("RecordingService", "Service Created")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Recording Controls",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Notifications for ongoing audio recording"
            notificationManager.createNotificationChannel(channel)
            Log.d("RecordingService", "Notification channel created.")
        }
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("RecordingService", "onStartCommand received: ${intent?.action}")
        when (intent?.action) {
            ACTION_START_RECORDING -> startRecording()
            ACTION_STOP_RECORDING -> stopRecording()
            ACTION_PAUSE_RECORDING -> pauseRecording()
            ACTION_RESUME_RECORDING -> resumeRecording()
            ACTION_CANCEL_RECORDING -> cancelRecording()
        }
        return START_NOT_STICKY
    }

    private fun startRecording() {
        if (mediaRecorder != null) {
            Log.w("RecordingService", "Start recording called but already recording.")
            broadcastError("Recording already in progress.")
            return
        }

        Log.d("RecordingService", "Attempting to start recording...")
        isPaused = false
        timeWhenPaused = 0L

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
                        broadcastStatusUpdate(isRecording = true, isPaused = false, elapsedTimeMillis = 0L)
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

    private fun pauseRecording() {
        if (mediaRecorder == null || isPaused) {
            Log.w("RecordingService", "Pause called but not recording or already paused.")
            broadcastError("Cannot pause: Not recording or already paused.")
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.w("RecordingService", "Pause requires Android N (API 24) or higher.")
            broadcastError("Pause/Resume requires Android N (API 24) or higher.")
            return
        }

        try {
            mediaRecorder?.pause()
            isPaused = true
            timeWhenPaused = System.currentTimeMillis()
            stopTimer()
            val elapsedMillis = calculateElapsedTime()
            updateNotification(formatElapsedTime(elapsedMillis))
            broadcastStatusUpdate(isRecording = true, isPaused = true, elapsedTimeMillis = elapsedMillis)
            Log.d("RecordingService", "Recording paused at ${formatElapsedTime(elapsedMillis)}")
        } catch (e: IllegalStateException) {
            handleError("Failed to pause MediaRecorder: ${e.message}", e)
        } catch (e: Exception) {
            handleError("An unexpected error occurred during pauseRecording: ${e.message}", e)
        }
    }

    private fun resumeRecording() {
        if (mediaRecorder == null || !isPaused) {
            Log.w("RecordingService", "Resume called but not recording or not paused.")
            broadcastError("Cannot resume: Not recording or not paused.")
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.w("RecordingService", "Resume requires Android N (API 24) or higher.")
            broadcastError("Pause/Resume requires Android N (API 24) or higher.")
            return
        }

        try {
            mediaRecorder?.resume()
            isPaused = false
            val pauseDuration = System.currentTimeMillis() - timeWhenPaused
            recordingStartTime += pauseDuration
            timeWhenPaused = 0L

            startTimer()
            val elapsedMillis = calculateElapsedTime()
            updateNotification(formatElapsedTime(elapsedMillis))
            broadcastStatusUpdate(isRecording = true, isPaused = false, elapsedTimeMillis = elapsedMillis)
            Log.d("RecordingService", "Recording resumed.")
        } catch (e: IllegalStateException) {
            handleError("Failed to resume MediaRecorder: ${e.message}", e)
        } catch (e: Exception) {
            handleError("An unexpected error occurred during resumeRecording: ${e.message}", e)
        }
    }

    private fun stopRecording() {
        if (mediaRecorder == null) {
            Log.w("RecordingService", "Stop recording called but not recording.")
            return
        }

        Log.d("RecordingService", "Attempting to stop recording...")
        stopTimer()
        val finalDuration = calculateElapsedTime()
        val fileToSave = currentRecordingFile

        if (isPaused && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                mediaRecorder?.resume()
                Log.d("RecordingService", "Briefly resumed before stopping.")
            } catch (e: IllegalStateException) {
                Log.e("RecordingService", "Error resuming before stop: ${e.message}", e)
            }
        }
        isPaused = false

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
                if (fileToSave?.exists() == true && finalDuration > 500) {
                    broadcastRecordingFinished(fileToSave.absolutePath, finalDuration)
                    Log.w("RecordingService", "Stopped with error, but file seems partially saved.")
                } else {
                    fileToSave?.delete()
                }
            } catch (e: RuntimeException) {
                handleError("Runtime error stopping recording: ${e.message}", e)
                releaseMediaRecorder()
                if (fileToSave?.exists() == true && finalDuration > 500) {
                    broadcastRecordingFinished(fileToSave.absolutePath, finalDuration)
                    Log.w("RecordingService", "Stopped with runtime error, but file seems partially saved.")
                } else {
                    fileToSave?.delete()
                }
            } catch (e: Exception) {
                handleError("An unexpected error occurred during stopRecording: ${e.message}", e)
                releaseMediaRecorder()
                if (fileToSave?.exists() == true && finalDuration > 500) {
                    broadcastRecordingFinished(fileToSave.absolutePath, finalDuration)
                    Log.w("RecordingService", "Stopped with unexpected error, but file seems partially saved.")
                } else {
                    fileToSave?.delete()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    stopForeground(Service.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                if (fileToSave == null || !fileToSave.exists()) {
                    broadcastStatusUpdate(isRecording = false, isPaused = false, elapsedTimeMillis = 0L)
                }
                Log.d("RecordingService", "Service stopping after stopRecording.")
            }
        }
    }

    private fun cancelRecording() {
        if (mediaRecorder == null) {
            Log.w("RecordingService", "Cancel called but not recording.")
            return
        }
        Log.d("RecordingService", "Attempting to cancel recording...")
        stopTimer()
        releaseMediaRecorder()

        val fileToDelete = currentRecordingFile
        currentRecordingFile = null
        isPaused = false
        timeWhenPaused = 0L

        serviceScope.launch {
            fileToDelete?.let {
                if (it.exists()) {
                    try {
                        if (it.delete()) {
                            Log.d("RecordingService", "Cancelled recording file deleted: ${it.absolutePath}")
                        } else {
                            Log.w("RecordingService", "Failed to delete cancelled recording file: ${it.absolutePath}")
                        }
                    } catch (e: SecurityException) {
                        Log.e("RecordingService", "SecurityException deleting cancelled file: ${e.message}", e)
                    }
                }
            }

            withContext(Dispatchers.Main) {
                stopForeground(Service.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            broadcastRecordingCancelled()
            Log.d("RecordingService", "Recording cancelled and service stopping.")
        }
    }


    private fun startTimer() {
        stopTimer()
        if (isPaused) return

        timerJob = serviceScope.launch(Dispatchers.Main) {
            Log.d("RecordingService", "Timer Started")
            while (isActive && mediaRecorder != null && !isPaused) {
                val elapsedMillis = calculateElapsedTime()
                updateNotification(formatElapsedTime(elapsedMillis))
                broadcastStatusUpdate(isRecording = true, isPaused = false, elapsedTimeMillis = elapsedMillis)
                delay(1000L)
            }
            Log.d("RecordingService", "Timer loop finished (isActive=$isActive, mediaRecorder=${mediaRecorder != null}, isPaused=$isPaused).")
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        Log.d("RecordingService", "Timer Stopped/Cancelled")
    }

    private fun calculateElapsedTime(): Long {
        if (recordingStartTime == 0L) return 0L
        if (isPaused) {
            return timeWhenPaused - recordingStartTime
        } else {
            return System.currentTimeMillis() - recordingStartTime
        }
    }


    private fun createNotification(contentText: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title_recording))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .clearActions()

        if (isPaused) {
            val resumeIntent = Intent(this, RecordingService::class.java).apply { action = ACTION_RESUME_RECORDING }
            val resumePendingIntent = PendingIntent.getService(this, 1, resumeIntent, pendingIntentFlags)
            builder.addAction(R.drawable.play_pause_24px, getString(R.string.notification_action_resume), resumePendingIntent)

            val cancelIntent = Intent(this, RecordingService::class.java).apply { action = ACTION_CANCEL_RECORDING }
            val cancelPendingIntent = PendingIntent.getService(this, 2, cancelIntent, pendingIntentFlags)
            builder.addAction(R.drawable.stop_circle_24px, getString(R.string.notification_action_cancel), cancelPendingIntent)

            builder.setContentTitle(getString(R.string.notification_title_paused))

        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val pauseIntent = Intent(this, RecordingService::class.java).apply { action = ACTION_PAUSE_RECORDING }
                val pausePendingIntent = PendingIntent.getService(this, 3, pauseIntent, pendingIntentFlags)
                builder.addAction(R.drawable.pause_circle_24px, getString(R.string.notification_action_pause), pausePendingIntent)
            }

            val stopIntent = Intent(this, RecordingService::class.java).apply { action = ACTION_STOP_RECORDING }
            val stopPendingIntent = PendingIntent.getService(this, 4, stopIntent, pendingIntentFlags)
            builder.addAction(R.drawable.stop_circle_24px, getString(R.string.notification_action_stop), stopPendingIntent)
        }

        return builder.build()
    }

    private fun updateNotification(contentText: String) {
        val notification = createNotification(contentText)
        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e("RecordingService", "Error updating notification: ${e.message}", e)
        }
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
                stopTimer()
                reset()
                release()
                Log.d("RecordingService", "MediaRecorder released successfully.")
            } catch (e: Exception) {
                Log.e("RecordingService", "Error releasing MediaRecorder: ${e.message}", e)
            }
        }
        mediaRecorder = null
    }

    private fun handleError(message: String, throwable: Throwable? = null) {
        Log.e("RecordingService", "Error: $message", throwable)
        releaseMediaRecorder()
        broadcastError(message)
        isPaused = false
        timeWhenPaused = 0L
        serviceScope.launch(Dispatchers.Main) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun broadcastStatusUpdate(isRecording: Boolean, isPaused: Boolean, elapsedTimeMillis: Long) {
        val intent = Intent(BROADCAST_ACTION_STATUS_UPDATE).apply {
            putExtra(EXTRA_IS_RECORDING, isRecording)
            putExtra(EXTRA_IS_PAUSED, isPaused)
            putExtra(EXTRA_ELAPSED_TIME_MILLIS, elapsedTimeMillis)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun broadcastError(errorMessage: String) {
        val intent = Intent(BROADCAST_ACTION_STATUS_UPDATE).apply {
            putExtra(EXTRA_IS_RECORDING, false)
            putExtra(EXTRA_IS_PAUSED, false)
            putExtra(EXTRA_ERROR_MESSAGE, errorMessage)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun broadcastRecordingFinished(filePath: String, durationMillis: Long) {
        val intent = Intent(BROADCAST_ACTION_STATUS_UPDATE).apply {
            putExtra(EXTRA_IS_RECORDING, false)
            putExtra(EXTRA_IS_PAUSED, false)
            putExtra(EXTRA_RECORDING_FINISHED_PATH, filePath)
            putExtra(EXTRA_RECORDING_FINISHED_DURATION, durationMillis)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun broadcastRecordingCancelled() {
        val intent = Intent(BROADCAST_ACTION_STATUS_UPDATE).apply {
            putExtra(EXTRA_IS_RECORDING, false)
            putExtra(EXTRA_IS_PAUSED, false)
            putExtra(EXTRA_RECORDING_CANCELLED, true)
            putExtra(EXTRA_ELAPSED_TIME_MILLIS, 0L)
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