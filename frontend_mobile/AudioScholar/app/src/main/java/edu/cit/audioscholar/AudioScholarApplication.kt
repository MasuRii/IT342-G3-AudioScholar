package edu.cit.audioscholar

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp
import edu.cit.audioscholar.service.RecordingService

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

            val uploadChannelName = getString(R.string.notification_channel_name_upload)
            val uploadChannelDescription = getString(R.string.notification_channel_desc_upload)
            val uploadChannelImportance = NotificationManager.IMPORTANCE_DEFAULT
            val uploadChannel = NotificationChannel(UPLOAD_CHANNEL_ID, uploadChannelName, uploadChannelImportance).apply {
                description = uploadChannelDescription
            }
            notificationManager.createNotificationChannel(uploadChannel)

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