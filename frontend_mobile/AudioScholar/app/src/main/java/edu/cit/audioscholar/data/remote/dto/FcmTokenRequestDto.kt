package edu.cit.audioscholar.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class FcmTokenRequestDto(
    val token: String
) 