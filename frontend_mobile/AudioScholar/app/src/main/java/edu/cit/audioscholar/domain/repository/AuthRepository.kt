package edu.cit.audioscholar.domain.repository

import edu.cit.audioscholar.data.remote.dto.AuthResponse
import edu.cit.audioscholar.data.remote.dto.FirebaseTokenRequest
import edu.cit.audioscholar.data.remote.dto.GitHubCodeRequest
import edu.cit.audioscholar.data.remote.dto.LoginRequest
import edu.cit.audioscholar.data.remote.dto.RegistrationRequest
import edu.cit.audioscholar.data.remote.dto.UserProfileDto
import edu.cit.audioscholar.util.Resource

typealias AuthResult = Resource<AuthResponse>

interface AuthRepository {
    suspend fun registerUser(request: RegistrationRequest): AuthResult
    suspend fun loginUser(request: LoginRequest): AuthResult
    suspend fun verifyFirebaseToken(request: FirebaseTokenRequest): AuthResult
    suspend fun verifyGoogleToken(request: FirebaseTokenRequest): AuthResult
    suspend fun verifyGitHubCode(request: GitHubCodeRequest): AuthResult
    suspend fun getUserProfile(): Resource<UserProfileDto>
}