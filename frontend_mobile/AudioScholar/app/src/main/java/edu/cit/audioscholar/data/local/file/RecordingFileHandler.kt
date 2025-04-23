package edu.cit.audioscholar.data.local.file

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

private const val RECORDINGS_DIRECTORY_NAME = "Recordings"
private const val FILENAME_DATE_FORMAT = "yyyy-MM-dd_HH-mm-ss"
private const val FILENAME_PREFIX = "Recording_"

private const val AUDIO_SOURCE = MediaRecorder.AudioSource.MIC
private const val OUTPUT_FORMAT = MediaRecorder.OutputFormat.MPEG_4
private const val AUDIO_ENCODER = MediaRecorder.AudioEncoder.AAC
private const val AUDIO_CHANNELS = 1
private const val AUDIO_ENCODING_BIT_RATE = 128000
private const val AUDIO_SAMPLING_RATE = 44100

private const val MIN_REQUIRED_SPACE_BYTES = 50 * 1024 * 1024L

private const val TAG = "RecordingFileHandler"

class InsufficientStorageException(message: String) : IOException(message)

class RecordingFileHandler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private fun getRecordingsDirectory(): Result<File> {
        return try {
            val baseDir = context.getExternalFilesDir(null)
                ?: return Result.failure(IOException("Cannot access app-specific external storage."))

            val recordingsDir = File(baseDir, RECORDINGS_DIRECTORY_NAME)
            if (!recordingsDir.exists()) {
                Log.i(TAG, "Recordings directory does not exist. Attempting to create: ${recordingsDir.absolutePath}")
                if (!recordingsDir.mkdirs()) {
                    if (!recordingsDir.exists() || !recordingsDir.isDirectory) {
                        return Result.failure(IOException("Failed to create directory: ${recordingsDir.absolutePath}"))
                    }
                    Log.w(TAG, "Recordings directory was created concurrently or mkdirs reported false positive.")
                } else {
                    Log.i(TAG, "Successfully created Recordings directory.")
                }
            } else if (!recordingsDir.isDirectory) {
                return Result.failure(IOException("Path exists but is not a directory: ${recordingsDir.absolutePath}"))
            }
            Result.success(recordingsDir)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException accessing recordings directory", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error accessing recordings directory", e)
            Result.failure(e)
        }
    }

    private fun hasSufficientStorage(directory: File, requiredBytes: Long = MIN_REQUIRED_SPACE_BYTES): Boolean {
        return try {
            val stat = StatFs(directory.path)
            val availableBytes = stat.availableBytes
            Log.d(TAG, "Available storage in ${directory.path}: $availableBytes bytes. Required: $requiredBytes bytes.")
            availableBytes > requiredBytes
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check available storage", e)
            false
        }
    }

    private fun getUriFileSize(uri: Uri): Long? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                        return cursor.getLong(sizeIndex)
                    }
                }
            }
            context.contentResolver.openFileDescriptor(uri, "r")?.use {
                it.statSize
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not determine file size for URI: $uri", e)
            null
        }
    }


    private fun getFileExtensionFromUri(uri: Uri): String {
        val mimeType = context.contentResolver.getType(uri)
        val extension = if (mimeType != null) {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        } else {
            MimeTypeMap.getFileExtensionFromUrl(uri.toString())?.substringAfterLast('.', "")
        }
        return when {
            !extension.isNullOrBlank() -> ".$extension"
            mimeType == "audio/mpeg" -> ".mp3"
            mimeType == "audio/aac" -> ".aac"
            mimeType == "audio/ogg" -> ".ogg"
            mimeType == "audio/wav" -> ".wav"
            mimeType == "audio/x-m4a" || mimeType == "audio/mp4" -> ".m4a"
            else -> ".audio"
        }
    }

    fun copyUriToLocalRecordings(sourceUri: Uri): Result<File> {
        val recordingsDirResult = getRecordingsDirectory()
        if (recordingsDirResult.isFailure) {
            return Result.failure(recordingsDirResult.exceptionOrNull() ?: IOException("Failed to get recordings directory"))
        }
        val recordingsDir = recordingsDirResult.getOrThrow()

        val estimatedSize = getUriFileSize(sourceUri)
        val requiredSpace = estimatedSize ?: MIN_REQUIRED_SPACE_BYTES
        if (!hasSufficientStorage(recordingsDir, requiredSpace)) {
            Log.e(TAG, "Insufficient storage space available in ${recordingsDir.absolutePath}. Required: $requiredSpace bytes.")
            return Result.failure(InsufficientStorageException("Not enough storage space to save the recording."))
        }

        return try {
            val timestamp = SimpleDateFormat(FILENAME_DATE_FORMAT, Locale.US).format(Date())
            val extension = getFileExtensionFromUri(sourceUri)
            val filename = "$FILENAME_PREFIX$timestamp$extension"
            val destinationFile = File(recordingsDir, filename)

            Log.d(TAG, "Attempting to copy Uri $sourceUri to local file ${destinationFile.absolutePath}")

            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                FileOutputStream(destinationFile).use { outputStream ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                    outputStream.flush()
                }
            } ?: run {
                Log.e(TAG, "Failed to open input stream for Uri: $sourceUri")
                return Result.failure(IOException("Could not open the selected file. It might be invalid or inaccessible."))
            }

            Log.i(TAG, "Successfully copied file from Uri to local storage: ${destinationFile.absolutePath}")
            Result.success(destinationFile)

        } catch (e: IOException) {
            Log.e(TAG, "IOException during file copy from Uri $sourceUri", e)
            if (e.message?.contains("no space left", ignoreCase = true) == true) {
                Result.failure(InsufficientStorageException("Not enough storage space to save the recording."))
            } else {
                Result.failure(IOException("Error copying file: ${e.localizedMessage}", e))
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException during file copy from Uri $sourceUri", e)
            Result.failure(SecurityException("Permission denied while accessing the selected file.", e))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during file copy from Uri $sourceUri", e)
            Result.failure(IOException("An unexpected error occurred during import: ${e.localizedMessage}", e))
        }
    }


    fun setupMediaRecorderOutputFile(mediaRecorder: MediaRecorder): Result<File> {
        val recordingsDirResult = getRecordingsDirectory()
        if (recordingsDirResult.isFailure) {
            return Result.failure(recordingsDirResult.exceptionOrNull() ?: IOException("Failed to get recordings directory"))
        }
        val recordingsDir = recordingsDirResult.getOrThrow()

        if (!hasSufficientStorage(recordingsDir)) {
            Log.e(TAG, "Insufficient storage space available for new recording.")
            return Result.failure(InsufficientStorageException("Not enough storage space to start recording."))
        }

        return try {
            val timestamp = SimpleDateFormat(FILENAME_DATE_FORMAT, Locale.US).format(Date())
            val filename = "$FILENAME_PREFIX$timestamp.m4a"
            val outputFile = File(recordingsDir, filename)
            Log.d(TAG, "Generated output file path for recording: ${outputFile.absolutePath}")

            mediaRecorder.setAudioSource(AUDIO_SOURCE)
            mediaRecorder.setOutputFormat(OUTPUT_FORMAT)
            mediaRecorder.setAudioEncoder(AUDIO_ENCODER)
            mediaRecorder.setAudioChannels(AUDIO_CHANNELS)
            mediaRecorder.setAudioEncodingBitRate(AUDIO_ENCODING_BIT_RATE)
            mediaRecorder.setAudioSamplingRate(AUDIO_SAMPLING_RATE)
            mediaRecorder.setOutputFile(outputFile.absolutePath)

            Log.i(TAG, "MediaRecorder configured successfully for output.")
            Result.success(outputFile)

        } catch (e: IOException) {
            Log.e(TAG, "IOException during MediaRecorder setup", e)
            if (e.message?.contains("no space left", ignoreCase = true) == true) {
                Result.failure(InsufficientStorageException("Not enough storage space to start recording."))
            } else {
                Result.failure(IOException("Error setting up recorder: ${e.localizedMessage}", e))
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException during MediaRecorder setup", e)
            Result.failure(SecurityException("Permission denied during recorder setup.", e))
        } catch (e: IllegalStateException) {
            Log.e(TAG, "IllegalStateException during MediaRecorder configuration", e)
            Result.failure(IllegalStateException("Recorder setup failed (invalid state): ${e.localizedMessage}", e))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during MediaRecorder setup", e)
            Result.failure(IOException("An unexpected error occurred during recorder setup: ${e.localizedMessage}", e))
        }
    }
}