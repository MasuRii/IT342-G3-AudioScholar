package edu.cit.audioscholar.domain.repository

import edu.cit.audioscholar.data.remote.dto.FcmTokenRequestDto
import edu.cit.audioscholar.util.Resource
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import android.content.SharedPreferences
import android.util.Log

class NotificationRepositoryImpl @Inject constructor(
    private val httpClient: HttpClient,
    private val prefs: SharedPreferences
) : NotificationRepository {

    private val tag = "NotificationRepoImpl"
    private val fcmTokenEndpoint = "/api/users/me/fcm-token"

    override suspend fun registerFcmToken(token: String): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(tag, "Attempting to register FCM token: $token")
            val requestDto = FcmTokenRequestDto(token)

            val authToken = prefs.getString("auth_token", null)

            val response = httpClient.post("/api/users/me/fcm-token") {
                contentType(ContentType.Application.Json)
                setBody(requestDto)
                if (!authToken.isNullOrBlank()) {
                    Log.d(tag, "Manually adding Authorization header to FCM token request.")
                    header("Authorization", "Bearer $authToken")
                } else {
                    Log.w(tag, "Auth token not found in prefs when registering FCM token. Request might fail.")
                }
            }

            if (response.status.value in 200..299) {
                Log.i(tag, "FCM token registered successfully with backend. Status: ${response.status}")
                Resource.Success(Unit)
            } else {
                val errorBody = try { response.body<String>() } catch (e: Exception) { "Could not read error body." }
                Log.e(tag, "Failed to register FCM token. Status: ${response.status.value}. Body: $errorBody")
                Resource.Error("Failed to register FCM token. Status: ${response.status.value}. Body: $errorBody", null)
            }
        } catch (e: Exception) {
            Log.e(tag, "Exception registering FCM token", e)
            Resource.Error("Exception registering FCM token: ${e.localizedMessage ?: e.message}", null)
        }
    }
} 