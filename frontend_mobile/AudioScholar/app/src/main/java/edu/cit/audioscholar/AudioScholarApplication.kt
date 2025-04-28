package edu.cit.audioscholar

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp
import edu.cit.audioscholar.service.RecordingService
import edu.cit.audioscholar.service.PROCESSING_COMPLETE_CHANNEL_ID
import edu.cit.audioscholar.service.GENERAL_NOTIFICATION_CHANNEL_ID

const val UPLOAD_CHANNEL_ID = "upload_status_channel"

@HiltAndroidApp
class AudioScholarApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager: NotificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            val generalChannelName = "General Notifications"
            val generalChannelDescription = "General app notifications"
            val generalChannelImportance = NotificationManager.IMPORTANCE_DEFAULT
            val generalChannel = NotificationChannel(GENERAL_NOTIFICATION_CHANNEL_ID, generalChannelName, generalChannelImportance).apply {
                description = generalChannelDescription
            }
            notificationManager.createNotificationChannel(generalChannel)

            val processingChannelName = "Processing Complete"
            val processingChannelDescription = "Notifications for when audio processing is finished"
            val processingChannelImportance = NotificationManager.IMPORTANCE_DEFAULT
            val processingChannel = NotificationChannel(PROCESSING_COMPLETE_CHANNEL_ID, processingChannelName, processingChannelImportance).apply {
                description = processingChannelDescription
            }
            notificationManager.createNotificationChannel(processingChannel)

            val recordingChannelName = getString(R.string.notification_channel_name_recording)
            val recordingChannelDescription = getString(R.string.notification_channel_desc_recording)
            val recordingChannelImportance = NotificationManager.IMPORTANCE_LOW
            val recordingChannel = NotificationChannel(
                RecordingService.NOTIFICATION_CHANNEL_ID,
                recordingChannelName,
                recordingChannelImportance
            ).apply {
                description = recordingChannelDescription
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(recordingChannel)

        }
    }
}