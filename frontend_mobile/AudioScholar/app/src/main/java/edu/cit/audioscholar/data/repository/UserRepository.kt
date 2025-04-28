package edu.cit.audioscholar.data.repository

import android.util.Log
import edu.cit.audioscholar.data.remote.dto.FcmTokenRequestDto
import edu.cit.audioscholar.data.remote.service.ApiService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val apiService: ApiService
) {
    private val tag = "UserRepository"

    suspend fun registerFcmToken(token: String): Boolean {
        Log.d(tag, "Attempting to register FCM token with backend.")
        if (token.isBlank()) {
            Log.w(tag, "FCM token is blank, cannot register.")
            return false
        }
        return try {
            val request = FcmTokenRequestDto(token = token)
            val response = apiService.registerFcmToken(request)
            if (response.isSuccessful) {
                Log.i(tag, "FCM token successfully registered with backend.")
                true
            } else {
                val errorBody = response.errorBody()?.string() ?: "No error body"
                Log.e(tag, "Failed to register FCM token. Code: ${response.code()}, Error: $errorBody")
                false
            }
        } catch (e: Exception) {
            Log.e(tag, "Exception while registering FCM token: ${e.message}", e)
            false
        }
    }

} 