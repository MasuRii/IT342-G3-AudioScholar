package edu.cit.audioscholar.data.remote.dto

import com.google.gson.annotations.SerializedName

data class UpdateUserProfileRequest(
    @SerializedName("firstName")
    val firstName: String?,

    @SerializedName("lastName")
    val lastName: String?,

    @SerializedName("displayName")
    val displayName: String?,

    @SerializedName("profileImageUrl")
    val profileImageUrl: String?
)