package edu.cit.audioscholar.data.remote.dto

data class RegistrationRequest(
    val firstName: String,
    val lastName: String,
    val email: String,
    val password: String
)

data class AuthResponse(
    val message: String? = null,
)