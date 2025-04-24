package edu.cit.audioscholar.data.remote.dto

import com.google.gson.annotations.SerializedName

data class GlossaryItemDto(
    @SerializedName("term")
    val term: String? = null,

    @SerializedName("definition")
    val definition: String? = null
)