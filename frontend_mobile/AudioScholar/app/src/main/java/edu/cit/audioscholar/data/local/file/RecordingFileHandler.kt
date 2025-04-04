package edu.cit.audioscholar.data.local.file

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
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

private const val TAG = "RecordingFileHandler"

class RecordingFileHandler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private fun getRecordingsDirectory(): Result<File> {
        return try {
            val baseDir = context.getExternalFilesDir(null)
            if (baseDir == null) {
                Log.e(TAG, "Failed to get app-specific external files directory.")
                return Result.failure(IOException("Cannot access app-specific external storage."))
            }

            val recordingsDir = File(baseDir, RECORDINGS_DIRECTORY_NAME)
            if (!recordingsDir.exists()) {
                Log.i(TAG, "Recordings directory does not exist. Attempting to create: ${recordingsDir.absolutePath}")
                if (!recordingsDir.mkdirs()) {
                    Log.e(TAG, "Failed to create Recordings directory.")
                    if (!recordingsDir.exists() || !recordingsDir.isDirectory) {
                        return Result.failure(IOException("Failed to create directory: ${recordingsDir.absolutePath}"))
                    }
                    Log.w(TAG, "Recordings directory was created concurrently or mkdirs reported false positive.")
                } else {
                    Log.i(TAG, "Successfully created Recordings directory.")
                }
            } else if (!recordingsDir.isDirectory) {
                Log.e(TAG, "Recordings path exists but is not a directory: ${recordingsDir.absolutePath}")
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

    private fun getFileExtensionFromUri(uri: Uri): String {
        val mimeType = context.contentResolver.getType(uri)
        val extension = if (mimeType != null) {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        } else {
            MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        }
        return if (!extension.isNullOrBlank()) ".$extension" else ".audio"
    }

    fun copyUriToLocalRecordings(sourceUri: Uri): Result<File> {
        val recordingsDirResult = getRecordingsDirectory()
        if (recordingsDirResult.isFailure) {
            return Result.failure(recordingsDirResult.exceptionOrNull() ?: IOException("Failed to get recordings directory"))
        }
        val recordingsDir = recordingsDirResult.getOrThrow()

        return try {
            val timestamp = SimpleDateFormat(FILENAME_DATE_FORMAT, Locale.US).format(Date())
            val extension = getFileExtensionFromUri(sourceUri)
            val filename = "$FILENAME_PREFIX$timestamp$extension"
            val destinationFile = File(recordingsDir, filename)

            Log.d(TAG, "Attempting to copy Uri $sourceUri to local file ${destinationFile.absolutePath}")

            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                FileOutputStream(destinationFile).use { outputStream ->
                    val buffer = ByteArray(4 * 1024)
                    var read: Int
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                    }
                    outputStream.flush()
                }
            } ?: run {
                Log.e(TAG, "Failed to open input stream for Uri: $sourceUri")
                return Result.failure(IOException("Could not open input stream for the selected file."))
            }

            Log.i(TAG, "Successfully copied uploaded file to local storage: ${destinationFile.absolutePath}")
            Result.success(destinationFile)

        } catch (e: IOException) {
            Log.e(TAG, "IOException during file copy from Uri $sourceUri", e)
            Result.failure(e)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException during file copy from Uri $sourceUri", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during file copy from Uri $sourceUri", e)
            Result.failure(e)
        }
    }


    fun setupMediaRecorderOutputFile(mediaRecorder: MediaRecorder): Result<File> {
        val recordingsDirResult = getRecordingsDirectory()
        if (recordingsDirResult.isFailure) {
            return Result.failure(recordingsDirResult.exceptionOrNull() ?: IOException("Failed to get recordings directory"))
        }
        val recordingsDir = recordingsDirResult.getOrThrow()

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
            Result.failure(e)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException during MediaRecorder setup", e)
            Result.failure(e)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "IllegalStateException during MediaRecorder configuration", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during MediaRecorder setup", e)
            Result.failure(e)
        }
    }
}