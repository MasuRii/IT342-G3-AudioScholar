package edu.cit.audioscholar.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ChangePasswordRequest(

    @SerializedName("newPassword")
    val newPassword: String
)