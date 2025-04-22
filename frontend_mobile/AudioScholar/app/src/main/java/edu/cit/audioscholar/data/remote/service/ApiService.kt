package edu.cit.audioscholar.data.remote.service

import edu.cit.audioscholar.data.remote.dto.AudioMetadataDto
import edu.cit.audioscholar.data.remote.dto.AuthResponse
import edu.cit.audioscholar.data.remote.dto.ChangePasswordRequest
import edu.cit.audioscholar.data.remote.dto.FirebaseTokenRequest
import edu.cit.audioscholar.data.remote.dto.GitHubCodeRequest
import edu.cit.audioscholar.data.remote.dto.LoginRequest
import edu.cit.audioscholar.data.remote.dto.RegistrationRequest
import edu.cit.audioscholar.data.remote.dto.UpdateUserProfileRequest
import edu.cit.audioscholar.data.remote.dto.UserProfileDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part

interface ApiService {

    @Multipart
    @POST("/api/audio/upload")
    suspend fun uploadAudio(
        @Part file: MultipartBody.Part,
        @Part("title") title: RequestBody?,
        @Part("description") description: RequestBody?
    ): Response<AudioMetadataDto>

    @GET("/api/audio/metadata")
    suspend fun getAudioMetadataList(): Response<List<AudioMetadataDto>>

    @POST("/api/auth/register")
    suspend fun registerUser(
        @Body registrationRequest: RegistrationRequest
    ): Response<AuthResponse>

    @POST("/api/auth/login")
    suspend fun loginUser(
        @Body loginRequest: LoginRequest
    ): Response<AuthResponse>

    @POST("/api/auth/verify-firebase-token")
    suspend fun verifyFirebaseToken(
        @Body tokenRequest: FirebaseTokenRequest
    ): Response<AuthResponse>

    @POST("/api/auth/verify-google-token")
    suspend fun verifyGoogleToken(
        @Body tokenRequest: FirebaseTokenRequest
    ): Response<AuthResponse>

    @POST("/api/auth/verify-github-code")
    suspend fun verifyGitHubCode(
        @Body codeRequest: GitHubCodeRequest
    ): Response<AuthResponse>

    @GET("/api/users/me")
    suspend fun getUserProfile(): Response<UserProfileDto>

    @PUT("/api/users/me")
    suspend fun updateUserProfile(
        @Body updateUserProfileRequest: UpdateUserProfileRequest
    ): Response<UserProfileDto>

    @Multipart
    @POST("/api/users/me/avatar")
    suspend fun uploadAvatar(
        @Part avatar: MultipartBody.Part
    ): Response<UserProfileDto>

    @POST("/api/auth/change-password")
    suspend fun changePassword(
        @Body changePasswordRequest: ChangePasswordRequest
    ): Response<AuthResponse>
}