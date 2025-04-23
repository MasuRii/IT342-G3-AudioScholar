package edu.cit.audioscholar.domain.repository

import android.app.Application
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import edu.cit.audioscholar.data.local.file.RecordingFileHandler
import edu.cit.audioscholar.data.local.model.RecordingMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
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
private val SUPPORTED_LOCAL_AUDIO_EXTENSIONS = setOf(".m4a", ".mp3", ".wav", ".aac", ".ogg", ".flac")
private const val FILENAME_EXTENSION_METADATA = ".json"
private const val TAG_LOCAL_REPO = "LocalAudioRepoImpl"

@Singleton
class LocalAudioRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val application: Application,
    private val gson: Gson,
    private val recordingFileHandler: RecordingFileHandler
) : LocalAudioRepository {

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
                Log.w(TAG_LOCAL_REPO, "Could not parse timestamp from filename: $fileName", e)
                file.lastModified()
            }

            var durationMillis: Long
            var retriever: MediaMetadataRetriever? = null
            try {
                retriever = MediaMetadataRetriever()
                retriever.setDataSource(filePath)
                durationMillis = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            } catch (e: Exception) {
                Log.e(TAG_LOCAL_REPO, "Failed to get duration for file: $fileName", e)
                durationMillis = 0L
            } finally {
                try {
                    retriever?.release()
                } catch (e: Exception) {
                    Log.e(TAG_LOCAL_REPO, "Error releasing MediaMetadataRetriever in getRecordingMetadata", e)
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
                    Log.d(TAG_LOCAL_REPO,"Successfully parsed metadata from ${jsonFile.name}")
                } catch (e: Exception) {
                    Log.e(TAG_LOCAL_REPO, "Failed to read or parse JSON metadata file: ${jsonFile.name}", e)
                    try {
                        if (jsonFile.readText().contains("\"title\"")) {
                            titleFromFile = jsonFile.readText().split("\"title\":\"")[1].split("\"")[0]
                            Log.w(TAG_LOCAL_REPO,"Parsed title using fallback string split for ${jsonFile.name}")
                        }
                    } catch (splitError: Exception) {
                        Log.e(TAG_LOCAL_REPO, "Fallback string split for title also failed for ${jsonFile.name}", splitError)
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
            Log.e(TAG_LOCAL_REPO, "Error processing file metadata for: $filePath", e)
            metadataResult = Result.failure(e)
        }

        emit(metadataResult ?: Result.failure(Exception("Metadata processing failed unexpectedly for $filePath")))

    }.flowOn(Dispatchers.IO)


    override fun getLocalRecordings(): Flow<List<RecordingMetadata>> = flow {
        val recordingsDir = getRecordingsDirectory()
        if (recordingsDir == null || !recordingsDir.exists() || !recordingsDir.isDirectory) {
            Log.w(TAG_LOCAL_REPO, "Recordings directory not found or invalid.")
            emit(emptyList())
            return@flow
        }

        val recordingFiles = recordingsDir.listFiles { _, name ->
            name.startsWith(FILENAME_PREFIX) && SUPPORTED_LOCAL_AUDIO_EXTENSIONS.any { name.endsWith(it, ignoreCase = true) }
        } ?: emptyArray()

        Log.d(TAG_LOCAL_REPO, "Found ${recordingFiles.size} potential recording audio files.")

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
                    Log.w(TAG_LOCAL_REPO, "Could not parse timestamp from filename: $fileName", e)
                    file.lastModified()
                }

                val durationMillis = try {
                    retriever.setDataSource(filePath)
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                } catch (e: Exception) {
                    Log.e(TAG_LOCAL_REPO, "Failed to get duration for file: $fileName", e)
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
                        Log.d(TAG_LOCAL_REPO,"Successfully parsed metadata from ${jsonFile.name}")
                    } catch (e: Exception) {
                        Log.e(TAG_LOCAL_REPO, "Failed to read or parse JSON metadata file: ${jsonFile.name}", e)
                        try {
                            if (jsonFile.readText().contains("\"title\"")) {
                                titleFromFile = jsonFile.readText().split("\"title\":\"")[1].split("\"")[0]
                                Log.w(TAG_LOCAL_REPO,"Parsed title using fallback string split for ${jsonFile.name}")
                            }
                        } catch (splitError: Exception) {
                            Log.e(TAG_LOCAL_REPO, "Fallback string split for title also failed for ${jsonFile.name}", splitError)
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
                Log.e(TAG_LOCAL_REPO, "Error processing file: ${file.name}", e)
            }
        }
        try {
            retriever.release()
        } catch (e: Exception) {
            Log.e(TAG_LOCAL_REPO, "Error releasing MediaMetadataRetriever", e)
        }

        metadataList.sortByDescending { it.timestampMillis }

        Log.d(TAG_LOCAL_REPO, "Emitting ${metadataList.size} recording metadata items.")
        emit(metadataList)

    }.flowOn(Dispatchers.IO)


    override suspend fun deleteLocalRecording(metadata: RecordingMetadata): Boolean = withContext(Dispatchers.IO) {
        var audioDeleted = false
        var jsonDeletedOrNotFound = true

        try {
            val audioFile = File(metadata.filePath)
            if (audioFile.exists()) {
                audioDeleted = audioFile.delete()
                if (audioDeleted) {
                    Log.i(TAG_LOCAL_REPO, "Successfully deleted audio file: ${metadata.fileName}")
                } else {
                    Log.w(TAG_LOCAL_REPO, "Failed to delete audio file: ${metadata.fileName}")
                    return@withContext false
                }
            } else {
                Log.w(TAG_LOCAL_REPO, "Audio file not found for deletion (considered success for overall operation): ${metadata.filePath}")
                audioDeleted = true
            }
        } catch (e: SecurityException) {
            Log.e(TAG_LOCAL_REPO, "SecurityException during audio file deletion: ${metadata.fileName}", e)
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG_LOCAL_REPO, "Error deleting audio file: ${metadata.fileName}", e)
            return@withContext false
        }

        if (audioDeleted) {
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
                            Log.i(TAG_LOCAL_REPO, "Successfully deleted metadata file: ${jsonFile.name}")
                        } else {
                            Log.w(TAG_LOCAL_REPO, "Failed to delete metadata file: ${jsonFile.name}")
                        }
                    } else {
                        Log.i(TAG_LOCAL_REPO, "Metadata file not found (considered success): ${jsonFile.name}")
                        jsonDeletedOrNotFound = true
                    }
                } else {
                    Log.w(TAG_LOCAL_REPO, "Could not get parent directory to find/delete JSON file for ${metadata.fileName}.")
                    jsonDeletedOrNotFound = false
                }
            } catch (e: SecurityException) {
                Log.e(TAG_LOCAL_REPO, "SecurityException during JSON file deletion: ${metadata.fileName.replaceAfterLast('.', FILENAME_EXTENSION_METADATA)}", e)
                jsonDeletedOrNotFound = false
            } catch (e: Exception) {
                Log.e(TAG_LOCAL_REPO, "Error deleting JSON file: ${metadata.fileName.replaceAfterLast('.', FILENAME_EXTENSION_METADATA)}", e)
                jsonDeletedOrNotFound = false
            }
        }

        return@withContext jsonDeletedOrNotFound
    }


    override suspend fun deleteLocalRecordings(filePaths: List<String>): Boolean = withContext(Dispatchers.IO) {
        var allSucceeded = true
        Log.i(TAG_LOCAL_REPO, "Attempting to delete ${filePaths.size} recordings.")

        for (filePath in filePaths) {
            val file = File(filePath)
            if (!file.exists()) {
                Log.w(TAG_LOCAL_REPO, "Multi-delete: File not found, skipping: $filePath")
                continue
            }
            val fileName = file.name

            var audioDeleted = false
            var jsonDeletedOrNotFound = true

            try {
                audioDeleted = file.delete()
                if (!audioDeleted) {
                    Log.w(TAG_LOCAL_REPO, "Multi-delete: Failed to delete audio file: $fileName")
                    allSucceeded = false
                    continue
                }
                Log.i(TAG_LOCAL_REPO, "Multi-delete: Successfully deleted audio file: $fileName")
            } catch (e: SecurityException) {
                Log.e(TAG_LOCAL_REPO, "Multi-delete: SecurityException during audio file deletion: $fileName", e)
                allSucceeded = false
                continue
            } catch (e: Exception) {
                Log.e(TAG_LOCAL_REPO, "Multi-delete: Error deleting audio file: $fileName", e)
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
                            Log.i(TAG_LOCAL_REPO, "Multi-delete: Successfully deleted metadata file: ${jsonFile.name}")
                        } else {
                            Log.w(TAG_LOCAL_REPO, "Multi-delete: Failed to delete metadata file: ${jsonFile.name}")
                            allSucceeded = false
                        }
                    } else {
                        Log.i(TAG_LOCAL_REPO, "Multi-delete: Metadata file not found (considered success): ${jsonFile.name}")
                        jsonDeletedOrNotFound = true
                    }
                } else {
                    Log.w(TAG_LOCAL_REPO, "Multi-delete: Could not get parent directory for $fileName to find/delete JSON file.")
                    jsonDeletedOrNotFound = false
                    allSucceeded = false
                }
            } catch (e: SecurityException) {
                Log.e(TAG_LOCAL_REPO, "Multi-delete: SecurityException during JSON file deletion for $fileName", e)
                allSucceeded = false
            } catch (e: Exception) {
                Log.e(TAG_LOCAL_REPO, "Multi-delete: Error deleting JSON file for $fileName", e)
            }
        }
        Log.i(TAG_LOCAL_REPO, "Multi-delete operation finished. Overall success: $allSucceeded")
        return@withContext allSucceeded
    }


    override suspend fun updateRecordingTitle(filePath: String, newTitle: String): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG_LOCAL_REPO, "Attempting to update title for $filePath to '$newTitle'")
        val audioFile = File(filePath)
        if (!audioFile.exists()) {
            Log.e(TAG_LOCAL_REPO, "Audio file not found: $filePath")
            return@withContext false
        }

        val recordingsDir = audioFile.parentFile
        if (recordingsDir == null || !recordingsDir.isDirectory) {
            Log.e(TAG_LOCAL_REPO, "Could not determine parent directory for: $filePath")
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
                        Log.w(TAG_LOCAL_REPO, "JSON file exists but failed to parse: ${jsonFile.name}. Creating new metadata object.")
                        createFallbackMetadata(audioFile, baseName, newTitle)
                    }
                } catch (e: Exception) {
                    Log.e(TAG_LOCAL_REPO, "Error reading/parsing existing JSON file: ${jsonFile.name}", e)
                    createFallbackMetadata(audioFile, baseName, newTitle)
                }
            } else {
                Log.w(TAG_LOCAL_REPO, "JSON file not found: ${jsonFile.name}. Creating new metadata object.")
                createFallbackMetadata(audioFile, baseName, newTitle)
            }

            val updatedMetadata = metadata.copy(title = newTitle)

            val updatedJsonContent = gson.toJson(updatedMetadata)
            jsonFile.writeText(updatedJsonContent)

            Log.i(TAG_LOCAL_REPO, "Successfully updated title in metadata file: ${jsonFile.name}")
            return@withContext true

        } catch (e: IOException) {
            Log.e(TAG_LOCAL_REPO, "IOException during title update for ${jsonFile.name}", e)
            return@withContext false
        } catch (e: SecurityException) {
            Log.e(TAG_LOCAL_REPO, "SecurityException during title update for ${jsonFile.name}", e)
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG_LOCAL_REPO, "Unexpected error during title update for ${jsonFile.name}", e)
            return@withContext false
        }
    }

    override suspend fun importAudioFile(sourceUri: Uri, title: String?, description: String?): Result<RecordingMetadata> = withContext(Dispatchers.IO) {
        Log.i(TAG_LOCAL_REPO, "Starting import for URI: $sourceUri, Title: $title")

        val copyResult = recordingFileHandler.copyUriToLocalRecordings(sourceUri)
        if (copyResult.isFailure) {
            Log.e(TAG_LOCAL_REPO, "Import failed: Could not copy file from URI.", copyResult.exceptionOrNull())
            return@withContext Result.failure(copyResult.exceptionOrNull() ?: IOException("File copy failed"))
        }
        val destinationFile = copyResult.getOrThrow()
        Log.d(TAG_LOCAL_REPO, "File copied successfully to: ${destinationFile.absolutePath}")

        var durationMillis: Long
        var retriever: MediaMetadataRetriever? = null
        try {
            retriever = MediaMetadataRetriever()
            if (sourceUri.scheme == "content") {
                retriever.setDataSource(context, sourceUri)
            } else {
                retriever.setDataSource(destinationFile.absolutePath)
            }
            durationMillis = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            Log.d(TAG_LOCAL_REPO, "Extracted duration: $durationMillis ms")
        } catch (e: Exception) {
            Log.e(TAG_LOCAL_REPO, "Failed to get duration for imported file: ${destinationFile.name}", e)
            durationMillis = 0L
        } finally {
            try {
                retriever?.release()
            } catch (e: Exception) {
                Log.e(TAG_LOCAL_REPO, "Error releasing MediaMetadataRetriever during import", e)
            }
        }

        val timestampMillis = System.currentTimeMillis()
        val fileName = destinationFile.name
        val finalTitle = if (title.isNullOrBlank()) {
            fileName.substringBeforeLast('.', fileName)
        } else {
            title
        }

        val metadata = RecordingMetadata(
            id = timestampMillis,
            filePath = destinationFile.absolutePath,
            fileName = fileName,
            title = finalTitle,
            timestampMillis = timestampMillis,
            durationMillis = durationMillis
        )
        Log.d(TAG_LOCAL_REPO, "Created metadata object: $metadata")

        val recordingsDir = destinationFile.parentFile
        if (recordingsDir == null) {
            Log.e(TAG_LOCAL_REPO, "Could not get parent directory for ${destinationFile.name} to save JSON.")
            destinationFile.delete()
            return@withContext Result.failure(IOException("Failed to get recordings directory"))
        }

        val fileExtension = SUPPORTED_LOCAL_AUDIO_EXTENSIONS.firstOrNull { fileName.endsWith(it, ignoreCase = true) } ?: ""
        val baseName = fileName.removeSuffix(fileExtension)
        val jsonFileName = "$baseName$FILENAME_EXTENSION_METADATA"
        val jsonFile = File(recordingsDir, jsonFileName)

        try {
            val jsonContent = gson.toJson(metadata)
            jsonFile.writeText(jsonContent)
            Log.i(TAG_LOCAL_REPO, "Successfully saved metadata to ${jsonFile.name}")
            return@withContext Result.success(metadata)
        } catch (e: IOException) {
            Log.e(TAG_LOCAL_REPO, "IOException saving JSON metadata for ${jsonFile.name}", e)
            destinationFile.delete()
            return@withContext Result.failure(e)
        } catch (e: SecurityException) {
            Log.e(TAG_LOCAL_REPO, "SecurityException saving JSON metadata for ${jsonFile.name}", e)
            destinationFile.delete()
            return@withContext Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG_LOCAL_REPO, "Unexpected error saving JSON metadata for ${jsonFile.name}", e)
            destinationFile.delete()
            return@withContext Result.failure(e)
        }
    }

    private fun getRecordingsDirectory(): File? {
        val baseDir = application.getExternalFilesDir(null)
        if (baseDir == null) {
            Log.e(TAG_LOCAL_REPO, "Failed to get app-specific external files directory.")
            return null
        }
        val recordingsDir = File(baseDir, RECORDINGS_DIRECTORY_NAME)
        if (!recordingsDir.exists()) {
            if (recordingsDir.mkdirs()) {
                Log.i(TAG_LOCAL_REPO, "Created recordings directory at: ${recordingsDir.absolutePath}")
            } else {
                Log.e(TAG_LOCAL_REPO, "Failed to create recordings directory.")
                if (!recordingsDir.exists() || !recordingsDir.isDirectory) {
                    return null
                }
            }
        } else if (!recordingsDir.isDirectory) {
            Log.e(TAG_LOCAL_REPO, "Recordings path exists but is not a directory.")
            return null
        }
        return recordingsDir
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
            Log.e(TAG_LOCAL_REPO, "Fallback: Failed to get duration for ${audioFile.name}", e)
            durationMillis = 0L
        } finally {
            try {
                retriever?.release()
            } catch (e: Exception) {
                Log.e(TAG_LOCAL_REPO, "Error releasing MediaMetadataRetriever in createFallbackMetadata", e)
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
}