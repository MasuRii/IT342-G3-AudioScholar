package edu.cit.audioscholar.data.repository

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import edu.cit.audioscholar.data.remote.service.ApiService
import edu.cit.audioscholar.domain.repository.AudioRepository
import edu.cit.audioscholar.domain.repository.UploadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.*
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val application: Application
) : AudioRepository {

    override fun uploadAudioFile(
        fileUri: Uri,
        title: String?,
        description: String?
    ): Flow<UploadResult> = callbackFlow {
        trySend(UploadResult.Loading)
        Log.d("UploadRepo", "Starting upload for $fileUri. Title: '$title', Desc: '$description'")

        var fileName = "uploaded_audio"
        val contentResolver = application.contentResolver

        try {
            contentResolver.query(fileUri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex)
                        Log.d("UploadRepo", "Resolved filename: $fileName")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("UploadRepo", "Could not resolve filename, using default.", e)
        }


        val mimeType = contentResolver.getType(fileUri) ?: "application/octet-stream"
        Log.d("UploadRepo", "Resolved MIME type: $mimeType")

        val fileBytes: ByteArray? = try {
            contentResolver.openInputStream(fileUri)?.use { it.readBytes() }
        } catch (e: Exception) {
            Log.e("UploadRepo", "Failed to read file bytes", e)
            trySend(UploadResult.Error("Failed to read file: ${e.message}"))
            close(e)
            return@callbackFlow
        }

        if (fileBytes == null) {
            Log.e("UploadRepo", "Failed to read file content (stream was null or empty).")
            trySend(UploadResult.Error("Failed to read file content."))
            close()
            return@callbackFlow
        }
        Log.d("UploadRepo", "Read ${fileBytes.size} bytes from file.")

        val baseRequestBody = fileBytes.toRequestBody(mimeType.toMediaTypeOrNull())
        val progressRequestBody = ProgressReportingRequestBody(baseRequestBody) { percentage ->
            trySend(UploadResult.Progress(percentage))
        }

        val filePart = MultipartBody.Part.createFormData(
            "file",
            fileName,
            progressRequestBody
        )

        val titlePart: RequestBody? = title?.toRequestBody("text/plain".toMediaTypeOrNull())
        val descriptionPart: RequestBody? = description?.toRequestBody("text/plain".toMediaTypeOrNull())

        Log.d("UploadRepo", "Title part created: ${titlePart != null}")
        Log.d("UploadRepo", "Description part created: ${descriptionPart != null}")


        try {
            Log.d("UploadRepo", "Executing API call...")
            val response = apiService.uploadAudio(
                file = filePart,
                title = titlePart,
                description = descriptionPart
            )
            Log.d("UploadRepo", "API call finished. Response code: ${response.code()}")

            if (response.isSuccessful) {
                Log.i("UploadRepo", "Upload successful.")
                trySend(UploadResult.Success)
                close()
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown server error"
                Log.e("UploadRepo", "Upload failed: ${response.code()} - $errorBody")
                trySend(UploadResult.Error("Upload failed: ${response.code()} - $errorBody"))
                close()
            }
        } catch (e: Exception) {
            Log.e("UploadRepo", "Upload exception: ${e.message}", e)
            trySend(UploadResult.Error("Upload failed: ${e.message ?: "Network or unexpected error"}"))
            close(e)
        }

        awaitClose {
            Log.d("UploadRepo", "Upload flow channel closed.")
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
            if (totalBytes <= 0L) {
                Log.w("ProgressRequestBody", "Content length unknown or zero ($totalBytes), cannot report progress accurately.")
                if (totalBytes == -1L) onProgressUpdate(0)
                delegate.writeTo(sink)
                if (totalBytes == -1L) onProgressUpdate(100)
                return
            }

            var bytesWritten: Long = 0
            var lastPercentage = -1

            val countingSink = object : ForwardingSink(sink) {
                @Throws(IOException::class)
                override fun write(source: Buffer, byteCount: Long) {
                    super.write(source, byteCount)
                    bytesWritten += byteCount
                    val percentage = ((bytesWritten * 100) / totalBytes).toInt()
                    if (percentage != lastPercentage && percentage in 0..100) {
                        lastPercentage = percentage
                        onProgressUpdate(percentage)
                    }
                }
            }

            val bufferedCountingSink = countingSink.buffer()
            delegate.writeTo(bufferedCountingSink)
            bufferedCountingSink.flush()

            if (lastPercentage != 100 && bytesWritten == totalBytes) {
                Log.d("ProgressRequestBody", "Ensuring 100% progress sent.")
                onProgressUpdate(100)
            }
        }
    }
}