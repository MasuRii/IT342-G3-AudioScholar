package edu.cit.audioscholar.util

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

object FcmTokenProvider {

    private const val TAG = "FcmTokenProvider"

    suspend fun getCurrentToken(): String? {
        return try {
            val token = FirebaseMessaging.getInstance().token.await()
            Log.d(TAG, "FCM token retrieved successfully via FcmTokenProvider: $token")
            token
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve FCM token via FcmTokenProvider", e)
            null
        }
    }
} 