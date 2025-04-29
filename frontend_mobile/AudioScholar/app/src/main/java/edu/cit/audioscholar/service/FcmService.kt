package edu.cit.audioscholar.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import edu.cit.audioscholar.R
import edu.cit.audioscholar.UPLOAD_CHANNEL_ID
import edu.cit.audioscholar.data.repository.UserRepository
import edu.cit.audioscholar.domain.repository.NotificationRepository
import edu.cit.audioscholar.ui.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random
import edu.cit.audioscholar.util.TokenEventBus
import edu.cit.audioscholar.util.Resource
import edu.cit.audioscholar.util.ProcessingEventBus

const val NAVIGATE_TO_EXTRA = "NAVIGATE_TO"
const val RECORDING_ID_EXTRA = "RECORDING_ID"
const val SUMMARY_ID_EXTRA = "SUMMARY_ID"
const val UPLOAD_SCREEN_VALUE = "UPLOAD_SCREEN"
const val RECORDING_DETAIL_DESTINATION = "RECORDING_DETAIL"
const val LIBRARY_CLOUD_DESTINATION = "LIBRARY_CLOUD"

const val GENERAL_NOTIFICATION_CHANNEL_ID = "GENERAL_NOTIFICATIONS"
const val PROCESSING_COMPLETE_CHANNEL_ID = "PROCESSING_COMPLETE"

private const val UPLOAD_NOTIFICATION_REQUEST_CODE = 1
private const val PROCESSING_COMPLETE_REQUEST_CODE = 2

@AndroidEntryPoint
class FcmService : FirebaseMessagingService() {

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var notificationRepository: NotificationRepository

    @Inject
    lateinit var processingEventBus: ProcessingEventBus

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private val tag = "FcmService"

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(tag, "From: ${remoteMessage.from}")

        if (remoteMessage.data.isNotEmpty()) {
            Log.d(tag, "Message data payload: " + remoteMessage.data)
            val messageType = remoteMessage.data["type"]

            if (messageType == "processingComplete") {
                val recordingId = remoteMessage.data["recordingId"]
                val summaryId = remoteMessage.data["summaryId"]
                Log.i(tag, "Received processing complete notification data: recordingId=$recordingId, summaryId=$summaryId")

                if (!recordingId.isNullOrBlank()) {
                    scope.launch {
                        Log.i(tag, "[FCM RECEIVE] About to signal ProcessingEventBus for recordingId: $recordingId")
                        val emitted = processingEventBus.signalProcessingComplete(recordingId)
                        if (emitted) {
                            Log.d(tag, "Successfully signaled ProcessingEventBus.")
                        } else {
                            Log.w(tag, "Failed to signal ProcessingEventBus (buffer full?).")
                        }
                    }
                    sendProcessingCompleteNotification(recordingId)
                } else {
                    Log.w(tag, "Processing complete message received but recordingId is missing. Cannot process.")
                }
                return
            }
        }

        var title: String? = null
        var body: String? = null
        remoteMessage.notification?.let {
            Log.d(tag, "Message Notification Payload: Title=${it.title}, Body=${it.body}")
            title = it.title
            body = it.body
        }

        if (title != null || body != null) {
            sendGeneralNotification(title, body, remoteMessage.data[NAVIGATE_TO_EXTRA] ?: UPLOAD_SCREEN_VALUE)
        } else {
            Log.w(tag, "Received message without data payload or notification payload. Ignoring.")
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(tag, "FCM Registration Token Refreshed: $token")

        TokenEventBus.postNewToken(token)
        Log.d(tag, "Posted refreshed token to TokenEventBus flow.")

        scope.launch {
            val result = notificationRepository.registerFcmToken(token)
            when (result) {
                is Resource.Success -> {
                    Log.i(tag, "Successfully sent refreshed FCM token to backend via NotificationRepository.")
                }
                is Resource.Error -> {
                    Log.e(tag, "Failed to send refreshed FCM token to backend via NotificationRepository: ${result.message}")
                }
                else -> { }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    private fun sendProcessingCompleteNotification(recordingId: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(NAVIGATE_TO_EXTRA, LIBRARY_CLOUD_DESTINATION)
            putExtra(RECORDING_ID_EXTRA, recordingId)
        }

        val pendingIntentFlag = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val pendingIntent = PendingIntent.getActivity(
            this,
            PROCESSING_COMPLETE_REQUEST_CODE,
            intent,
            pendingIntentFlag
        )

        val notificationBuilder = NotificationCompat.Builder(this, PROCESSING_COMPLETE_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_audioscholar)
            .setContentTitle("Processing Complete")
            .setContentText("Your audio recording is ready.")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val notificationId = recordingId.hashCode()
        notificationManager.notify(notificationId, notificationBuilder.build())
        Log.i(tag, "Processing complete notification sent for recordingId: $recordingId")
    }

    private fun sendGeneralNotification(title: String?, messageBody: String?, navigateTo: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(NAVIGATE_TO_EXTRA, navigateTo)
        }

        val pendingIntentFlag = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val pendingIntent = PendingIntent.getActivity(
            this,
            UPLOAD_NOTIFICATION_REQUEST_CODE,
            intent,
            pendingIntentFlag
        )

        val notificationBuilder = NotificationCompat.Builder(this, GENERAL_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_audioscholar)
            .setContentTitle(title ?: "AudioScholar")
            .setContentText(messageBody ?: "New notification")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val notificationId = Random.nextInt()
        notificationManager.notify(notificationId, notificationBuilder.build())
        Log.d(tag, "General notification sent. Title=$title, Body=$messageBody, NavigateTo=$navigateTo")
    }
}