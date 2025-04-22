package edu.cit.audioscholar.domain.repository

import android.net.Uri
import edu.cit.audioscholar.data.remote.dto.AuthResponse
import edu.cit.audioscholar.data.remote.dto.ChangePasswordRequest
import edu.cit.audioscholar.data.remote.dto.FirebaseTokenRequest
import edu.cit.audioscholar.data.remote.dto.GitHubCodeRequest
import edu.cit.audioscholar.data.remote.dto.LoginRequest
import edu.cit.audioscholar.data.remote.dto.RegistrationRequest
import edu.cit.audioscholar.data.remote.dto.UpdateUserProfileRequest
import edu.cit.audioscholar.data.remote.dto.UserProfileDto
import edu.cit.audioscholar.util.Resource
import kotlinx.coroutines.flow.Flow

typealias AuthResult = Resource<AuthResponse>
typealias UserProfileResult = Resource<UserProfileDto>
typealias SimpleResult = Resource<Unit>

interface AuthRepository {
    suspend fun registerUser(request: RegistrationRequest): AuthResult
    suspend fun loginUser(request: LoginRequest): AuthResult
    suspend fun verifyFirebaseToken(request: FirebaseTokenRequest): AuthResult
    suspend fun verifyGoogleToken(request: FirebaseTokenRequest): AuthResult
    suspend fun verifyGitHubCode(request: GitHubCodeRequest): AuthResult

    fun getUserProfile(): Flow<UserProfileResult>

    suspend fun updateUserProfile(request: UpdateUserProfileRequest): UserProfileResult
    suspend fun uploadAvatar(imageUri: Uri): UserProfileResult

    suspend fun changePassword(request: ChangePasswordRequest): SimpleResult

    suspend fun clearLocalUserCache()
}