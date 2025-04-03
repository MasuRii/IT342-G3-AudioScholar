package edu.cit.audioscholar.data.repository

import android.app.Application
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import edu.cit.audioscholar.data.local.model.RecordingMetadata
import edu.cit.audioscholar.data.remote.service.ApiService
import edu.cit.audioscholar.domain.repository.AudioRepository
import edu.cit.audioscholar.domain.repository.UploadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.*
import java.io.File
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val RECORDINGS_DIRECTORY_NAME = "Recordings"
private const val FILENAME_DATE_FORMAT = "yyyy-MM-dd_HH-mm-ss"
private const val FILENAME_PREFIX = "Recording_"
private const val FILENAME_EXTENSION_AUDIO = ".m4a"
private const val FILENAME_EXTENSION_METADATA = ".json"
private const val TAG_REPO = "AudioRepositoryImpl"


@Singleton
class AudioRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val application: Application
) : AudioRepository {

    override fun uploadAudioFile(
        fileUri: Uri,
        title: String?,
        description: String?
    ): Flow<UploadResult> = callbackFlow<UploadResult> {
        trySend(UploadResult.Loading)
        Log.d(TAG_REPO, "Starting upload for $fileUri. Title: '$title', Desc: '$description'")

        var fileName = "uploaded_audio"
        val contentResolver = application.contentResolver

        try {
            contentResolver.query(fileUri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex)
                        Log.d(TAG_REPO, "Resolved filename: $fileName")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG_REPO, "Could not resolve filename, using default.", e)
        }


        val mimeType = contentResolver.getType(fileUri) ?: "application/octet-stream"
        Log.d(TAG_REPO, "Resolved MIME type: $mimeType")

        val fileBytes: ByteArray? = try {
            contentResolver.openInputStream(fileUri)?.use { it.readBytes() }
        } catch (e: Exception) {
            Log.e(TAG_REPO, "Failed to read file bytes", e)
            trySend(UploadResult.Error("Failed to read file: ${e.message}"))
            close(e)
            return@callbackFlow
        }

        if (fileBytes == null) {
            Log.e(TAG_REPO, "Failed to read file content (stream was null or empty).")
            trySend(UploadResult.Error("Failed to read file content."))
            close()
            return@callbackFlow
        }
        Log.d(TAG_REPO, "Read ${fileBytes.size} bytes from file.")

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

        Log.d(TAG_REPO, "Title part created: ${titlePart != null}")
        Log.d(TAG_REPO, "Description part created: ${descriptionPart != null}")


        try {
            Log.d(TAG_REPO, "Executing API call...")
            val response = apiService.uploadAudio(
                file = filePart,
                title = titlePart,
                description = descriptionPart
            )
            Log.d(TAG_REPO, "API call finished. Response code: ${response.code()}")

            if (response.isSuccessful) {
                Log.i(TAG_REPO, "Upload successful.")
                trySend(UploadResult.Success)
                close()
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown server error"
                Log.e(TAG_REPO, "Upload failed: ${response.code()} - $errorBody")
                trySend(UploadResult.Error("Upload failed: ${response.code()} - $errorBody"))
                close()
            }
        } catch (e: Exception) {
            Log.e(TAG_REPO, "Upload exception: ${e.message}", e)
            trySend(UploadResult.Error("Upload failed: ${e.message ?: "Network or unexpected error"}"))
            close(e)
        }

        awaitClose {
            Log.d(TAG_REPO, "Upload flow channel closed.")
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

    private fun getRecordingsDirectory(): File? {
        val baseDir = application.getExternalFilesDir(null)
        if (baseDir == null) {
            Log.e(TAG_REPO, "Failed to get app-specific external files directory.")
            return null
        }
        return File(baseDir, RECORDINGS_DIRECTORY_NAME)
    }

    override fun getLocalRecordings(): Flow<List<RecordingMetadata>> = flow {
        val recordingsDir = getRecordingsDirectory()
        if (recordingsDir == null || !recordingsDir.exists() || !recordingsDir.isDirectory) {
            Log.w(TAG_REPO, "Recordings directory not found or invalid.")
            emit(emptyList())
            return@flow
        }

        val recordingFiles = recordingsDir.listFiles { _, name ->
            name.startsWith(FILENAME_PREFIX) && name.endsWith(FILENAME_EXTENSION_AUDIO)
        } ?: emptyArray()

        Log.d(TAG_REPO, "Found ${recordingFiles.size} potential recording audio files.")

        val metadataList = mutableListOf<RecordingMetadata>()
        val dateFormat = SimpleDateFormat(FILENAME_DATE_FORMAT, Locale.US)
        val retriever = MediaMetadataRetriever()

        for (file in recordingFiles) {
            try {
                val fileName = file.name
                val filePath = file.absolutePath
                val timestampString = fileName
                    .removePrefix(FILENAME_PREFIX)
                    .removeSuffix(FILENAME_EXTENSION_AUDIO)

                val timestampMillis = try {
                    dateFormat.parse(timestampString)?.time ?: file.lastModified()
                } catch (e: ParseException) {
                    Log.w(TAG_REPO, "Could not parse timestamp from filename: $fileName", e)
                    file.lastModified()
                }

                val durationMillis = try {
                    retriever.setDataSource(filePath)
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                } catch (e: Exception) {
                    Log.e(TAG_REPO, "Failed to get duration for file: $fileName", e)
                    0L
                }

                val baseName = fileName.removeSuffix(FILENAME_EXTENSION_AUDIO)
                val jsonFile = File(recordingsDir, "$baseName$FILENAME_EXTENSION_METADATA")
                var titleFromFile: String? = null
                if (jsonFile.exists() && jsonFile.isFile) {
                    try {
                        val jsonContent = jsonFile.readText()
                        if (jsonContent.contains("\"title\"")) {
                            titleFromFile = jsonContent.substringAfter("\"title\":\"").substringBefore("\"")
                        }
                        Log.d(TAG_REPO,"Read title '$titleFromFile' from ${jsonFile.name}")
                    } catch (e: Exception) {
                        Log.e(TAG_REPO, "Failed to read or parse JSON metadata file: ${jsonFile.name}", e)
                    }
                }

                metadataList.add(
                    RecordingMetadata(
                        id = timestampMillis,
                        filePath = filePath,
                        fileName = fileName,
                        title = titleFromFile ?: baseName,
                        timestampMillis = timestampMillis,
                        durationMillis = durationMillis
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG_REPO, "Error processing file: ${file.name}", e)
            }
        }
        try {
            retriever.release()
        } catch (e: IOException) {
            Log.e(TAG_REPO, "Error releasing MediaMetadataRetriever", e)
        }


        metadataList.sortByDescending { it.timestampMillis }

        Log.d(TAG_REPO, "Emitting ${metadataList.size} recording metadata items.")
        emit(metadataList)

    }.flowOn(Dispatchers.IO)

    override suspend fun deleteLocalRecording(metadata: RecordingMetadata): Boolean = withContext(Dispatchers.IO) {
        var audioDeleted = false
        var jsonDeletedOrNotFound = false

        try {
            val audioFile = File(metadata.filePath)
            if (audioFile.exists()) {
                audioDeleted = audioFile.delete()
                if (audioDeleted) {
                    Log.i(TAG_REPO, "Successfully deleted audio file: ${metadata.fileName}")
                } else {
                    Log.w(TAG_REPO, "Failed to delete audio file: ${metadata.fileName}")
                }
            } else {
                Log.w(TAG_REPO, "Audio file not found for deletion: ${metadata.filePath}")
                return@withContext false
            }
        } catch (e: SecurityException) {
            Log.e(TAG_REPO, "SecurityException during audio file deletion: ${metadata.fileName}", e)
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG_REPO, "Error deleting audio file: ${metadata.fileName}", e)
            return@withContext false
        }

        if (!audioDeleted) {
            return@withContext false
        }

        try {
            val baseName = metadata.fileName.removeSuffix(FILENAME_EXTENSION_AUDIO)
            val recordingsDir = getRecordingsDirectory()
            if (recordingsDir != null) {
                val jsonFile = File(recordingsDir, "$baseName$FILENAME_EXTENSION_METADATA")
                if (jsonFile.exists()) {
                    jsonDeletedOrNotFound = jsonFile.delete()
                    if (jsonDeletedOrNotFound) {
                        Log.i(TAG_REPO, "Successfully deleted metadata file: ${jsonFile.name}")
                    } else {
                        Log.w(TAG_REPO, "Failed to delete metadata file: ${jsonFile.name}")
                    }
                } else {
                    Log.i(TAG_REPO, "Metadata file not found (which is okay): ${jsonFile.name}")
                    jsonDeletedOrNotFound = true
                }
            } else {
                Log.w(TAG_REPO, "Could not get recordings directory to delete JSON file.")
                jsonDeletedOrNotFound = false
            }
        } catch (e: SecurityException) {
            Log.e(TAG_REPO, "SecurityException during JSON file deletion: ${metadata.fileName.replace(FILENAME_EXTENSION_AUDIO, FILENAME_EXTENSION_METADATA)}", e)
            jsonDeletedOrNotFound = false
        } catch (e: Exception) {
            Log.e(TAG_REPO, "Error deleting JSON file: ${metadata.fileName.replace(FILENAME_EXTENSION_AUDIO, FILENAME_EXTENSION_METADATA)}", e)
            jsonDeletedOrNotFound = false
        }

        return@withContext audioDeleted
    }
}