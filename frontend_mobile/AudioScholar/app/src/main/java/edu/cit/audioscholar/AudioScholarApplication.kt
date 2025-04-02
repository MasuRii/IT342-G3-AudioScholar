package edu.cit.audioscholar

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

const val UPLOAD_CHANNEL_ID = "upload_status_channel"

@HiltAndroidApp
class AudioScholarApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val uploadChannelName = "Upload Status"
            val uploadChannelDescription = "Notifications about the status of your audio uploads."
            val uploadChannelImportance = NotificationManager.IMPORTANCE_DEFAULT
            val uploadChannel = NotificationChannel(UPLOAD_CHANNEL_ID, uploadChannelName, uploadChannelImportance).apply {
                description = uploadChannelDescription
            }

            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            notificationManager.createNotificationChannel(uploadChannel)
        }
    }
}