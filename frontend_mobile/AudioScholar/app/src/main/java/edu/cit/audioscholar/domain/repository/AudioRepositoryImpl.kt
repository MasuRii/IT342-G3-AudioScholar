package edu.cit.audioscholar.data.repository

import android.app.Application
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.google.gson.Gson
import edu.cit.audioscholar.R
import edu.cit.audioscholar.data.local.model.RecordingMetadata
import edu.cit.audioscholar.data.remote.dto.AudioMetadataDto
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
import retrofit2.HttpException
import retrofit2.Response
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val RECORDINGS_DIRECTORY_NAME = "Recordings"
private const val FILENAME_DATE_FORMAT = "yyyy-MM-dd_HH-mm-ss"
private const val FILENAME_PREFIX = "Recording_"
private val SUPPORTED_LOCAL_AUDIO_EXTENSIONS = setOf(".m4a", ".mp3", ".wav", ".aac", ".ogg", ".flac")
private const val FILENAME_EXTENSION_METADATA = ".json"
private const val TAG_REPO = "AudioRepositoryImpl"

@Singleton
class AudioRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val application: Application,
    private val gson: Gson
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
            trySend(UploadResult.Error(application.getString(R.string.upload_error_read_failed, e.message ?: "Unknown reason")))
            close()
            return@callbackFlow
        }

        if (fileBytes == null) {
            Log.e(TAG_REPO, "Failed to read file content (stream was null or empty).")
            trySend(UploadResult.Error(application.getString(R.string.upload_error_read_empty)))
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

        val titlePart: RequestBody? = title?.takeIf { it.isNotBlank() }?.toRequestBody("text/plain".toMediaTypeOrNull())
        val descriptionPart: RequestBody? = description?.takeIf { it.isNotBlank() }?.toRequestBody("text/plain".toMediaTypeOrNull())


        Log.d(TAG_REPO, "Title part created: ${titlePart != null}")
        Log.d(TAG_REPO, "Description part created: ${descriptionPart != null}")


        try {
            Log.d(TAG_REPO, "Executing API call...")
            val response: Response<AudioMetadataDto> = apiService.uploadAudio(
                file = filePart,
                title = titlePart,
                description = descriptionPart
            )
            Log.d(TAG_REPO, "API call finished. Response code: ${response.code()}")

            if (response.isSuccessful) {
                val responseBody: AudioMetadataDto? = response.body()
                if (responseBody != null) {
                    Log.i(TAG_REPO, "Upload successful. Metadata ID: ${responseBody.id}")
                    trySend(UploadResult.Success)
                } else {
                    Log.w(TAG_REPO, "Upload successful (Code: ${response.code()}) but response body was null.")
                    trySend(UploadResult.Success)
                }
                close()
            } else {
                val errorBody = response.errorBody()?.string() ?: application.getString(R.string.upload_error_server_generic)
                Log.e(TAG_REPO, "Upload failed with HTTP error: ${response.code()} - $errorBody")
                trySend(UploadResult.Error(application.getString(R.string.upload_error_server_http, response.code(), errorBody)))
                close()
            }
        } catch (e: IOException) {
            Log.e(TAG_REPO, "Network/IO exception during upload: ${e.message}", e)
            trySend(UploadResult.Error(application.getString(R.string.upload_error_network_connection)))
            close()
        } catch (e: Exception) {
            Log.e(TAG_REPO, "Unexpected exception during upload: ${e.message}", e)
            trySend(UploadResult.Error(application.getString(R.string.upload_error_unexpected, e.message ?: "Unknown error")))
            close()
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
                Log.d("ProgressRequestBody", "Ensuring 100% progress sent at the end.")
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
            name.startsWith(FILENAME_PREFIX) && SUPPORTED_LOCAL_AUDIO_EXTENSIONS.any { name.endsWith(it, ignoreCase = true) }
        } ?: emptyArray()

        Log.d(TAG_REPO, "Found ${recordingFiles.size} potential recording audio files.")

        val metadataList = mutableListOf<RecordingMetadata>()
        val dateFormat = SimpleDateFormat(FILENAME_DATE_FORMAT, Locale.US)
        val retriever = MediaMetadataRetriever()

        for (file in recordingFiles) {
            try {
                val fileName = file.name
                val filePath = file.absolutePath

                val fileExtension = SUPPORTED_LOCAL_AUDIO_EXTENSIONS.firstOrNull { fileName.endsWith(it, ignoreCase = true) } ?: ""
                val baseNameWithTimestamp = fileName.removeSuffix(fileExtension)

                val timestampString = baseNameWithTimestamp.removePrefix(FILENAME_PREFIX)

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

                val jsonFileName = "$baseNameWithTimestamp$FILENAME_EXTENSION_METADATA"
                val jsonFile = File(recordingsDir, jsonFileName)
                var titleFromFile: String? = null
                var parsedMetadata: RecordingMetadata? = null

                if (jsonFile.exists() && jsonFile.isFile) {
                    try {
                        val jsonContent = jsonFile.readText()
                        parsedMetadata = gson.fromJson(jsonContent, RecordingMetadata::class.java)
                        titleFromFile = parsedMetadata?.title
                        Log.d(TAG_REPO,"Successfully parsed metadata from ${jsonFile.name}")
                    } catch (e: Exception) {
                        Log.e(TAG_REPO, "Failed to read or parse JSON metadata file: ${jsonFile.name}", e)
                        try {
                            if (jsonFile.readText().contains("\"title\"")) {
                                titleFromFile = jsonFile.readText().split("\"title\":\"")[1].split("\"")[0]
                                Log.w(TAG_REPO,"Parsed title using fallback string split for ${jsonFile.name}")
                            }
                        } catch (splitError: Exception) {
                            Log.e(TAG_REPO, "Fallback string split for title also failed for ${jsonFile.name}", splitError)
                        }
                    }
                }

                metadataList.add(
                    RecordingMetadata(
                        id = timestampMillis,
                        filePath = filePath,
                        fileName = fileName,
                        title = titleFromFile ?: baseNameWithTimestamp.removePrefix(FILENAME_PREFIX).replace("_", " "),
                        timestampMillis = timestampMillis,
                        durationMillis = if (parsedMetadata != null && parsedMetadata.durationMillis > 0) parsedMetadata.durationMillis else durationMillis
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG_REPO, "Error processing file: ${file.name}", e)
            }
        }
        try {
            retriever.release()
        } catch (e: Exception) {
            Log.e(TAG_REPO, "Error releasing MediaMetadataRetriever", e)
        }

        metadataList.sortByDescending { it.timestampMillis }

        Log.d(TAG_REPO, "Emitting ${metadataList.size} recording metadata items.")
        emit(metadataList)

    }.flowOn(Dispatchers.IO)

    override fun getRecordingMetadata(filePath: String): Flow<Result<RecordingMetadata>> = flow {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            emit(Result.failure(IOException("File not found or is not a valid file: $filePath")))
            return@flow
        }

        val fileName = file.name
        val isSupported = SUPPORTED_LOCAL_AUDIO_EXTENSIONS.any { fileName.endsWith(it, ignoreCase = true) }
        if (!isSupported) {
            emit(Result.failure(IOException("Unsupported file type: $fileName")))
            return@flow
        }

        val recordingsDir = file.parentFile
        if (recordingsDir == null) {
            emit(Result.failure(IOException("Could not determine parent directory for: $filePath")))
            return@flow
        }

        val dateFormat = SimpleDateFormat(FILENAME_DATE_FORMAT, Locale.US)
        val retriever = MediaMetadataRetriever()
        var metadataResult: Result<RecordingMetadata>? = null

        try {
            val fileExtension = SUPPORTED_LOCAL_AUDIO_EXTENSIONS.firstOrNull { fileName.endsWith(it, ignoreCase = true) } ?: ""
            val baseNameWithTimestamp = fileName.removeSuffix(fileExtension)
            val timestampString = baseNameWithTimestamp.removePrefix(FILENAME_PREFIX)

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
            } finally {
                try { retriever.release() } catch (e: Exception) { Log.e(TAG_REPO, "Error releasing MediaMetadataRetriever", e) }
            }

            val jsonFileName = "$baseNameWithTimestamp$FILENAME_EXTENSION_METADATA"
            val jsonFile = File(recordingsDir, jsonFileName)
            var titleFromFile: String? = null
            var parsedMetadata: RecordingMetadata? = null

            if (jsonFile.exists() && jsonFile.isFile) {
                try {
                    val jsonContent = jsonFile.readText()
                    parsedMetadata = gson.fromJson(jsonContent, RecordingMetadata::class.java)
                    titleFromFile = parsedMetadata?.title
                    Log.d(TAG_REPO,"Successfully parsed metadata from ${jsonFile.name}")
                } catch (e: Exception) {
                    Log.e(TAG_REPO, "Failed to read or parse JSON metadata file: ${jsonFile.name}", e)
                    try {
                        if (jsonFile.readText().contains("\"title\"")) {
                            titleFromFile = jsonFile.readText().split("\"title\":\"")[1].split("\"")[0]
                            Log.w(TAG_REPO,"Parsed title using fallback string split for ${jsonFile.name}")
                        }
                    } catch (splitError: Exception) {
                        Log.e(TAG_REPO, "Fallback string split for title also failed for ${jsonFile.name}", splitError)
                    }
                }
            }

            val finalMetadata = RecordingMetadata(
                id = timestampMillis,
                filePath = filePath,
                fileName = fileName,
                title = titleFromFile ?: baseNameWithTimestamp.removePrefix(FILENAME_PREFIX).replace("_", " "),
                timestampMillis = timestampMillis,
                durationMillis = if (parsedMetadata != null && parsedMetadata.durationMillis > 0) parsedMetadata.durationMillis else durationMillis
            )
            metadataResult = Result.success(finalMetadata)

        } catch (e: Exception) {
            Log.e(TAG_REPO, "Error processing file metadata for: $filePath", e)
            metadataResult = Result.failure(e)
        }

        emit(metadataResult ?: Result.failure(Exception("Metadata processing failed unexpectedly for $filePath")))

    }.flowOn(Dispatchers.IO)


    override suspend fun deleteLocalRecording(metadata: RecordingMetadata): Boolean = withContext(Dispatchers.IO) {
        var audioDeleted = false
        var jsonDeletedOrNotFound = true

        try {
            val audioFile = File(metadata.filePath)
            if (audioFile.exists()) {
                audioDeleted = audioFile.delete()
                if (audioDeleted) {
                    Log.i(TAG_REPO, "Successfully deleted audio file: ${metadata.fileName}")
                } else {
                    Log.w(TAG_REPO, "Failed to delete audio file: ${metadata.fileName}")
                    return@withContext false
                }
            } else {
                Log.w(TAG_REPO, "Audio file not found for deletion (considered success for overall operation): ${metadata.filePath}")
                audioDeleted = true
            }
        } catch (e: SecurityException) {
            Log.e(TAG_REPO, "SecurityException during audio file deletion: ${metadata.fileName}", e)
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG_REPO, "Error deleting audio file: ${metadata.fileName}", e)
            return@withContext false
        }

        if (audioDeleted) {
            try {
                val fileExtension = SUPPORTED_LOCAL_AUDIO_EXTENSIONS.firstOrNull { metadata.fileName.endsWith(it, ignoreCase = true) } ?: ""
                val baseName = metadata.fileName.removeSuffix(fileExtension)
                val jsonFileName = "$baseName$FILENAME_EXTENSION_METADATA"

                val recordingsDir = getRecordingsDirectory()
                if (recordingsDir != null) {
                    val jsonFile = File(recordingsDir, jsonFileName)
                    if (jsonFile.exists()) {
                        jsonDeletedOrNotFound = jsonFile.delete()
                        if (jsonDeletedOrNotFound) {
                            Log.i(TAG_REPO, "Successfully deleted metadata file: ${jsonFile.name}")
                        } else {
                            Log.w(TAG_REPO, "Failed to delete metadata file: ${jsonFile.name}")
                        }
                    } else {
                        Log.i(TAG_REPO, "Metadata file not found (considered success): ${jsonFile.name}")
                        jsonDeletedOrNotFound = true
                    }
                } else {
                    Log.w(TAG_REPO, "Could not get recordings directory to find/delete JSON file.")
                    jsonDeletedOrNotFound = false
                }
            } catch (e: SecurityException) {
                Log.e(TAG_REPO, "SecurityException during JSON file deletion: ${metadata.fileName.replaceAfterLast('.', FILENAME_EXTENSION_METADATA)}", e)
                jsonDeletedOrNotFound = false
            } catch (e: Exception) {
                Log.e(TAG_REPO, "Error deleting JSON file: ${metadata.fileName.replaceAfterLast('.', FILENAME_EXTENSION_METADATA)}", e)
                jsonDeletedOrNotFound = false
            }
        }

        return@withContext audioDeleted
    }


    override fun getCloudRecordings(): Flow<Result<List<AudioMetadataDto>>> = flow {
        try {
            Log.d(TAG_REPO, "Fetching cloud recordings metadata from API...")
            val response = apiService.getAudioMetadataList()
            if (response.isSuccessful) {
                val metadataList = response.body() ?: emptyList()
                Log.i(TAG_REPO, "Successfully fetched ${metadataList.size} cloud recordings metadata.")
                emit(Result.success(metadataList))
            } else {
                val errorBody = response.errorBody()?.string() ?: application.getString(R.string.upload_error_server_generic)
                Log.e(TAG_REPO, "Failed to fetch cloud recordings: ${response.code()} - $errorBody")
                emit(Result.failure(IOException(application.getString(R.string.upload_error_server_http, response.code(), errorBody))))
            }
        } catch (e: IOException) {
            Log.e(TAG_REPO, "Network/IO exception fetching cloud recordings: ${e.message}", e)
            emit(Result.failure(IOException(application.getString(R.string.upload_error_network_connection), e)))
        } catch (e: Exception) {
            Log.e(TAG_REPO, "Unexpected exception fetching cloud recordings: ${e.message}", e)
            emit(Result.failure(IOException(application.getString(R.string.upload_error_unexpected, e.message ?: "Unknown error"), e)))
        }
    }.flowOn(Dispatchers.IO)
}