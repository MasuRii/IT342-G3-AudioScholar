package edu.cit.audioscholar.data.remote.service

import edu.cit.audioscholar.data.remote.dto.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

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

    @DELETE("/api/audio/metadata/{id}")
    suspend fun deleteAudioMetadata(
        @Path("id") metadataId: String
    ): Response<Unit>

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

    @POST("/api/auth/logout")
    suspend fun logout(): Response<Unit>

    @GET("/api/recordings/{recordingId}/summary")
    suspend fun getRecordingSummary(
        @Path("recordingId") recordingId: String
    ): Response<SummaryResponseDto>

    @GET("/api/v1/recommendations/recording/{recordingId}")
    suspend fun getRecordingRecommendations(
        @Path("recordingId") recordingId: String
    ): Response<List<RecommendationDto>>

    @GET("/api/recordings/{recordingId}")
    suspend fun getRecordingDetails(
        @Path("recordingId") recordingId: String
    ): Response<AudioMetadataDto>

}