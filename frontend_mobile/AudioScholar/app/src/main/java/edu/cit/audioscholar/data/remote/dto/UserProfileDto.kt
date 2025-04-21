package edu.cit.audioscholar.data.remote.dto

import com.google.gson.annotations.SerializedName

data class UserProfileDto(
    @SerializedName("userId")
    val userId: String?,

    @SerializedName("email")
    val email: String?,

    @SerializedName("displayName")
    val displayName: String?,

    @SerializedName("profileImageUrl")
    val profileImageUrl: String?
)