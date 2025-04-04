package edu.cit.audioscholar.data.remote.service

import edu.cit.audioscholar.data.remote.dto.AudioMetadataDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ApiService {

    @Multipart
    @POST("/api/audio/upload")
    suspend fun uploadAudio(
        @Part file: MultipartBody.Part,
        @Part("title") title: RequestBody?,
        @Part("description") description: RequestBody?
    ): Response<AudioMetadataDto>

}