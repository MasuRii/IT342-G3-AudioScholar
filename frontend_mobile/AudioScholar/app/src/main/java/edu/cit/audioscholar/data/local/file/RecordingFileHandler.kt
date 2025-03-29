package edu.cit.audioscholar.data.local.file // Adjust package name if needed

import android.content.Context
import android.media.MediaRecorder
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext // <-- Import this
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject // Assuming you use Hilt/Dagger for DI

// --- Constants ---
private const val RECORDINGS_DIRECTORY_NAME = "Recordings"
private const val FILENAME_DATE_FORMAT = "yyyy-MM-dd_HH-mm-ss"
private const val FILENAME_PREFIX = "Recording_"
private const val FILENAME_EXTENSION = ".m4a" // Matches MPEG_4 container with AAC encoder

// MediaRecorder Configuration Constants (Based on our discussion)
private const val AUDIO_SOURCE = MediaRecorder.AudioSource.MIC
private const val OUTPUT_FORMAT = MediaRecorder.OutputFormat.MPEG_4
private const val AUDIO_ENCODER = MediaRecorder.AudioEncoder.AAC
private const val AUDIO_CHANNELS = 1 // Mono
private const val AUDIO_ENCODING_BIT_RATE = 128000 // 128 kbps
private const val AUDIO_SAMPLING_RATE = 44100 // 44.1 kHz

private const val TAG = "RecordingFileHandler" // For logging

/**
 * Handles the creation of recording files and configuration of MediaRecorder output.
 *
 * This class is responsible for:
 * 1. Ensuring the app-specific external 'Recordings' directory exists.
 * 2. Generating a unique filename based on a timestamp.
 * 3. Configuring the provided MediaRecorder instance with appropriate output settings.
 *
 * @param context Application context to access file system directories safely.
 *                Should be injected using a DI framework like Hilt/Dagger.
 */
class RecordingFileHandler @Inject constructor(
    // Use @ApplicationContext if using Hilt to ensure the application context is injected
    @ApplicationContext private val context: Context
) {

    /**
     * Prepares the storage location and configures the MediaRecorder for output.
     *
     * This function attempts to:
     * 1. Get the app-specific external files directory.
     * 2. Create a 'Recordings' subdirectory if it doesn't exist.
     * 3. Generate a unique filename (e.g., Recording_2023-10-27_15-30-05.m4a).
     * 4. Configure the provided `mediaRecorder` with source, format, encoder, channels,
     *    bit rate, sample rate, and the output file path.
     *
     * @param mediaRecorder The `MediaRecorder` instance to configure. It should be in the
     *                      `Initial` state before calling this method.
     * @return A `Result<File>`:
     *         - `Result.success(file)`: Contains the `File` object representing the prepared
     *                                   output file if setup was successful.
     *         - `Result.failure(exception)`: Contains the exception encountered during setup
     *                                        (e.g., IOException, SecurityException,
     *                                        IllegalStateException).
     */
    fun setupMediaRecorderOutputFile(mediaRecorder: MediaRecorder): Result<File> {
        return try {
            // 1. Get base directory
            val baseDir = context.getExternalFilesDir(null)
            if (baseDir == null) {
                Log.e(TAG, "Failed to get app-specific external files directory.")
                return Result.failure(IOException("Cannot access app-specific external storage."))
            }

            // 2. Ensure "Recordings" subdirectory exists
            val recordingsDir = File(baseDir, RECORDINGS_DIRECTORY_NAME)
            if (!recordingsDir.exists()) {
                Log.i(TAG, "Recordings directory does not exist. Attempting to create: ${recordingsDir.absolutePath}")
                if (!recordingsDir.mkdirs()) {
                    Log.e(TAG, "Failed to create Recordings directory.")
                    // Check if it was created between the check and the attempt (race condition)
                    if (!recordingsDir.exists() || !recordingsDir.isDirectory) {
                        return Result.failure(IOException("Failed to create directory: ${recordingsDir.absolutePath}"))
                    }
                    // If it exists now, proceed cautiously
                    Log.w(TAG, "Recordings directory was created concurrently or mkdirs reported false positive.")
                } else {
                    Log.i(TAG, "Successfully created Recordings directory.")
                }
            } else if (!recordingsDir.isDirectory) {
                Log.e(TAG, "Recordings path exists but is not a directory: ${recordingsDir.absolutePath}")
                return Result.failure(IOException("Path exists but is not a directory: ${recordingsDir.absolutePath}"))
            }


            // 3. Generate unique filename
            val timestamp = SimpleDateFormat(FILENAME_DATE_FORMAT, Locale.US).format(Date())
            val filename = "$FILENAME_PREFIX$timestamp$FILENAME_EXTENSION"
            val outputFile = File(recordingsDir, filename)
            Log.d(TAG, "Generated output file path: ${outputFile.absolutePath}")

            // 4. Configure MediaRecorder
            // Note: Order matters for MediaRecorder configuration!
            mediaRecorder.setAudioSource(AUDIO_SOURCE)
            mediaRecorder.setOutputFormat(OUTPUT_FORMAT)
            mediaRecorder.setAudioEncoder(AUDIO_ENCODER)
            mediaRecorder.setAudioChannels(AUDIO_CHANNELS)
            mediaRecorder.setAudioEncodingBitRate(AUDIO_ENCODING_BIT_RATE)
            mediaRecorder.setAudioSamplingRate(AUDIO_SAMPLING_RATE)
            mediaRecorder.setOutputFile(outputFile.absolutePath) // Set the output file path

            Log.i(TAG, "MediaRecorder configured successfully for output.")
            Result.success(outputFile) // Return the File object on success

        } catch (e: IOException) {
            Log.e(TAG, "IOException during MediaRecorder setup", e)
            Result.failure(e)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException during directory creation or file access", e)
            Result.failure(e)
        } catch (e: IllegalStateException) {
            // This might happen if mediaRecorder is not in the correct state
            Log.e(TAG, "IllegalStateException during MediaRecorder configuration", e)
            Result.failure(e)
        } catch (e: Exception) {
            // Catch unexpected errors
            Log.e(TAG, "Unexpected error during MediaRecorder setup", e)
            Result.failure(e)
        }
    }
}