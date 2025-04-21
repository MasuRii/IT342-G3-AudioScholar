package edu.cit.audioscholar.domain.repository

import android.app.Application
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.google.gson.Gson
import edu.cit.audioscholar.R
import edu.cit.audioscholar.data.local.model.RecordingMetadata
import edu.cit.audioscholar.data.remote.dto.AudioMetadataDto
import edu.cit.audioscholar.data.remote.dto.AuthResponse
import edu.cit.audioscholar.data.remote.dto.FirebaseTokenRequest
import edu.cit.audioscholar.data.remote.dto.GitHubCodeRequest
import edu.cit.audioscholar.data.remote.dto.LoginRequest
import edu.cit.audioscholar.data.remote.dto.RegistrationRequest
import edu.cit.audioscholar.data.remote.service.ApiService
import edu.cit.audioscholar.util.Resource
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
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer
import retrofit2.HttpException
import retrofit2.Response
import java.io.File
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
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
            close(e)
            return@callbackFlow
        }

        if (fileBytes == null) {
            Log.e(TAG_REPO, "Failed to read file content (stream was null or empty).")
            val error = IOException(application.getString(R.string.upload_error_read_empty))
            trySend(UploadResult.Error(error.message ?: "Empty file error"))
            close(error)
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
            Log.d(TAG_REPO, "Executing API call: apiService.uploadAudio")

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
                val error = HttpException(response)
                trySend(UploadResult.Error(application.getString(R.string.upload_error_server_http, response.code(), errorBody)))
                close(error)
            }
        } catch (e: IOException) {
            Log.e(TAG_REPO, "Network/IO exception during upload: ${e.message}", e)
            trySend(UploadResult.Error(application.getString(R.string.upload_error_network_connection)))
            close(e)
        } catch (e: HttpException) {
            Log.e(TAG_REPO, "HTTP exception during upload: ${e.code()} - ${e.message()}", e)
            trySend(UploadResult.Error(application.getString(R.string.upload_error_server_http, e.code(), e.message())))
            close(e)
        } catch (e: Exception) {
            Log.e(TAG_REPO, "Unexpected exception during upload: ${e.message}", e)
            trySend(UploadResult.Error(application.getString(R.string.upload_error_unexpected, e.message ?: "Unknown error")))
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

            var durationMillis: Long
            var retriever: MediaMetadataRetriever? = null
            try {
                retriever = MediaMetadataRetriever()
                retriever.setDataSource(filePath)
                durationMillis = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            } catch (e: Exception) {
                Log.e(TAG_REPO, "Failed to get duration for file: $fileName", e)
                durationMillis = 0L
            } finally {
                try {
                    retriever?.release()
                } catch (e: Exception) {
                    Log.e(TAG_REPO, "Error releasing MediaMetadataRetriever in getRecordingMetadata", e)
                }
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


        try {
            val fileExtension = SUPPORTED_LOCAL_AUDIO_EXTENSIONS.firstOrNull { metadata.fileName.endsWith(it, ignoreCase = true) } ?: ""
            val baseName = metadata.fileName.removeSuffix(fileExtension)
            val jsonFileName = "$baseName$FILENAME_EXTENSION_METADATA"

            val recordingsDir = File(metadata.filePath).parentFile

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
                Log.w(TAG_REPO, "Could not get parent directory to find/delete JSON file for ${metadata.fileName}.")
                jsonDeletedOrNotFound = false
            }
        } catch (e: SecurityException) {
            Log.e(TAG_REPO, "SecurityException during JSON file deletion: ${metadata.fileName.replaceAfterLast('.', FILENAME_EXTENSION_METADATA)}", e)
            jsonDeletedOrNotFound = false
        } catch (e: Exception) {
            Log.e(TAG_REPO, "Error deleting JSON file: ${metadata.fileName.replaceAfterLast('.', FILENAME_EXTENSION_METADATA)}", e)
            jsonDeletedOrNotFound = false
        }

        return@withContext jsonDeletedOrNotFound
    }

    override fun getCloudRecordings(): Flow<Result<List<AudioMetadataDto>>> = flow {
        try {
            Log.d(TAG_REPO, "Fetching cloud recordings metadata from API: apiService.getAudioMetadataList")
            val response: Response<List<AudioMetadataDto>> = apiService.getAudioMetadataList()

            if (response.isSuccessful) {
                val metadataList: List<AudioMetadataDto> = response.body() ?: emptyList()
                Log.i(TAG_REPO, "Successfully fetched ${metadataList.size} cloud recordings metadata.")
                emit(Result.success(metadataList))
            } else {
                val errorBody = response.errorBody()?.string() ?: application.getString(R.string.upload_error_server_generic)
                Log.e(TAG_REPO, "Failed to fetch cloud recordings: ${response.code()} - $errorBody")
                val exception = IOException(application.getString(R.string.upload_error_server_http, response.code(), errorBody))
                emit(Result.failure(exception))
            }
        } catch (e: IOException) {
            Log.e(TAG_REPO, "Network/IO exception fetching cloud recordings: ${e.message}", e)
            emit(Result.failure(IOException(application.getString(R.string.upload_error_network_connection), e)))
        } catch (e: HttpException) {
            Log.e(TAG_REPO, "HTTP exception fetching cloud recordings: ${e.code()} - ${e.message()}", e)
            emit(Result.failure(IOException(application.getString(R.string.upload_error_server_http, e.code(), e.message()))))
        } catch (e: Exception) {
            Log.e(TAG_REPO, "Unexpected exception fetching cloud recordings: ${e.message}", e)
            emit(Result.failure(IOException(application.getString(R.string.upload_error_unexpected, e.message ?: "Unknown error"), e)))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun deleteLocalRecordings(filePaths: List<String>): Boolean = withContext(Dispatchers.IO) {
        var allSucceeded = true
        Log.i(TAG_REPO, "Attempting to delete ${filePaths.size} recordings.")

        for (filePath in filePaths) {
            val file = File(filePath)
            if (!file.exists()) {
                Log.w(TAG_REPO, "Multi-delete: File not found, skipping: $filePath")
                continue
            }
            val fileName = file.name

            var audioDeleted = false
            var jsonDeletedOrNotFound = true

            try {
                audioDeleted = file.delete()
                if (audioDeleted) {
                    Log.i(TAG_REPO, "Multi-delete: Successfully deleted audio file: $fileName")
                } else {
                    Log.w(TAG_REPO, "Multi-delete: Failed to delete audio file: $fileName")
                    allSucceeded = false
                    continue
                }
            } catch (e: SecurityException) {
                Log.e(TAG_REPO, "Multi-delete: SecurityException during audio file deletion: $fileName", e)
                allSucceeded = false
                continue
            } catch (e: Exception) {
                Log.e(TAG_REPO, "Multi-delete: Error deleting audio file: $fileName", e)
                allSucceeded = false
                continue
            }

            try {
                val fileExtension = SUPPORTED_LOCAL_AUDIO_EXTENSIONS.firstOrNull { fileName.endsWith(it, ignoreCase = true) } ?: ""
                val baseName = fileName.removeSuffix(fileExtension)
                val jsonFileName = "$baseName$FILENAME_EXTENSION_METADATA"

                val recordingsDir = file.parentFile
                if (recordingsDir != null && recordingsDir.isDirectory) {
                    val jsonFile = File(recordingsDir, jsonFileName)
                    if (jsonFile.exists()) {
                        jsonDeletedOrNotFound = jsonFile.delete()
                        if (jsonDeletedOrNotFound) {
                            Log.i(TAG_REPO, "Multi-delete: Successfully deleted metadata file: ${jsonFile.name}")
                        } else {
                            Log.w(TAG_REPO, "Multi-delete: Failed to delete metadata file: ${jsonFile.name}")
                        }
                    } else {
                        Log.i(TAG_REPO, "Multi-delete: Metadata file not found (considered success): ${jsonFile.name}")
                        jsonDeletedOrNotFound = true
                    }
                } else {
                    Log.w(TAG_REPO, "Multi-delete: Could not get parent directory for $fileName to find/delete JSON file.")
                }
            } catch (e: SecurityException) {
                Log.e(TAG_REPO, "Multi-delete: SecurityException during JSON file deletion for $fileName", e)
            } catch (e: Exception) {
                Log.e(TAG_REPO, "Multi-delete: Error deleting JSON file for $fileName", e)
            }
        }
        Log.i(TAG_REPO, "Multi-delete operation finished. Overall success: $allSucceeded")
        return@withContext allSucceeded
    }


    override suspend fun updateRecordingTitle(filePath: String, newTitle: String): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG_REPO, "Attempting to update title for $filePath to '$newTitle'")
        val audioFile = File(filePath)
        if (!audioFile.exists()) {
            Log.e(TAG_REPO, "Audio file not found: $filePath")
            return@withContext false
        }

        val recordingsDir = audioFile.parentFile
        if (recordingsDir == null || !recordingsDir.isDirectory) {
            Log.e(TAG_REPO, "Could not determine parent directory for: $filePath")
            return@withContext false
        }

        val fileName = audioFile.name
        val fileExtension = SUPPORTED_LOCAL_AUDIO_EXTENSIONS.firstOrNull { fileName.endsWith(it, ignoreCase = true) } ?: ""
        val baseName = fileName.removeSuffix(fileExtension)
        val jsonFileName = "$baseName$FILENAME_EXTENSION_METADATA"
        val jsonFile = File(recordingsDir, jsonFileName)

        try {
            val metadata: RecordingMetadata = if (jsonFile.exists() && jsonFile.isFile) {
                try {
                    val jsonContent = jsonFile.readText()
                    gson.fromJson(jsonContent, RecordingMetadata::class.java) ?: run {
                        Log.w(TAG_REPO, "JSON file exists but failed to parse: ${jsonFile.name}. Creating new metadata object.")
                        createFallbackMetadata(audioFile, baseName, newTitle)
                    }
                } catch (e: Exception) {
                    Log.e(TAG_REPO, "Error reading/parsing existing JSON file: ${jsonFile.name}", e)
                    createFallbackMetadata(audioFile, baseName, newTitle)
                }
            } else {
                Log.w(TAG_REPO, "JSON file not found: ${jsonFile.name}. Creating new metadata object.")
                createFallbackMetadata(audioFile, baseName, newTitle)
            }

            val updatedMetadata = metadata.copy(title = newTitle)

            val updatedJsonContent = gson.toJson(updatedMetadata)
            jsonFile.writeText(updatedJsonContent)

            Log.i(TAG_REPO, "Successfully updated title in metadata file: ${jsonFile.name}")
            return@withContext true

        } catch (e: IOException) {
            Log.e(TAG_REPO, "IOException during title update for ${jsonFile.name}", e)
            return@withContext false
        } catch (e: SecurityException) {
            Log.e(TAG_REPO, "SecurityException during title update for ${jsonFile.name}", e)
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG_REPO, "Unexpected error during title update for ${jsonFile.name}", e)
            return@withContext false
        }
    }

    private fun createFallbackMetadata(audioFile: File, baseName: String, title: String): RecordingMetadata {
        val dateFormat = SimpleDateFormat(FILENAME_DATE_FORMAT, Locale.US)
        val timestampString = baseName.removePrefix(FILENAME_PREFIX)
        val timestampMillis = try {
            dateFormat.parse(timestampString)?.time ?: audioFile.lastModified()
        } catch (e: ParseException) {
            audioFile.lastModified()
        }

        var durationMillis: Long
        var retriever: MediaMetadataRetriever? = null
        try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(audioFile.absolutePath)
            durationMillis = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            Log.e(TAG_REPO, "Fallback: Failed to get duration for ${audioFile.name}", e)
            durationMillis = 0L
        } finally {
            try {
                retriever?.release()
            } catch (e: Exception) {
                Log.e(TAG_REPO, "Error releasing MediaMetadataRetriever in createFallbackMetadata", e)
            }
        }

        return RecordingMetadata(
            id = timestampMillis,
            filePath = audioFile.absolutePath,
            fileName = audioFile.name,
            title = title,
            timestampMillis = timestampMillis,
            durationMillis = durationMillis
        )
    }

    override suspend fun loginUser(request: LoginRequest): Resource<AuthResponse> {
        return try {
            Log.d(TAG_REPO, "Attempting login for email: ${request.email}")
            val response = apiService.loginUser(request)

            if (response.isSuccessful) {
                val authResponse = response.body()
                if (authResponse != null) {
                    if (authResponse.token != null) {
                        Log.i(TAG_REPO, "Login successful. Token received.")
                        Resource.Success(authResponse)
                    } else {
                        Log.w(TAG_REPO, "Login successful (Code: ${response.code()}) but token was null in response.")
                        Resource.Error("Login successful but token missing.", authResponse)
                    }
                } else {
                    Log.w(TAG_REPO, "Login successful (Code: ${response.code()}) but response body was null.")
                    Resource.Error("Login successful but response body was empty.")
                }
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMessage = try {
                    gson.fromJson(errorBody, AuthResponse::class.java)?.message ?: errorBody ?: "Unknown server error"
                } catch (e: Exception) {
                    errorBody ?: "Unknown server error (Code: ${response.code()})"
                }
                Log.e(TAG_REPO, "Login failed: ${response.code()} - $errorMessage")
                Resource.Error(errorMessage)
            }
        } catch (e: IOException) {
            Log.e(TAG_REPO, "Network/IO exception during login: ${e.message}", e)
            Resource.Error(application.getString(R.string.error_network_connection))
        } catch (e: HttpException) {
            Log.e(TAG_REPO, "HTTP exception during login: ${e.code()} - ${e.message()}", e)
            Resource.Error("HTTP Error: ${e.code()} ${e.message()}")
        } catch (e: Exception) {
            Log.e(TAG_REPO, "Unexpected exception during login: ${e.message}", e)
            Resource.Error(application.getString(R.string.error_unexpected_login, e.message ?: "Unknown error"))
        }
    }

    override suspend fun registerUser(request: RegistrationRequest): Resource<AuthResponse> {
        return try {
            Log.d(TAG_REPO, "Attempting registration for email: ${request.email}")
            val response = apiService.registerUser(request)

            if (response.isSuccessful) {
                val authResponse = response.body()
                if (authResponse != null) {
                    Log.i(TAG_REPO, "Registration successful: ${authResponse.message ?: "No message"}")
                    Resource.Success(authResponse)
                } else {
                    Log.w(TAG_REPO, "Registration successful (Code: ${response.code()}) but response body was null.")
                    Resource.Success(AuthResponse(success = true, message = "Registration successful"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMessage = try {
                    gson.fromJson(errorBody, AuthResponse::class.java)?.message ?: errorBody ?: "Unknown server error"
                } catch (e: Exception) {
                    errorBody ?: "Unknown server error (Code: ${response.code()})"
                }
                Log.e(TAG_REPO, "Registration failed: ${response.code()} - $errorMessage")
                Resource.Error(errorMessage)
            }
        } catch (e: IOException) {
            Log.e(TAG_REPO, "Network/IO exception during registration: ${e.message}", e)
            Resource.Error(application.getString(R.string.error_network_connection))
        } catch (e: HttpException) {
            Log.e(TAG_REPO, "HTTP exception during registration: ${e.code()} - ${e.message()}", e)
            Resource.Error("HTTP Error: ${e.code()} ${e.message()}")
        } catch (e: Exception) {
            Log.e(TAG_REPO, "Unexpected exception during registration: ${e.message}", e)
            Resource.Error(application.getString(R.string.error_unexpected_registration, e.message ?: "Unknown error"))
        }
    }

    override suspend fun verifyGitHubCode(request: GitHubCodeRequest): Resource<AuthResponse> {
        return try {
            Log.d(TAG_REPO, "Sending GitHub code to backend for verification.")
            val response = apiService.verifyGitHubCode(request)

            if (response.isSuccessful) {
                val authResponse = response.body()
                if (authResponse != null && authResponse.token != null) {
                    Log.i(TAG_REPO, "GitHub code verified successfully by backend. API JWT received.")
                    Resource.Success(authResponse)
                } else {
                    val errorMsg = "Backend GitHub verification successful but response or API token was null."
                    Log.w(TAG_REPO, errorMsg)
                    Resource.Error(errorMsg, authResponse)
                }
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMessage = try {
                    gson.fromJson(errorBody, AuthResponse::class.java)?.message ?: errorBody ?: "Unknown backend GitHub verification error"
                } catch (e: Exception) {
                    errorBody ?: "Unknown backend GitHub verification error (Code: ${response.code()})"
                }
                Log.e(TAG_REPO, "Backend GitHub verification failed: ${response.code()} - $errorMessage")
                Resource.Error(errorMessage)
            }
        } catch (e: IOException) {
            Log.e(TAG_REPO, "Network/IO exception during GitHub code verification call: ${e.message}", e)
            Resource.Error(application.getString(R.string.error_network_connection))
        } catch (e: HttpException) {
            Log.e(TAG_REPO, "HTTP exception during GitHub code verification call: ${e.code()} - ${e.message()}", e)
            Resource.Error("HTTP Error: ${e.code()} ${e.message()}")
        } catch (e: Exception) {
            Log.e(TAG_REPO, "Unexpected exception during GitHub code verification call: ${e.message}", e)
            Resource.Error(application.getString(R.string.upload_error_unexpected, e.message ?: "Unknown error"))
        }
    }

    override suspend fun verifyFirebaseToken(request: FirebaseTokenRequest): Resource<AuthResponse> {
        return try {
            Log.d(TAG_REPO, "Sending Firebase ID token to backend for verification.")
            val response = apiService.verifyFirebaseToken(request)

            if (response.isSuccessful) {
                val authResponse = response.body()
                if (authResponse != null && authResponse.token != null) {
                    Log.i(TAG_REPO, "Firebase token verified successfully by backend. API JWT received.")
                    Resource.Success(authResponse)
                } else {
                    val errorMsg = "Backend verification successful but response or API token was null."
                    Log.w(TAG_REPO, errorMsg)
                    Resource.Error(errorMsg, authResponse)
                }
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMessage = try {
                    gson.fromJson(errorBody, AuthResponse::class.java)?.message ?: errorBody ?: "Unknown backend verification error"
                } catch (e: Exception) {
                    errorBody ?: "Unknown backend verification error (Code: ${response.code()})"
                }
                Log.e(TAG_REPO, "Backend verification failed: ${response.code()} - $errorMessage")
                Resource.Error(errorMessage)
            }
        } catch (e: IOException) {
            Log.e(TAG_REPO, "Network/IO exception during token verification call: ${e.message}", e)
            Resource.Error(application.getString(R.string.error_network_connection))
        } catch (e: HttpException) {
            Log.e(TAG_REPO, "HTTP exception during token verification call: ${e.code()} - ${e.message()}", e)
            Resource.Error("HTTP Error: ${e.code()} ${e.message()}")
        } catch (e: Exception) {
            Log.e(TAG_REPO, "Unexpected exception during token verification call: ${e.message}", e)
            Resource.Error(application.getString(R.string.upload_error_unexpected, e.message ?: "Unknown error"))
        }
    }

    override suspend fun verifyGoogleToken(request: FirebaseTokenRequest): Resource<AuthResponse> {
        return try {
            Log.d(TAG_REPO, "Sending Google ID token to backend for verification.")
            val response = apiService.verifyGoogleToken(request)

            if (response.isSuccessful) {
                val authResponse = response.body()
                if (authResponse != null && authResponse.token != null) {
                    Log.i(TAG_REPO, "Google token verified successfully by backend. API JWT received.")
                    Resource.Success(authResponse)
                } else {
                    val errorMsg = "Backend Google verification successful but response or API token was null."
                    Log.w(TAG_REPO, errorMsg)
                    Resource.Error(errorMsg, authResponse)
                }
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMessage = try {
                    gson.fromJson(errorBody, AuthResponse::class.java)?.message ?: errorBody ?: "Unknown backend Google verification error"
                } catch (e: Exception) {
                    errorBody ?: "Unknown backend Google verification error (Code: ${response.code()})"
                }
                Log.e(TAG_REPO, "Backend Google verification failed: ${response.code()} - $errorMessage")
                Resource.Error(errorMessage)
            }
        } catch (e: IOException) {
            Log.e(TAG_REPO, "Network/IO exception during Google token verification call: ${e.message}", e)
            Resource.Error(application.getString(R.string.error_network_connection))
        } catch (e: HttpException) {
            Log.e(TAG_REPO, "HTTP exception during Google token verification call: ${e.code()} - ${e.message()}", e)
            Resource.Error("HTTP Error: ${e.code()} ${e.message()}")
        } catch (e: Exception) {
            Log.e(TAG_REPO, "Unexpected exception during Google token verification call: ${e.message}", e)
            Resource.Error(application.getString(R.string.upload_error_unexpected, e.message ?: "Unknown error"))
        }
    }
}