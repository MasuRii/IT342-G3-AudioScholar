package edu.cit.audioscholar.domain.repository

import android.app.Application
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import com.google.gson.Gson
import edu.cit.audioscholar.R
import edu.cit.audioscholar.data.remote.dto.*
import edu.cit.audioscholar.data.remote.service.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.*
import retrofit2.HttpException
import retrofit2.Response
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG_REMOTE_REPO = "RemoteAudioRepoImpl"

@Singleton
class RemoteAudioRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val application: Application,
    private val gson: Gson
) : RemoteAudioRepository {

    override fun uploadAudioFile(
        audioFile: File,
        powerpointFile: File?,
        title: String?,
        description: String?
    ): Flow<UploadResult> = callbackFlow<UploadResult> {
        trySend(UploadResult.Loading)
        Log.d(TAG_REMOTE_REPO, "Starting upload for File: ${audioFile.absolutePath}. PowerPoint: ${powerpointFile?.absolutePath}, Title: '$title', Desc: '$description'")

        val fileName = audioFile.name
        val contentResolver = application.contentResolver
        var mimeType: String? = null

        val fileExtension = fileName.substringAfterLast('.', "").lowercase()
        if (fileExtension.isNotEmpty()) {
            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension)
            Log.d(TAG_REMOTE_REPO, "MIME type from extension '$fileExtension': $mimeType")
        }

        if (mimeType == null) {
            try {
                mimeType = contentResolver.getType(audioFile.toUri())
                Log.d(TAG_REMOTE_REPO, "MIME type from ContentResolver: $mimeType")
            } catch (e: Exception) {
                Log.w(TAG_REMOTE_REPO, "Could not get MIME type from ContentResolver for ${audioFile.toUri()}", e)
            }
        }

        mimeType = mimeType ?: "audio/mpeg"
        Log.d(TAG_REMOTE_REPO, "Final resolved MIME type: $mimeType")

        val mediaType = mimeType.toMediaTypeOrNull()

        if (!audioFile.exists() || !audioFile.canRead()) {
            val error = IOException("File not found or cannot be read: ${audioFile.absolutePath}")
            Log.e(TAG_REMOTE_REPO, error.message, error)
            trySend(UploadResult.Error(error.message ?: "File access error"))
            close(error)
            return@callbackFlow
        }

        val baseRequestBody = audioFile.asRequestBody(mediaType)
        val progressRequestBody = ProgressReportingRequestBody(baseRequestBody) { percentage ->
            trySend(UploadResult.Progress(percentage))
        }

        val filePart = MultipartBody.Part.createFormData(
            "audioFile",
            fileName,
            progressRequestBody
        )

        var pptxPart: MultipartBody.Part? = null
        if (powerpointFile != null && powerpointFile.exists() && powerpointFile.canRead()) {
            val pptxFileName = powerpointFile.name
            val pptxExtension = pptxFileName.substringAfterLast('.', "").lowercase()
            var pptxMimeType: String? = null
            
            if (pptxExtension.isNotEmpty()) {
                pptxMimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(pptxExtension)
                Log.d(TAG_REMOTE_REPO, "PPTX MIME type from extension '$pptxExtension': $pptxMimeType")
            }
            
            if (pptxMimeType == null) {
                try {
                    pptxMimeType = contentResolver.getType(powerpointFile.toUri())
                    Log.d(TAG_REMOTE_REPO, "PPTX MIME type from ContentResolver: $pptxMimeType")
                } catch (e: Exception) {
                    Log.w(TAG_REMOTE_REPO, "Could not get MIME type from ContentResolver for PowerPoint ${powerpointFile.toUri()}", e)
                }
            }
            
            pptxMimeType = pptxMimeType ?: when (pptxExtension) {
                "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                "ppt" -> "application/vnd.ms-powerpoint"
                else -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            }
            
            Log.d(TAG_REMOTE_REPO, "Final resolved PowerPoint MIME type: $pptxMimeType")
            
            val pptxMediaType = pptxMimeType.toMediaTypeOrNull()
            val pptxRequestBody = powerpointFile.asRequestBody(pptxMediaType)
            
            pptxPart = MultipartBody.Part.createFormData(
                "powerpointFile",
                pptxFileName,
                pptxRequestBody
            )
            
            Log.d(TAG_REMOTE_REPO, "PowerPoint part created with filename: '$pptxFileName', MIME Type: '$pptxMimeType'")
        } else if (powerpointFile != null) {
            Log.w(TAG_REMOTE_REPO, "PowerPoint file specified but not accessible: ${powerpointFile.absolutePath}")
        }

        val titlePart: RequestBody? = title?.takeIf { it.isNotBlank() }?.toRequestBody("text/plain".toMediaTypeOrNull())
        val descriptionPart: RequestBody? = description?.takeIf { it.isNotBlank() }?.toRequestBody("text/plain".toMediaTypeOrNull())

        Log.d(TAG_REMOTE_REPO, "Multipart parts created. Filename: '$fileName', MIME Type: '$mimeType'")
        Log.d(TAG_REMOTE_REPO, "PowerPoint part created: ${pptxPart != null}")
        Log.d(TAG_REMOTE_REPO, "Title part created: ${titlePart != null}")
        Log.d(TAG_REMOTE_REPO, "Description part created: ${descriptionPart != null}")

        try {
            Log.d(TAG_REMOTE_REPO, "Executing API call: apiService.uploadAudio")
            val response: Response<AudioMetadataDto> = apiService.uploadAudio(
                file = filePart,
                powerpointFile = pptxPart,
                title = titlePart,
                description = descriptionPart
            )
            Log.d(TAG_REMOTE_REPO, "API call finished. Response code: ${response.code()}")

            if (response.isSuccessful) {
                val responseBody: AudioMetadataDto? = response.body()
                if (responseBody != null) {
                    Log.i(TAG_REMOTE_REPO, "Upload successful. Metadata ID: ${responseBody.id}, Recording ID: ${responseBody.recordingId}")
                    trySend(UploadResult.Success(responseBody))
                } else {
                    Log.w(TAG_REMOTE_REPO, "Upload successful (Code: ${response.code()}) but response body was null.")
                    trySend(UploadResult.Success(null))
                }
                close()
            } else {
                val errorBody = response.errorBody()?.string() ?: application.getString(R.string.upload_error_server_generic)
                Log.e(TAG_REMOTE_REPO, "Upload failed with HTTP error: ${response.code()} - $errorBody")
                val error = HttpException(response)
                val errorMessage = when (response.code()) {
                    415 -> application.getString(R.string.error_unsupported_file_type_detected, mimeType)
                    400 -> application.getString(R.string.error_upload_failed_generic) + " (Bad Request)"
                    401 -> application.getString(R.string.error_unauthorized)
                    403 -> application.getString(R.string.error_upload_failed_generic) + " (Forbidden)"
                    else -> application.getString(R.string.upload_error_server_http, response.code(), errorBody)
                }
                trySend(UploadResult.Error(errorMessage))
                close(error)
            }
        } catch (e: IOException) {
            Log.e(TAG_REMOTE_REPO, "Network/IO exception during upload: ${e.message}", e)
            trySend(UploadResult.Error(application.getString(R.string.upload_error_network_connection)))
            close(e)
        } catch (e: HttpException) {
            Log.e(TAG_REMOTE_REPO, "HTTP exception during upload: ${e.code()} - ${e.message()}", e)
            val errorMessage = when (e.code()) {
                415 -> application.getString(R.string.error_unsupported_file_type_detected, mimeType)
                else -> application.getString(R.string.upload_error_server_http, e.code(), e.message())
            }
            trySend(UploadResult.Error(errorMessage))
            close(e)
        } catch (e: Exception) {
            Log.e(TAG_REMOTE_REPO, "Unexpected exception during upload: ${e.message}", e)
            trySend(UploadResult.Error(application.getString(R.string.upload_error_unexpected, e.message ?: "Unknown error")))
            close(e)
        }

        awaitClose {
            Log.d(TAG_REMOTE_REPO, "Upload flow channel closed.")
        }

    }.flowOn(Dispatchers.IO)


    private class ProgressReportingRequestBody(
        private val delegate: RequestBody,
        private val onProgressUpdate: (percentage: Int) -> Unit
    ) : RequestBody() {

        override fun contentType(): MediaType? = delegate.contentType()

        override fun contentLength(): Long {
            return try {
                delegate.contentLength()
            } catch (e: IOException) {
                Log.e("ProgressRequestBody", "Failed to get content length", e)
                -1
            }
        }

        @Throws(IOException::class)
        override fun writeTo(sink: BufferedSink) {
            val totalBytes = contentLength()
            var bytesWritten: Long = 0
            var lastPercentage = -1

            val countingSink = object : ForwardingSink(sink) {
                @Throws(IOException::class)
                override fun write(source: Buffer, byteCount: Long) {
                    super.write(source, byteCount)

                    bytesWritten += byteCount
                    if (totalBytes > 0) {
                        val percentage = ((bytesWritten * 100) / totalBytes).toInt()
                        if (percentage != lastPercentage && percentage in 0..100) {
                            lastPercentage = percentage
                            Log.v("ProgressRequestBody", "Progress: $percentage%")
                            onProgressUpdate(percentage)
                        }
                    } else if (totalBytes == -1L && lastPercentage != 0) {
                        lastPercentage = 0
                        Log.v("ProgressRequestBody", "Progress: 0% (unknown total size)")
                        onProgressUpdate(0)
                    }
                }
            }

            val bufferedCountingSink = countingSink.buffer()
            delegate.writeTo(bufferedCountingSink)
            bufferedCountingSink.flush()

            if (totalBytes > 0 && bytesWritten == totalBytes && lastPercentage != 100) {
                Log.d("ProgressRequestBody", "Ensuring 100% progress sent at the end.")
                onProgressUpdate(100)
            } else if (totalBytes == -1L && lastPercentage != 100) {
                Log.d("ProgressRequestBody", "Reporting 100% (unknown total size finished)")
                onProgressUpdate(100)
            }
        }
    }

    override fun getCloudRecordings(): Flow<Result<List<AudioMetadataDto>>> = flow {
        try {
            Log.d(TAG_REMOTE_REPO, "Fetching cloud recordings metadata from API: apiService.getAudioMetadataList")
            val response: Response<List<AudioMetadataDto>> = apiService.getAudioMetadataList()

            if (response.isSuccessful) {
                val metadataList: List<AudioMetadataDto> = response.body() ?: emptyList()
                Log.i(TAG_REMOTE_REPO, "Successfully fetched ${metadataList.size} cloud recordings metadata.")
                emit(Result.success(metadataList))
            } else {
                val errorBody = response.errorBody()?.string() ?: application.getString(R.string.upload_error_server_generic)
                Log.e(TAG_REMOTE_REPO, "Failed to fetch cloud recordings: ${response.code()} - $errorBody")
                val exception = mapHttpException("fetch cloud recordings", response.code(), errorBody, HttpException(response))
                emit(Result.failure(exception))
            }
        } catch (e: IOException) {
            Log.e(TAG_REMOTE_REPO, "Network/IO exception fetching cloud recordings: ${e.message}", e)
            emit(Result.failure(IOException(application.getString(R.string.upload_error_network_connection), e)))
        } catch (e: HttpException) {
            Log.e(TAG_REMOTE_REPO, "HTTP exception fetching cloud recordings: ${e.code()} - ${e.message()}", e)
            val exception = mapHttpException("fetch cloud recordings", e.code(), e.message(), e)
            emit(Result.failure(exception))
        } catch (e: Exception) {
            Log.e(TAG_REMOTE_REPO, "Unexpected exception fetching cloud recordings: ${e.message}", e)
            emit(Result.failure(IOException(application.getString(R.string.upload_error_unexpected, e.message ?: "Unknown error"), e)))
        }
    }.flowOn(Dispatchers.IO)

    override fun getSummary(recordingId: String): Flow<Result<SummaryResponseDto>> = flow {
        try {
            Log.d(TAG_REMOTE_REPO, "Fetching summary for recordingId: $recordingId")
            val response: Response<SummaryResponseDto> = apiService.getRecordingSummary(recordingId)

            if (response.isSuccessful) {
                val summaryDto: SummaryResponseDto? = response.body()
                if (summaryDto != null) {
                    Log.i(TAG_REMOTE_REPO, "Successfully fetched summary for recordingId: $recordingId")
                    emit(Result.success(summaryDto))
                } else {
                    Log.w(TAG_REMOTE_REPO, "Summary fetch successful (Code: ${response.code()}) but response body was null.")
                    emit(Result.failure(IOException("Server returned empty summary.")))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG_REMOTE_REPO, "Failed to fetch summary: ${response.code()} - $errorBody")
                val exception = mapHttpException("fetch summary", response.code(), errorBody, HttpException(response))
                emit(Result.failure(exception))
            }
        } catch (e: IOException) {
            Log.e(TAG_REMOTE_REPO, "Network/IO exception fetching summary: ${e.message}", e)
            emit(Result.failure(IOException(application.getString(R.string.upload_error_network_connection), e)))
        } catch (e: HttpException) {
            Log.e(TAG_REMOTE_REPO, "HTTP exception fetching summary: ${e.code()} - ${e.message()}", e)
            val exception = mapHttpException("fetch summary", e.code(), e.message(), e)
            emit(Result.failure(exception))
        } catch (e: Exception) {
            Log.e(TAG_REMOTE_REPO, "Unexpected exception fetching summary: ${e.message}", e)
            emit(Result.failure(IOException(application.getString(R.string.upload_error_unexpected, e.message ?: "Unknown error"), e)))
        }
    }.flowOn(Dispatchers.IO)

    override fun getRecommendations(recordingId: String): Flow<Result<List<RecommendationDto>>> = flow {
        try {
            Log.d(TAG_REMOTE_REPO, "Fetching recommendations for recordingId: $recordingId")
            val response: Response<List<RecommendationDto>> = apiService.getRecordingRecommendations(recordingId)

            if (response.isSuccessful) {
                val recommendations: List<RecommendationDto> = response.body() ?: emptyList()
                Log.i(TAG_REMOTE_REPO, "Successfully fetched ${recommendations.size} recommendations for recordingId: $recordingId")
                emit(Result.success(recommendations))
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG_REMOTE_REPO, "Failed to fetch recommendations: ${response.code()} - $errorBody")
                val exception = mapHttpException("fetch recommendations", response.code(), errorBody, HttpException(response))
                emit(Result.failure(exception))
            }
        } catch (e: IOException) {
            Log.e(TAG_REMOTE_REPO, "Network/IO exception fetching recommendations: ${e.message}", e)
            emit(Result.failure(IOException(application.getString(R.string.upload_error_network_connection), e)))
        } catch (e: HttpException) {
            Log.e(TAG_REMOTE_REPO, "HTTP exception fetching recommendations: ${e.code()} - ${e.message()}", e)
            val exception = mapHttpException("fetch recommendations", e.code(), e.message(), e)
            emit(Result.failure(exception))
        } catch (e: Exception) {
            Log.e(TAG_REMOTE_REPO, "Unexpected exception fetching recommendations: ${e.message}", e)
            emit(Result.failure(IOException(application.getString(R.string.upload_error_unexpected, e.message ?: "Unknown error"), e)))
        }
    }.flowOn(Dispatchers.IO)

    override fun getCloudRecordingDetails(recordingId: String): Flow<Result<AudioMetadataDto>> = flow {
        try {
            Log.d(TAG_REMOTE_REPO, "Fetching cloud recording details for ID: $recordingId")
            val response = apiService.getRecordingDetails(recordingId)
            if (response.isSuccessful && response.body() != null) {
                Log.i(TAG_REMOTE_REPO, "Successfully fetched details for cloud recording ID: $recordingId")
                emit(Result.success(response.body()!!))
            } else {
                val errorBody = response.errorBody()?.string() ?: application.getString(R.string.upload_error_server_generic)
                Log.e(TAG_REMOTE_REPO, "Failed to fetch cloud recording details: ${response.code()} - $errorBody")
                val exception = mapHttpException("fetch cloud details", response.code(), errorBody, HttpException(response))
                emit(Result.failure(exception))
            }
        } catch (e: IOException) {
            Log.e(TAG_REMOTE_REPO, "Network/IO exception fetching cloud recording details for ID: $recordingId", e)
            emit(Result.failure(IOException(application.getString(R.string.upload_error_network_connection), e)))
        } catch (e: HttpException) {
            Log.e(TAG_REMOTE_REPO, "HTTP exception fetching cloud recording details for ID: $recordingId", e)
            val exception = mapHttpException("fetch cloud details", e.code(), e.message(), e)
            emit(Result.failure(exception))
        } catch (e: Exception) {
            Log.e(TAG_REMOTE_REPO, "Exception fetching cloud recording details for ID: $recordingId", e)
            emit(Result.failure(IOException(application.getString(R.string.upload_error_unexpected, e.message ?: "Unknown error"), e)))
        }
    }.flowOn(Dispatchers.IO)

    override fun deleteCloudRecording(metadataId: String): Flow<Result<Unit>> = flow {
        try {
            Log.d(TAG_REMOTE_REPO, "Attempting to delete cloud recording metadata with ID: $metadataId")
            val response = apiService.deleteAudioMetadata(metadataId)

            if (response.isSuccessful) {
                Log.i(TAG_REMOTE_REPO, "Successfully deleted cloud recording metadata with ID: $metadataId (Code: ${response.code()})")
                emit(Result.success(Unit))
            } else {
                val errorBody = response.errorBody()?.string() ?: application.getString(R.string.error_delete_failed_generic)
                val exception = mapHttpException("delete cloud recording", response.code(), errorBody, HttpException(response))
                Log.e(TAG_REMOTE_REPO, "Failed to delete cloud recording metadata (ID: $metadataId): ${response.code()} - $errorBody")
                emit(Result.failure(exception))
            }
        } catch (e: IOException) {
            Log.e(TAG_REMOTE_REPO, "Network/IO exception during cloud recording deletion: ${e.message}", e)
            emit(Result.failure(IOException(application.getString(R.string.error_network_connection_generic), e)))
        } catch (e: HttpException) {
            Log.e(TAG_REMOTE_REPO, "HTTP exception during cloud recording deletion: ${e.code()} - ${e.message()}", e)
            val exception = mapHttpException("delete cloud recording", e.code(), e.message(), e)
            emit(Result.failure(exception))
        } catch (e: Exception) {
            Log.e(TAG_REMOTE_REPO, "Unexpected exception during cloud recording deletion: ${e.message}", e)
            emit(Result.failure(IOException(application.getString(R.string.error_delete_failed_generic), e)))
        }
    }.flowOn(Dispatchers.IO)

    private fun mapHttpException(operation: String, code: Int, errorBody: String?, cause: HttpException): IOException {
        val message = when (code) {
            400 -> application.getString(R.string.error_upload_failed_generic) + " (Bad Request)"
            401 -> application.getString(R.string.error_unauthorized)
            403 -> application.getString(R.string.error_delete_forbidden)
            404 -> application.getString(R.string.error_delete_not_found)
            415 -> application.getString(R.string.error_upload_failed_generic) + " (Unsupported Media Type)"
            in 500..599 -> application.getString(R.string.error_server_generic)
            else -> application.getString(R.string.upload_error_server_http, code, errorBody ?: "Unknown error")
        }
        Log.w(TAG_REMOTE_REPO, "Mapped HTTP $code error during '$operation' to: $message")
        return IOException(message, cause)
    }

}