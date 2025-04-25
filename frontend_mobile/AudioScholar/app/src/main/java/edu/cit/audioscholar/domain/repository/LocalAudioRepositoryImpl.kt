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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import edu.cit.audioscholar.data.local.file.InsufficientStorageException
import java.io.FileOutputStream
import java.util.Date
import java.util.UUID

private const val RECORDINGS_DIRECTORY_NAME = "Recordings"
private const val FILENAME_DATE_FORMAT = "yyyy-MM-dd_HH-mm-ss-SSS"
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

    private fun getJsonFileForAudio(audioFilePath: String): File? {
        val audioFile = File(audioFilePath)
        val recordingsDir = audioFile.parentFile ?: return null
        val fileName = audioFile.name
        val fileExtension = SUPPORTED_LOCAL_AUDIO_EXTENSIONS.firstOrNull { fileName.endsWith(it, ignoreCase = true) } ?: ""
        if (fileExtension.isEmpty()) return null
        val baseName = fileName.removeSuffix(fileExtension)
        val jsonFileName = "$baseName$FILENAME_EXTENSION_METADATA"
        return File(recordingsDir, jsonFileName)
    }

    override fun getRecordingMetadata(filePath: String): Flow<Result<RecordingMetadata>> = flow {
        val audioFile = File(filePath)
        if (!audioFile.exists() || !audioFile.isFile) {
            emit(Result.failure(IOException("File not found or is not a valid file: $filePath")))
            return@flow
        }

        val fileName = audioFile.name
        val isSupported = SUPPORTED_LOCAL_AUDIO_EXTENSIONS.any { fileName.endsWith(it, ignoreCase = true) }
        if (!isSupported) {
            emit(Result.failure(IOException("Unsupported file type: $fileName")))
            return@flow
        }

        val recordingsDir = audioFile.parentFile
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
                dateFormat.parse(timestampString)?.time ?: audioFile.lastModified()
            } catch (e: ParseException) {
                Log.w(TAG_LOCAL_REPO, "Could not parse timestamp from filename: $fileName", e)
                audioFile.lastModified()
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

            val jsonFile = getJsonFileForAudio(filePath)
            var parsedMetadata: RecordingMetadata? = null

            if (jsonFile != null && jsonFile.exists() && jsonFile.isFile) {
                try {
                    val jsonContent = jsonFile.readText()
                    parsedMetadata = gson.fromJson(jsonContent, RecordingMetadata::class.java)
                    Log.d(TAG_LOCAL_REPO,"Successfully parsed metadata from ${jsonFile.name}")
                } catch (e: Exception) {
                    Log.e(TAG_LOCAL_REPO, "Failed to read or parse JSON metadata file: ${jsonFile.name}", e)
                }
            }

            val finalMetadata = RecordingMetadata(
                filePath = filePath,
                fileName = fileName,
                title = parsedMetadata?.title ?: baseNameWithTimestamp.removePrefix(FILENAME_PREFIX).replace("_", " "),
                description = parsedMetadata?.description,
                timestampMillis = timestampMillis,
                durationMillis = if (parsedMetadata != null && parsedMetadata.durationMillis > 0) parsedMetadata.durationMillis else durationMillis,
                remoteRecordingId = parsedMetadata?.remoteRecordingId,
                cachedSummaryText = parsedMetadata?.cachedSummaryText,
                cachedGlossaryItems = parsedMetadata?.cachedGlossaryItems,
                cachedRecommendations = parsedMetadata?.cachedRecommendations,
                cacheTimestampMillis = parsedMetadata?.cacheTimestampMillis
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
                    0L
                }

                val jsonFile = getJsonFileForAudio(filePath)
                var parsedMetadata: RecordingMetadata? = null

                if (jsonFile != null && jsonFile.exists() && jsonFile.isFile) {
                    try {
                        val jsonContent = jsonFile.readText()
                        parsedMetadata = gson.fromJson(jsonContent, RecordingMetadata::class.java)
                    } catch (e: Exception) {
                        Log.e(TAG_LOCAL_REPO, "Failed to read or parse JSON metadata file: ${jsonFile.name}", e)
                    }
                }

                metadataList.add(
                    RecordingMetadata(
                        filePath = filePath,
                        fileName = fileName,
                        title = parsedMetadata?.title ?: baseNameWithTimestamp.removePrefix(FILENAME_PREFIX).replace("_", " "),
                        description = parsedMetadata?.description,
                        timestampMillis = timestampMillis,
                        durationMillis = if (parsedMetadata != null && parsedMetadata.durationMillis > 0) parsedMetadata.durationMillis else durationMillis,
                        remoteRecordingId = parsedMetadata?.remoteRecordingId,
                        cachedSummaryText = parsedMetadata?.cachedSummaryText,
                        cachedGlossaryItems = parsedMetadata?.cachedGlossaryItems,
                        cachedRecommendations = parsedMetadata?.cachedRecommendations,
                        cacheTimestampMillis = parsedMetadata?.cacheTimestampMillis
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG_LOCAL_REPO, "Error processing file in list: ${file.name}", e)
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

    override suspend fun saveMetadata(metadata: RecordingMetadata): Boolean = withContext(Dispatchers.IO) {
        val jsonFile = getJsonFileForAudio(metadata.filePath)
        if (jsonFile == null) {
            Log.e(TAG_LOCAL_REPO, "Could not determine JSON file path for audio: ${metadata.filePath}")
            return@withContext false
        }

        try {
            val jsonContent = gson.toJson(metadata)
            jsonFile.writeText(jsonContent)
            Log.i(TAG_LOCAL_REPO, "Successfully saved metadata to ${jsonFile.name}")
            return@withContext true
        } catch (e: IOException) {
            Log.e(TAG_LOCAL_REPO, "IOException saving metadata to ${jsonFile.name}", e)
            return@withContext false
        } catch (e: SecurityException) {
            Log.e(TAG_LOCAL_REPO, "SecurityException saving metadata to ${jsonFile.name}", e)
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG_LOCAL_REPO, "Unexpected error saving metadata to ${jsonFile.name}", e)
            return@withContext false
        }
    }

    override suspend fun updateRecordingTitle(filePath: String, newTitle: String): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG_LOCAL_REPO, "Attempting to update title for $filePath to '$newTitle'")
        val jsonFile = getJsonFileForAudio(filePath) ?: return@withContext false
        if (!jsonFile.exists()) {
            Log.w(TAG_LOCAL_REPO, "Metadata JSON file not found for $filePath, cannot update title.")
            return@withContext false
        }

        try {
            val currentMetadata = gson.fromJson(jsonFile.readText(), RecordingMetadata::class.java)
            val updatedMetadata = currentMetadata.copy(
                title = newTitle.ifBlank { currentMetadata.title }
            )
            saveMetadata(updatedMetadata)
        } catch (e: Exception) {
            Log.e(TAG_LOCAL_REPO, "Failed to update title for $filePath", e)
            false
        }
    }

    override suspend fun updateRemoteRecordingId(localFilePath: String, remoteId: String): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG_LOCAL_REPO, "Attempting to update remoteId for $localFilePath to '$remoteId'")
        val jsonFile = getJsonFileForAudio(localFilePath) ?: return@withContext false
        if (!jsonFile.exists()) {
            Log.w(TAG_LOCAL_REPO, "Metadata JSON file not found for $localFilePath, cannot update remoteId.")
            return@withContext false
        }

        try {
            val currentMetadata = gson.fromJson(jsonFile.readText(), RecordingMetadata::class.java)
            if (currentMetadata.remoteRecordingId == remoteId) {
                Log.d(TAG_LOCAL_REPO, "Remote ID for $localFilePath already set to $remoteId. No update needed.")
                return@withContext true
            }
            val updatedMetadata = currentMetadata.copy(remoteRecordingId = remoteId)
            saveMetadata(updatedMetadata)
        } catch (e: Exception) {
            Log.e(TAG_LOCAL_REPO, "Failed to update remoteId for $localFilePath", e)
            false
        }
    }

    override suspend fun deleteLocalRecording(metadata: RecordingMetadata): Boolean = withContext(Dispatchers.IO) {
        deleteLocalRecordings(listOf(metadata.filePath))
    }

    override suspend fun deleteLocalRecordings(filePaths: List<String>): Boolean = withContext(Dispatchers.IO) {
        var allSucceeded = true
        var filesDeleted = 0
        Log.i(TAG_LOCAL_REPO, "Attempting to delete ${filePaths.size} recordings.")

        for (filePath in filePaths) {
            val audioFile = File(filePath)
            val jsonFile = getJsonFileForAudio(filePath)
            var audioDeleted = false
            var jsonDeleted = true

            try {
                if (audioFile.exists() && audioFile.isFile) {
                    if (audioFile.delete()) {
                        Log.d(TAG_LOCAL_REPO, "Deleted audio file: ${audioFile.name}")
                        audioDeleted = true
                    } else {
                        Log.w(TAG_LOCAL_REPO, "Failed to delete audio file: ${audioFile.name}")
                        allSucceeded = false
                    }
                } else {
                    Log.w(TAG_LOCAL_REPO, "Audio file not found or not a file, skipping delete: ${audioFile.name}")
                    audioDeleted = true
                }

                if (jsonFile != null && jsonFile.exists() && jsonFile.isFile) {
                    if (jsonFile.delete()) {
                        Log.d(TAG_LOCAL_REPO, "Deleted metadata file: ${jsonFile.name}")
                    } else {
                        Log.w(TAG_LOCAL_REPO, "Failed to delete metadata file: ${jsonFile.name}")
                        jsonDeleted = false
                        allSucceeded = false
                    }
                }
                if (audioDeleted && jsonDeleted) {
                    filesDeleted++
                }

            } catch (e: SecurityException) {
                Log.e(TAG_LOCAL_REPO, "SecurityException deleting file: $filePath", e)
                allSucceeded = false
            } catch (e: Exception) {
                Log.e(TAG_LOCAL_REPO, "Exception deleting file: $filePath", e)
                allSucceeded = false
            }
        }
        Log.i(TAG_LOCAL_REPO, "Finished deleting. Successful deletions: $filesDeleted/${filePaths.size}. Overall success: $allSucceeded")
        return@withContext allSucceeded
    }

    override suspend fun importAudioFile(
        sourceUri: Uri,
        originalFileName: String,
        title: String?,
        description: String?
    ): Result<RecordingMetadata> = withContext(Dispatchers.IO) {
        Log.d(TAG_LOCAL_REPO, "Starting import: $originalFileName (URI: $sourceUri), Title: $title, Desc: $description")

        val recordingsDirResult = recordingFileHandler.getRecordingsDirectory()
        val recordingsDir: File = recordingsDirResult.getOrElse { error ->
            Log.e(TAG_LOCAL_REPO, "Could not get recordings directory for import.", error)
            return@withContext Result.failure(IOException("Cannot access recordings directory.", error))
        }

        val timestamp = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat(FILENAME_DATE_FORMAT, Locale.US)
        val dateString = dateFormat.format(Date(timestamp))
        val fileExtension = ".m4a"
        val targetFileName = "$FILENAME_PREFIX${dateString}$fileExtension"
        val targetFile = File(recordingsDir, targetFileName)

        Log.d(TAG_LOCAL_REPO, "Generated target file path: ${targetFile.absolutePath}")

        try {
            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    val copiedBytes = inputStream.copyTo(outputStream)
                    Log.d(TAG_LOCAL_REPO, "Copied $copiedBytes bytes from $sourceUri to $targetFileName")
                }
            } ?: throw IOException("Could not open input stream for URI: $sourceUri")
        } catch (e: SecurityException) {
            Log.e(TAG_LOCAL_REPO, "SecurityException copying file: $sourceUri", e)
            return@withContext Result.failure(e)
        } catch (e: IOException) {
            Log.e(TAG_LOCAL_REPO, "IOException copying file: $sourceUri", e)
            val usableSpace = recordingsDir.usableSpace
            if (usableSpace < 10 * 1024 * 1024) {
                Log.e(TAG_LOCAL_REPO, "Low storage space detected: $usableSpace bytes")
                targetFile.delete()
                return@withContext Result.failure(InsufficientStorageException("Insufficient storage space to import file."))
            }
            targetFile.delete()
            return@withContext Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG_LOCAL_REPO, "Unexpected exception copying file: $sourceUri", e)
            targetFile.delete()
            return@withContext Result.failure(e)
        }

        var durationMillis: Long = 0L
        var retriever: MediaMetadataRetriever? = null
        try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(targetFile.absolutePath)
            durationMillis = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            Log.d(TAG_LOCAL_REPO, "Extracted duration: $durationMillis ms for $targetFileName")
        } catch (e: Exception) {
            Log.e(TAG_LOCAL_REPO, "Failed to get duration for imported file: $targetFileName", e)
            durationMillis = 0L
        } finally {
            try {
                retriever?.release()
            } catch (e: Exception) {
                Log.e(TAG_LOCAL_REPO, "Error releasing MediaMetadataRetriever after import", e)
            }
        }

        val finalTitle = title?.takeIf { it.isNotBlank() } ?: run {
            val lastDot = originalFileName.lastIndexOf('.')
            if (lastDot > 0) originalFileName.substring(0, lastDot) else originalFileName
        }

        val metadata = RecordingMetadata(
            filePath = targetFile.absolutePath,
            fileName = targetFileName,
            title = finalTitle,
            description = description,
            timestampMillis = timestamp,
            durationMillis = durationMillis,
            remoteRecordingId = null,
            cachedSummaryText = null,
            cachedGlossaryItems = null,
            cachedRecommendations = null,
            cacheTimestampMillis = null
        )

        val metadataSaved = saveMetadata(metadata)
        if (!metadataSaved) {
            Log.e(TAG_LOCAL_REPO, "Failed to save metadata JSON for imported file: $targetFileName")
            targetFile.delete()
            return@withContext Result.failure(IOException("Failed to save metadata for imported file."))
        }

        Log.i(TAG_LOCAL_REPO, "Successfully imported '$originalFileName' as '$targetFileName' with title '$finalTitle'")
        return@withContext Result.success(metadata)
    }

    private fun getRecordingsDirectory(): File? {
        return recordingFileHandler.getRecordingsDirectory().getOrNull()
    }

    private fun createFallbackMetadata(audioFile: File, baseName: String, title: String?): RecordingMetadata {
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

        val finalTitle = title ?: baseName.removePrefix(FILENAME_PREFIX).replace("_", " ")

        return RecordingMetadata(
            filePath = audioFile.absolutePath,
            fileName = audioFile.name,
            title = finalTitle,
            description = null,
            timestampMillis = timestampMillis,
            durationMillis = durationMillis,
            remoteRecordingId = null,
            cachedSummaryText = null,
            cachedGlossaryItems = null,
            cachedRecommendations = null,
            cacheTimestampMillis = null
        )
    }

    override fun getMetadataByRemoteId(remoteId: String): Flow<RecordingMetadata?> = flow {
        Log.d(TAG_LOCAL_REPO, "Searching for local metadata matching remoteId: $remoteId")
        val recordingsDir = getRecordingsDirectory()
        if (recordingsDir == null || !recordingsDir.exists() || !recordingsDir.isDirectory) {
            emit(null) // No directory, no cache
            return@flow
        }

        // Find all JSON metadata files
        val jsonFiles = recordingsDir.listFiles { _, name ->
            name.endsWith(FILENAME_EXTENSION_METADATA, ignoreCase = true)
        } ?: emptyArray()

        Log.d(TAG_LOCAL_REPO, "Found ${jsonFiles.size} potential metadata files to check for remoteId.")

        var foundMetadata: RecordingMetadata? = null
        for (jsonFile in jsonFiles) {
            try {
                val jsonContent = jsonFile.readText()
                val parsedMetadata = gson.fromJson(jsonContent, RecordingMetadata::class.java)
                if (parsedMetadata?.remoteRecordingId == remoteId) {
                    Log.i(TAG_LOCAL_REPO, "Found match for remoteId $remoteId in file: ${jsonFile.name}")
                    // Re-validate file path just in case audio was deleted but JSON wasn't
                    val audioFilePath = parsedMetadata.filePath
                    if (File(audioFilePath).exists()) {
                        foundMetadata = parsedMetadata
                         // Fetch duration again in case it wasn't saved correctly before or file changed
                         // This might be slightly inefficient but ensures accuracy if needed.
                         // Optional: Trust saved duration if present: parsedMetadata.durationMillis > 0
                         val updatedDuration = getDurationForFile(audioFilePath) ?: parsedMetadata.durationMillis
                         foundMetadata = foundMetadata.copy(durationMillis = updatedDuration)

                        break // Stop searching once found
                    } else {
                         Log.w(TAG_LOCAL_REPO, "Metadata found for $remoteId, but associated audio file ${parsedMetadata.filePath} is missing. Ignoring cache.")
                         // Optionally delete the orphan JSON file here: jsonFile.delete()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG_LOCAL_REPO, "Error parsing metadata file ${jsonFile.name} while searching for remoteId $remoteId", e)
                // Continue searching other files
            }
        }
        emit(foundMetadata)
    }.flowOn(Dispatchers.IO) // Ensure file operations are off the main thread

    private fun getDurationForFile(filePath: String): Long? {
        var retriever: MediaMetadataRetriever? = null
        return try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(filePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (e: Exception) {
            Log.e(TAG_LOCAL_REPO, "Failed to get duration for file: $filePath", e)
            null
        } finally {
            try {
                retriever?.release()
            } catch (e: Exception) {
                Log.e(TAG_LOCAL_REPO, "Error releasing MediaMetadataRetriever in getDurationForFile", e)
            }
        }
    }
}