package edu.cit.audioscholar.ui.subscription

import android.net.Uri
import edu.cit.audioscholar.data.remote.dto.*
import edu.cit.audioscholar.domain.repository.AuthRepository
import edu.cit.audioscholar.util.Resource
import kotlinx.coroutines.flow.flowOf

class PreviewAuthRepository : AuthRepository {
    override suspend fun registerUser(request: RegistrationRequest) = 
        Resource.Loading<AuthResponse>()
        
    override suspend fun loginUser(request: LoginRequest) = 
        Resource.Loading<AuthResponse>()
        
    override suspend fun verifyFirebaseToken(request: FirebaseTokenRequest) = 
        Resource.Loading<AuthResponse>()
        
    override suspend fun verifyGoogleToken(request: FirebaseTokenRequest) = 
        Resource.Loading<AuthResponse>()
        
    override suspend fun verifyGitHubCode(request: GitHubCodeRequest) = 
        Resource.Loading<AuthResponse>()
        
    override fun getUserProfile() = 
        flowOf(Resource.Loading<UserProfileDto>())
        
    override suspend fun updateUserProfile(request: UpdateUserProfileRequest) = 
        Resource.Loading<UserProfileDto>()
        
    override suspend fun uploadAvatar(imageUri: Uri) = 
        Resource.Loading<UserProfileDto>()
        
    override suspend fun updateUserRole(userId: String, role: String) = 
        Resource.Loading<Unit>()
        
    override suspend fun changePassword(request: ChangePasswordRequest) = 
        Resource.Loading<Unit>()
        
    override suspend fun logout() = 
        Resource.Loading<Unit>()
        
    override suspend fun clearLocalUserCache() {}
} 