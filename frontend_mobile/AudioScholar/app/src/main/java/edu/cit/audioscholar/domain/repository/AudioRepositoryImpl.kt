package edu.cit.audioscholar.data.repository

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
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

    override fun uploadAudioFile(fileUri: Uri): Flow<UploadResult> = callbackFlow {
        send(UploadResult.Loading)

        var fileName = "uploaded_audio"
        val contentResolver = application.contentResolver

        contentResolver.query(fileUri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        }

        val mimeType = contentResolver.getType(fileUri) ?: "application/octet-stream"

        val fileBytes: ByteArray? = try {
            contentResolver.openInputStream(fileUri)?.use { it.readBytes() }
        } catch (e: Exception) {
            trySend(UploadResult.Error("Failed to read file: ${e.message}"))
            close(e)
            return@callbackFlow
        }

        if (fileBytes == null) {
            trySend(UploadResult.Error("Failed to read file content (stream was null)."))
            close()
            return@callbackFlow
        }

        val baseRequestBody = fileBytes.toRequestBody(mimeType.toMediaTypeOrNull())

        val progressRequestBody = ProgressReportingRequestBody(baseRequestBody) { percentage ->
            println("REPO: Emitting Progress: $percentage%")
            trySend(UploadResult.Progress(percentage))
        }

        val multipartBodyPart = MultipartBody.Part.createFormData(
            "file",
            fileName,
            progressRequestBody
        )

        try {
            val response = apiService.uploadAudio(file = multipartBodyPart)

            if (response.isSuccessful) {
                trySend(UploadResult.Success)
                close()
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown server error"
                trySend(UploadResult.Error("Upload failed: ${response.code()} - $errorBody"))
                close()
            }
        } catch (e: Exception) {
            trySend(UploadResult.Error("Upload failed: ${e.message ?: "Unknown error"}"))
            e.printStackTrace()
            close(e)
        }

        awaitClose {
            println("Upload flow closed.")
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
                e.printStackTrace()
                -1
            }
        }

        @Throws(IOException::class)
        override fun writeTo(sink: BufferedSink) {
            val totalBytes = contentLength()
            if (totalBytes == -1L) {
                println("Warning: Content length is unknown, cannot report progress accurately.")
                onProgressUpdate(0)
                delegate.writeTo(sink)
                onProgressUpdate(100)
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
                    if (percentage != lastPercentage) {
                        lastPercentage = percentage
                        println("REQ_BODY: Calling onProgressUpdate: $percentage%")
                        onProgressUpdate(percentage)
                    }
                }
            }

            val bufferedCountingSink = countingSink.buffer()
            delegate.writeTo(bufferedCountingSink)
            bufferedCountingSink.flush()

            if (lastPercentage != 100) {
                onProgressUpdate(100)
            }

        }
    }
}