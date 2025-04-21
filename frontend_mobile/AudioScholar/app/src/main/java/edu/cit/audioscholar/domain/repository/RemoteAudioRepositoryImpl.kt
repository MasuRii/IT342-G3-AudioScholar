package edu.cit.audioscholar.domain.repository

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.google.gson.Gson
import edu.cit.audioscholar.R
import edu.cit.audioscholar.data.remote.dto.AudioMetadataDto
import edu.cit.audioscholar.data.remote.service.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.IOException
import okio.buffer
import retrofit2.HttpException
import retrofit2.Response
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
        fileUri: Uri,
        title: String?,
        description: String?
    ): Flow<UploadResult> = callbackFlow<UploadResult> {
        trySend(UploadResult.Loading)
        Log.d(TAG_REMOTE_REPO, "Starting upload for $fileUri. Title: '$title', Desc: '$description'")

        var fileName = "uploaded_audio"
        val contentResolver = application.contentResolver

        try {
            contentResolver.query(fileUri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex)
                        Log.d(TAG_REMOTE_REPO, "Resolved filename: $fileName")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG_REMOTE_REPO, "Could not resolve filename, using default.", e)
        }

        val mimeType = contentResolver.getType(fileUri) ?: "application/octet-stream"
        Log.d(TAG_REMOTE_REPO, "Resolved MIME type: $mimeType")

        val fileBytes: ByteArray? = try {
            contentResolver.openInputStream(fileUri)?.use { it.readBytes() }
        } catch (e: Exception) {
            Log.e(TAG_REMOTE_REPO, "Failed to read file bytes", e)
            trySend(UploadResult.Error(application.getString(R.string.upload_error_read_failed, e.message ?: "Unknown reason")))
            close(e)
            return@callbackFlow
        }

        if (fileBytes == null) {
            Log.e(TAG_REMOTE_REPO, "Failed to read file content (stream was null or empty).")
            val error = IOException(application.getString(R.string.upload_error_read_empty))
            trySend(UploadResult.Error(error.message ?: "Empty file error"))
            close(error)
            return@callbackFlow
        }
        Log.d(TAG_REMOTE_REPO, "Read ${fileBytes.size} bytes from file.")

        val baseRequestBody = fileBytes.toRequestBody(mimeType.toMediaTypeOrNull())
        val progressRequestBody = ProgressReportingRequestBody(baseRequestBody) { percentage ->
            trySend(UploadResult.Progress(percentage))
        }

        val filePart = MultipartBody.Part.createFormData(
            "file",
            fileName,
            progressRequestBody
        )

        val titlePart: RequestBody? = title?.takeIf { it.isNotBlank() }?.toRequestBody("text/plain".toMediaTypeOrNull())
        val descriptionPart: RequestBody? = description?.takeIf { it.isNotBlank() }?.toRequestBody("text/plain".toMediaTypeOrNull())

        Log.d(TAG_REMOTE_REPO, "Title part created: ${titlePart != null}")
        Log.d(TAG_REMOTE_REPO, "Description part created: ${descriptionPart != null}")

        try {
            Log.d(TAG_REMOTE_REPO, "Executing API call: apiService.uploadAudio")
            val response: Response<AudioMetadataDto> = apiService.uploadAudio(
                file = filePart,
                title = titlePart,
                description = descriptionPart
            )
            Log.d(TAG_REMOTE_REPO, "API call finished. Response code: ${response.code()}")

            if (response.isSuccessful) {
                val responseBody: AudioMetadataDto? = response.body()
                if (responseBody != null) {
                    Log.i(TAG_REMOTE_REPO, "Upload successful. Metadata ID: ${responseBody.id}")
                    trySend(UploadResult.Success)
                } else {
                    Log.w(TAG_REMOTE_REPO, "Upload successful (Code: ${response.code()}) but response body was null.")
                    trySend(UploadResult.Success)
                }
                close()
            } else {
                val errorBody = response.errorBody()?.string() ?: application.getString(R.string.upload_error_server_generic)
                Log.e(TAG_REMOTE_REPO, "Upload failed with HTTP error: ${response.code()} - $errorBody")
                val error = HttpException(response)
                trySend(UploadResult.Error(application.getString(R.string.upload_error_server_http, response.code(), errorBody)))
                close(error)
            }
        } catch (e: IOException) {
            Log.e(TAG_REMOTE_REPO, "Network/IO exception during upload: ${e.message}", e)
            trySend(UploadResult.Error(application.getString(R.string.upload_error_network_connection)))
            close(e)
        } catch (e: HttpException) {
            Log.e(TAG_REMOTE_REPO, "HTTP exception during upload: ${e.code()} - ${e.message()}", e)
            trySend(UploadResult.Error(application.getString(R.string.upload_error_server_http, e.code(), e.message())))
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
                val exception = IOException(application.getString(R.string.upload_error_server_http, response.code(), errorBody))
                emit(Result.failure(exception))
            }
        } catch (e: IOException) {
            Log.e(TAG_REMOTE_REPO, "Network/IO exception fetching cloud recordings: ${e.message}", e)
            emit(Result.failure(IOException(application.getString(R.string.upload_error_network_connection), e)))
        } catch (e: HttpException) {
            Log.e(TAG_REMOTE_REPO, "HTTP exception fetching cloud recordings: ${e.code()} - ${e.message()}", e)
            emit(Result.failure(IOException(application.getString(R.string.upload_error_server_http, e.code(), e.message()))))
        } catch (e: Exception) {
            Log.e(TAG_REMOTE_REPO, "Unexpected exception fetching cloud recordings: ${e.message}", e)
            emit(Result.failure(IOException(application.getString(R.string.upload_error_unexpected, e.message ?: "Unknown error"), e)))
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
}