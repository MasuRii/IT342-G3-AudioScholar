package edu.cit.audioscholar.data.remote.dto

data class RegistrationRequest(
    val firstName: String,
    val lastName: String,
    val email: String,
    val password: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class AuthResponse(
    val token: String? = null,
    val message: String? = null,
    val success: Boolean? = null,
    val userId: String? = null
)

data class FirebaseTokenRequest(
    val idToken: String
)

data class GitHubCodeRequest(
    val code: String,
    val state: String?
)
