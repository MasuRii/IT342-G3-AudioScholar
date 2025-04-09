package edu.cit.audioscholar.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import edu.cit.audioscholar.R
import edu.cit.audioscholar.UPLOAD_CHANNEL_ID
import edu.cit.audioscholar.ui.main.MainActivity
import kotlin.random.Random

const val NAVIGATE_TO_EXTRA = "NAVIGATE_TO"
const val UPLOAD_SCREEN_VALUE = "UPLOAD_SCREEN"
private const val UPLOAD_NOTIFICATION_REQUEST_CODE = 1

class FcmService : FirebaseMessagingService() {

    private val tag = "FcmService"

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(tag, "From: ${remoteMessage.from}")

        var title: String? = null
        var body: String? = null
        var navigateTo: String? = null

        if (remoteMessage.data.isNotEmpty()) {
            Log.d(tag, "Message data payload: " + remoteMessage.data)
            navigateTo = remoteMessage.data[NAVIGATE_TO_EXTRA]
        }

        remoteMessage.notification?.let {
            Log.d(tag, "Message Notification Payload: Title=${it.title}, Body=${it.body}")
            if (title == null) title = it.title
            if (body == null) body = it.body
        }

        if (title != null || body != null) {
            sendNotification(title, body, navigateTo ?: UPLOAD_SCREEN_VALUE)
        } else {
            Log.w(tag, "No title/body found in notification or data payload. Notification not shown.")
        }
    }

    override fun onNewToken(token: String) {
        Log.d(tag, "Refreshed FCM token: $token")
    }

    private fun sendNotification(title: String?, messageBody: String?, navigateTo: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(NAVIGATE_TO_EXTRA, navigateTo)
        }

        val pendingIntentFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        Log.d(tag, "Creating PendingIntent with flags: $pendingIntentFlag. Intent extras: ${intent.extras}")

        val pendingIntent = PendingIntent.getActivity(
            this,
            UPLOAD_NOTIFICATION_REQUEST_CODE,
            intent,
            pendingIntentFlag
        )

        val notificationBuilder = NotificationCompat.Builder(this, UPLOAD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(title ?: "AudioScholar")
            .setContentText(messageBody ?: "New notification")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val notificationId = Random.nextInt()
        notificationManager.notify(notificationId, notificationBuilder.build())
        Log.d(tag, "Notification sent. Title=$title, Body=$messageBody, NavigateTo=$navigateTo")
    }
}