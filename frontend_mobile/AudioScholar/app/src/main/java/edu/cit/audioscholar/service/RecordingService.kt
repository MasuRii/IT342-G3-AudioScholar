package edu.cit.audioscholar.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import dagger.hilt.android.AndroidEntryPoint
import edu.cit.audioscholar.R
import edu.cit.audioscholar.data.local.file.RecordingFileHandler
import edu.cit.audioscholar.ui.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.sqrt
import android.content.SharedPreferences
import javax.inject.Named
import edu.cit.audioscholar.di.PreferencesModule
import edu.cit.audioscholar.domain.model.QualitySetting

@AndroidEntryPoint
class RecordingService : Service() {

    @Inject lateinit var recordingFileHandler: RecordingFileHandler
    @Inject
    @Named(PreferencesModule.SETTINGS_PREFERENCES)
    lateinit var prefs: SharedPreferences

    private var mediaRecorder: MediaRecorder? = null
    private var currentRecordingFile: File? = null

    private var audioRecord: AudioRecord? = null
    private var audioRecordBufferSize = 0
    private var audioRecordJob: Job? = null
    private val audioRecordScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var recordingStartTime: Long = 0L
    private var timeWhenPaused: Long = 0L
    private var timerJob: Job? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var isPaused: Boolean = false
    private var currentAmplitude: Float = 0f

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
        const val EXTRA_CURRENT_AMPLITUDE = "EXTRA_CURRENT_AMPLITUDE"

        const val NOTIFICATION_CHANNEL_ID = "RecordingChannel"
        const val NOTIFICATION_ID = 101

        private const val SAMPLE_RATE = 44100
        private const val AUDIO_SOURCE = MediaRecorder.AudioSource.MIC
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val AMPLITUDE_UPDATE_INTERVAL_MS = 100L

        private const val DEFAULT_SAMPLE_RATE = 44100
        private const val LOW_QUALITY_BUFFER_SIZE = 2048
        private const val MEDIUM_QUALITY_BUFFER_SIZE = 4096
        private const val HIGH_QUALITY_BUFFER_SIZE = 8192
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
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
        val action = intent?.action

        if (action == null) {
            Log.w("RecordingService", "Received null action. Stopping service.")
            stopSelf()
            return START_NOT_STICKY
        }

        val initialNotification = createNotification(
            when {
                action == ACTION_START_RECORDING -> getString(R.string.notification_title_starting)
                isPaused -> getString(R.string.notification_title_paused)
                mediaRecorder != null || audioRecord != null -> formatElapsedTime(calculateElapsedTime())
                else -> getString(R.string.notification_title_processing)
            }
        )
        try {
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, initialNotification)
            Log.d("RecordingService", "Ensured startForeground() is called early for action: $action")
        } catch (e: Exception) {
            Log.e("RecordingService", "Error calling startForeground in onStartCommand: ${e.message}", e)
            stopSelf()
            return START_NOT_STICKY
        }

        when (action) {
            ACTION_START_RECORDING -> startRecording()
            ACTION_STOP_RECORDING -> stopRecording()
            ACTION_PAUSE_RECORDING -> pauseRecording()
            ACTION_RESUME_RECORDING -> resumeRecording()
            ACTION_CANCEL_RECORDING -> cancelRecording()
            else -> {
                Log.w("RecordingService", "Received unknown action: $action. Stopping service.")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun getAudioRecordBufferSize(): Int {
        val quality = QualitySetting.valueOf(
            prefs.getString(QualitySetting.PREF_KEY, QualitySetting.DEFAULT)
                ?: QualitySetting.DEFAULT
        )
        
        val baseBufferSize = when (quality) {
            QualitySetting.Low -> LOW_QUALITY_BUFFER_SIZE
            QualitySetting.Medium -> MEDIUM_QUALITY_BUFFER_SIZE
            QualitySetting.High -> HIGH_QUALITY_BUFFER_SIZE
        }
        
        val channelConfig = if (quality == QualitySetting.High) 
            AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
        
        Log.d("RecordingQuality", "AudioRecord Configuration: Quality=$quality, " +
                "BufferSize=$baseBufferSize, " +
                "ChannelConfig=${if(channelConfig == AudioFormat.CHANNEL_IN_STEREO) "STEREO" else "MONO"}")
        
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            channelConfig,
            AUDIO_FORMAT
        )

        return if (minBufferSize > baseBufferSize) minBufferSize else baseBufferSize
    }

    private fun startRecording() {
        if (mediaRecorder != null || audioRecord != null) {
            Log.w("RecordingService", "Start recording called but already recording.")
            updateNotification(formatElapsedTime(calculateElapsedTime()))
            broadcastError("Recording already in progress.")
            return
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e("RecordingService", "RECORD_AUDIO permission not granted.")
            handleError("Microphone permission is required.")
            return
        }

        Log.d("RecordingService", "Attempting to start recording...")
        isPaused = false
        timeWhenPaused = 0L
        currentAmplitude = 0f

        serviceScope.launch {
            var recorderInstance: MediaRecorder? = null
            var audioRecordInstance: AudioRecord? = null
            var success = false
            var outputFile: File? = null

            try {
                audioRecordBufferSize = getAudioRecordBufferSize()
                
                if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    throw SecurityException("RECORD_AUDIO permission check failed unexpectedly before AudioRecord creation.")
                }
                audioRecordInstance = AudioRecord(AUDIO_SOURCE, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, audioRecordBufferSize)

                if (audioRecordInstance.state != AudioRecord.STATE_INITIALIZED) {
                    throw IOException("AudioRecord failed to initialize. State: ${audioRecordInstance.state}")
                }
                audioRecord = audioRecordInstance
                Log.d("RecordingService", "AudioRecord initialized. State: ${audioRecordInstance.state}, BufferSize: $audioRecordBufferSize")


                recorderInstance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(applicationContext)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }

                val fileResult = recordingFileHandler.setupMediaRecorderOutputFile(recorderInstance)
                if (fileResult.isFailure) {
                    throw fileResult.exceptionOrNull() ?: IOException("Unknown error setting up output file")
                }
                outputFile = fileResult.getOrThrow()

                Log.d("RecordingService", "File handler setup successful.")
                mediaRecorder = recorderInstance
                currentRecordingFile = outputFile

                recorderInstance.prepare()
                Log.d("RecordingService", "MediaRecorder prepared.")
                recorderInstance.start()
                Log.d("RecordingService", "MediaRecorder started.")

                audioRecordInstance.startRecording()
                if (audioRecordInstance.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    throw IOException("AudioRecord failed to start recording. State: ${audioRecordInstance.recordingState}")
                }
                Log.d("RecordingService", "AudioRecord started recording.")
                startAudioReadingLoop()

                recordingStartTime = System.currentTimeMillis()

                updateNotification(formatElapsedTime(0L))

                startTimer()
                broadcastStatusUpdate(isRecording = true, isPaused = false, elapsedTimeMillis = 0L, amplitude = 0f)
                Log.d("RecordingService", "Recording started successfully. File: ${outputFile?.absolutePath}")
                Log.d("RecordingQuality", "Recording started with: " +
                        "Quality=${QualitySetting.valueOf(prefs.getString(QualitySetting.PREF_KEY, QualitySetting.DEFAULT) ?: QualitySetting.DEFAULT)}, " +
                        "Format=AAC/M4A, " +
                        "OutputFile=${outputFile?.absolutePath}")
                success = true

            } catch (e: SecurityException) {
                handleError("Security error during recording setup. Check permissions.", e)
                releaseAudioRecord()
                recorderInstance?.release()
                mediaRecorder = null
            } catch (e: IOException) {
                handleError("MediaRecorder/AudioRecord setup failed (IO): ${e.message}", e)
                releaseAudioRecord()
                recorderInstance?.release()
                mediaRecorder = null
            } catch (e: IllegalStateException) {
                handleError("MediaRecorder/AudioRecord state error during start: ${e.message}", e)
                releaseAudioRecord()
                recorderInstance?.release()
                mediaRecorder = null
            } catch (e: Exception) {
                handleError("An unexpected error occurred during startRecording: ${e.message}", e)
                releaseAudioRecord()
                recorderInstance?.release()
                mediaRecorder = null
            } finally {
                if (!success) {
                    Log.w("RecordingService", "Start recording failed, stopping service.")
                    outputFile?.delete()
                    currentRecordingFile = null
                    releaseAudioRecord()
                    releaseMediaRecorder()
                    withContext(Dispatchers.Main) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
    }

    private fun startAudioReadingLoop() {
        audioRecordJob?.cancel()
        audioRecordJob = audioRecordScope.launch {
            val currentAudioRecord = audioRecord
            val bufferSize = audioRecordBufferSize
            if (currentAudioRecord == null || bufferSize <= 0) {
                Log.e("RecordingService", "AudioRecord not initialized or invalid buffer size in reading loop.")
                return@launch
            }

            val audioBuffer = ShortArray(bufferSize / 2)
            var lastAmplitudeUpdateTime = 0L

            Log.d("RecordingService", "Audio reading loop started.")
            while (isActive && currentAudioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val readResult = currentAudioRecord.read(audioBuffer, 0, audioBuffer.size)

                if (readResult > 0) {
                    val now = System.currentTimeMillis()
                    if (now - lastAmplitudeUpdateTime >= AMPLITUDE_UPDATE_INTERVAL_MS) {
                        val rms = calculateRMS(audioBuffer, readResult)
                        val newAmplitude = (rms / Short.MAX_VALUE.toDouble()).toFloat().coerceIn(0f, 1f)

                        currentAmplitude = newAmplitude
                        broadcastStatusUpdate(
                            isRecording = true,
                            isPaused = false,
                            elapsedTimeMillis = calculateElapsedTime(),
                            amplitude = currentAmplitude
                        )

                        lastAmplitudeUpdateTime = now
                    }
                } else if (readResult < 0) {
                    Log.e("RecordingService", "AudioRecord read error: $readResult. Stopping reading loop.")
                    break
                }
                if (readResult == 0) delay(20)
            }
            Log.d("RecordingService", "Audio reading loop finished. isActive=$isActive, recordingState=${currentAudioRecord.recordingState}")
            currentAmplitude = 0f
            if (mediaRecorder != null || audioRecord != null) {
                broadcastStatusUpdate(
                    isRecording = true,
                    isPaused = isPaused,
                    elapsedTimeMillis = calculateElapsedTime(),
                    amplitude = 0f
                )
            }
        }
    }

    private fun calculateRMS(buffer: ShortArray, readSize: Int): Double {
        if (readSize <= 0) return 0.0
        var sumOfSquares: Double = 0.0
        for (i in 0 until readSize) {
            sumOfSquares += buffer[i].toDouble() * buffer[i].toDouble()
        }
        val meanSquare = sumOfSquares / readSize
        return sqrt(meanSquare)
    }

    private fun stopAudioReadingLoop() {
        if (audioRecordJob?.isActive == true) {
            Log.d("RecordingService", "Requesting cancellation of audio reading loop...")
            audioRecordJob?.cancel()
        }
        audioRecordJob = null
        currentAmplitude = 0f
        Log.d("RecordingService", "Audio reading loop stop requested.")
    }


    private fun pauseRecording() {
        if (mediaRecorder == null || audioRecord == null || isPaused) {
            Log.w("RecordingService", "Pause called but not recording or already paused.")
            return
        }

        Log.d("RecordingService", "Attempting to pause recording...")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mediaRecorder?.pause()
                Log.d("RecordingService", "MediaRecorder paused.")
            } else {
                Log.w("RecordingService", "MediaRecorder.pause() not supported on this API level. Recording will continue for file.")
            }

            stopAudioReadingLoop()

            isPaused = true
            timeWhenPaused = System.currentTimeMillis()
            stopTimer()

            val elapsedMillis = calculateElapsedTime()
            updateNotification(formatElapsedTime(elapsedMillis))
            broadcastStatusUpdate(isRecording = true, isPaused = true, elapsedTimeMillis = elapsedMillis, amplitude = 0f)
            Log.d("RecordingService", "Recording paused state set at ${formatElapsedTime(elapsedMillis)}")
        } catch (e: IllegalStateException) {
            handleError("Failed to pause MediaRecorder: ${e.message}", e)
        } catch (e: Exception) {
            handleError("An unexpected error occurred during pauseRecording: ${e.message}", e)
        }
    }

    private fun resumeRecording() {
        if (mediaRecorder == null || audioRecord == null || !isPaused) {
            Log.w("RecordingService", "Resume called but not recording or not paused.")
            return
        }

        Log.d("RecordingService", "Attempting to resume recording...")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mediaRecorder?.resume()
                Log.d("RecordingService", "MediaRecorder resumed.")
            } else {
                Log.w("RecordingService", "MediaRecorder.resume() not supported on this API level.")
            }

            startAudioReadingLoop()

            isPaused = false
            val pauseDuration = System.currentTimeMillis() - timeWhenPaused
            recordingStartTime += pauseDuration
            timeWhenPaused = 0L

            startTimer()
            val elapsedMillis = calculateElapsedTime()
            updateNotification(formatElapsedTime(elapsedMillis))
            broadcastStatusUpdate(isRecording = true, isPaused = false, elapsedTimeMillis = elapsedMillis, amplitude = currentAmplitude)
            Log.d("RecordingService", "Recording resumed.")
        } catch (e: IllegalStateException) {
            handleError("Failed to resume MediaRecorder: ${e.message}", e)
        } catch (e: Exception) {
            handleError("An unexpected error occurred during resumeRecording: ${e.message}", e)
        }
    }

    private fun stopRecording() {
        if (mediaRecorder == null && audioRecord == null) {
            Log.w("RecordingService", "Stop recording called but not recording.")
            return
        }

        Log.d("RecordingService", "Attempting to stop recording...")
        stopTimer()
        stopAudioReadingLoop()

        val finalDuration = calculateElapsedTime()
        val fileToSave = currentRecordingFile

        if (isPaused && mediaRecorder != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                mediaRecorder?.resume()
                Log.d("RecordingService", "Briefly resumed MediaRecorder before stopping.")
            } catch (e: IllegalStateException) {
                Log.e("RecordingService", "Error resuming MediaRecorder before stop: ${e.message}", e)
            }
        }
        isPaused = false

        serviceScope.launch {
            var stoppedWithError = false
            try {
                mediaRecorder?.apply {
                    stop()
                    Log.d("RecordingService", "MediaRecorder stopped.")
                }
                releaseMediaRecorder()

                releaseAudioRecord()

                val fileSizeBytes = fileToSave?.length() ?: 0
                val fileSizeKB = fileSizeBytes / 1024
                
                val quality = QualitySetting.valueOf(
                    prefs.getString(QualitySetting.PREF_KEY, QualitySetting.DEFAULT) 
                        ?: QualitySetting.DEFAULT
                )
                
                val durationSeconds = finalDuration / 1000.0
                val bitrateBps = if (durationSeconds > 0) (fileSizeBytes * 8 / durationSeconds).toInt() else 0
                
                Log.d("RecordingQuality", "Recording completed: " +
                        "Quality=$quality, " +
                        "Duration=${formatElapsedTime(finalDuration)}, " +
                        "FileSize=${fileSizeKB}KB, " +
                        "ActualBitrate=${bitrateBps/1000}kbps")

            } catch (e: IllegalStateException) {
                Log.e("RecordingService", "IllegalStateException stopping recording: ${e.message}", e)
                stoppedWithError = true
                releaseMediaRecorder()
                releaseAudioRecord()
            } catch (e: RuntimeException) {
                Log.e("RecordingService", "RuntimeException stopping recording: ${e.message}", e)
                stoppedWithError = true
                releaseMediaRecorder()
                releaseAudioRecord()
            } catch (e: Exception) {
                Log.e("RecordingService", "Unexpected error stopping recording: ${e.message}", e)
                stoppedWithError = true
                releaseMediaRecorder()
                releaseAudioRecord()
            } finally {
                if (!stoppedWithError && fileToSave != null) {
                    Log.d("RecordingService", "Recording finished successfully. Path: ${fileToSave.absolutePath}, Duration: $finalDuration")
                    broadcastRecordingFinished(fileToSave.absolutePath, finalDuration)
                } else if (stoppedWithError) {
                    if (fileToSave?.exists() == true && finalDuration > 500) {
                        Log.w("RecordingService", "Stopped with error, but file seems partially saved. Broadcasting finished.")
                        broadcastRecordingFinished(fileToSave.absolutePath, finalDuration)
                    } else {
                        Log.w("RecordingService", "Stopped with error, deleting potentially corrupt file.")
                        fileToSave?.delete()
                        broadcastError("Recording stopped due to an error.")
                    }
                } else {
                    Log.w("RecordingService", "Recording stopped, but file path was null. No file saved.")
                    broadcastStatusUpdate(isRecording = false, isPaused = false, elapsedTimeMillis = 0L, amplitude = 0f)
                }

                withContext(Dispatchers.Main.immediate) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                Log.d("RecordingService", "Service stopping after stopRecording.")
            }
        }
    }

    private fun cancelRecording() {
        if (mediaRecorder == null && audioRecord == null) {
            Log.w("RecordingService", "Cancel called but not recording.")
            return
        }
        Log.d("RecordingService", "Attempting to cancel recording...")
        stopTimer()
        stopAudioReadingLoop()

        val fileToDelete = currentRecordingFile
        currentRecordingFile = null
        isPaused = false
        timeWhenPaused = 0L

        serviceScope.launch {
            releaseMediaRecorder()
            releaseAudioRecord()

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

            withContext(Dispatchers.Main.immediate) {
                stopForeground(STOP_FOREGROUND_REMOVE)
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
            Log.d("RecordingService", "Timer Started (for Elapsed Time)")
            while (isActive && (mediaRecorder != null || audioRecord != null) && !isPaused) {
                val elapsedMillis = calculateElapsedTime()
                updateNotification(formatElapsedTime(elapsedMillis))

                delay(1000L)
            }
            Log.d("RecordingService", "Timer loop finished (isActive=$isActive, mediaRecorder=${mediaRecorder != null}, audioRecord=${audioRecord != null} isPaused=$isPaused).")
        }
    }

    private fun stopTimer() {
        if (timerJob?.isActive == true) {
            timerJob?.cancel()
            Log.d("RecordingService", "Timer Stopped/Cancelled")
        }
        timerJob = null
    }

    private fun calculateElapsedTime(): Long {
        if (recordingStartTime == 0L) return 0L
        return if (isPaused) {
            timeWhenPaused - recordingStartTime
        } else {
            System.currentTimeMillis() - recordingStartTime
        }
    }


    private fun createNotification(contentText: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)

        val largeIconBitmap = BitmapFactory.decodeResource(resources, R.mipmap.ic_audioscholar)

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title_recording))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_mic)
            .setLargeIcon(largeIconBitmap)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setOnlyAlertOnce(true)
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
        if (mediaRecorder != null || audioRecord != null) {
            val notification = createNotification(contentText)
            try {
                notificationManager.notify(NOTIFICATION_ID, notification)
            } catch (e: Exception) {
                Log.e("RecordingService", "Error updating notification: ${e.message}", e)
            }
        } else {
            Log.w("RecordingService", "Skipped notification update as recording resources are null.")
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

    private fun releaseAudioRecord() {
        Log.d("RecordingService", "Attempting to release AudioRecord.")
        stopAudioReadingLoop()

        audioRecord?.apply {
            try {
                if (state == AudioRecord.STATE_INITIALIZED) {
                    if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        stop()
                        Log.d("RecordingService", "AudioRecord stopped.")
                    }
                }
                release()
                Log.d("RecordingService", "AudioRecord released successfully.")
            } catch (e: Exception) {
                Log.e("RecordingService", "Error releasing AudioRecord: ${e.message}", e)
            }
        }
        audioRecord = null
        currentAmplitude = 0f
    }


    private fun handleError(message: String, throwable: Throwable? = null) {
        Log.e("RecordingService", "Error: $message", throwable)
        stopTimer()
        stopAudioReadingLoop()
        serviceScope.launch {
            releaseMediaRecorder()
            releaseAudioRecord()
        }
        broadcastError(message)
        isPaused = false
        timeWhenPaused = 0L
        currentAmplitude = 0f
        serviceScope.launch(Dispatchers.Main.immediate) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun broadcastStatusUpdate(isRecording: Boolean, isPaused: Boolean, elapsedTimeMillis: Long, amplitude: Float) {
        val intent = Intent(BROADCAST_ACTION_STATUS_UPDATE).apply {
            putExtra(EXTRA_IS_RECORDING, isRecording)
            putExtra(EXTRA_IS_PAUSED, isPaused)
            putExtra(EXTRA_ELAPSED_TIME_MILLIS, elapsedTimeMillis)
            putExtra(EXTRA_CURRENT_AMPLITUDE, amplitude)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun broadcastError(errorMessage: String) {
        val intent = Intent(BROADCAST_ACTION_STATUS_UPDATE).apply {
            putExtra(EXTRA_IS_RECORDING, false)
            putExtra(EXTRA_IS_PAUSED, false)
            putExtra(EXTRA_ELAPSED_TIME_MILLIS, 0L)
            putExtra(EXTRA_CURRENT_AMPLITUDE, 0f)
            putExtra(EXTRA_ERROR_MESSAGE, errorMessage)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        Log.d("RecordingService", "Broadcast Error: $errorMessage")
    }

    private fun broadcastRecordingFinished(filePath: String, durationMillis: Long) {
        val intent = Intent(BROADCAST_ACTION_STATUS_UPDATE).apply {
            putExtra(EXTRA_IS_RECORDING, false)
            putExtra(EXTRA_IS_PAUSED, false)
            putExtra(EXTRA_RECORDING_FINISHED_PATH, filePath)
            putExtra(EXTRA_RECORDING_FINISHED_DURATION, durationMillis)
            putExtra(EXTRA_ELAPSED_TIME_MILLIS, durationMillis)
            putExtra(EXTRA_CURRENT_AMPLITUDE, 0f)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        Log.d("RecordingService", "Broadcast Finished: path=$filePath, duration=$durationMillis")
    }

    private fun broadcastRecordingCancelled() {
        val intent = Intent(BROADCAST_ACTION_STATUS_UPDATE).apply {
            putExtra(EXTRA_IS_RECORDING, false)
            putExtra(EXTRA_IS_PAUSED, false)
            putExtra(EXTRA_RECORDING_CANCELLED, true)
            putExtra(EXTRA_ELAPSED_TIME_MILLIS, 0L)
            putExtra(EXTRA_CURRENT_AMPLITUDE, 0f)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        Log.d("RecordingService", "Broadcast Cancelled")
    }


    override fun onDestroy() {
        super.onDestroy()
        Log.d("RecordingService", "Service Destroyed")
        releaseMediaRecorder()
        releaseAudioRecord()
        timerJob?.cancel()
        audioRecordScope.cancel()
        serviceJob.cancel()
        Log.d("RecordingService", "Scopes and jobs cancelled.")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}