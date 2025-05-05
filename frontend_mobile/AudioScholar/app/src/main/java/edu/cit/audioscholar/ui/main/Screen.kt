package edu.cit.audioscholar.ui.main

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavType
import androidx.navigation.navArgument
import edu.cit.audioscholar.R
import android.util.Log

sealed class Screen(val route: String, val labelResId: Int, val icon: ImageVector? = null) {
    object Onboarding : Screen("onboarding", R.string.nav_onboarding, Icons.Filled.Info)
    object Record : Screen("record", R.string.nav_record, Icons.Filled.Mic)
    object Library : Screen("library", R.string.nav_library, Icons.AutoMirrored.Filled.List)
    object Settings : Screen("settings", R.string.nav_settings, Icons.Filled.Settings)
    object Profile : Screen("profile", R.string.nav_profile, Icons.Filled.AccountCircle)
    object EditProfile : Screen("edit_profile", R.string.nav_edit_profile, Icons.Filled.Edit)
    object About : Screen("about", R.string.nav_about, Icons.Filled.Info)
    object Login : Screen("login?fromLogout={fromLogout}&isFirstLogin={isFirstLogin}", R.string.nav_login, Icons.AutoMirrored.Filled.Login) {
        fun createRoute(fromLogout: Boolean = false, isFirstLogin: Boolean = false) =
            "login?fromLogout=$fromLogout&isFirstLogin=$isFirstLogin"
    }
    object Registration : Screen("registration", R.string.nav_registration, Icons.Filled.PersonAdd)
    object ChangePassword : Screen("change_password", R.string.nav_change_password, Icons.Filled.Password)

    object RecordingDetails : Screen("recording_details", R.string.nav_recording_details) {
        const val ARG_LOCAL_FILE_PATH = "localFilePath"
        const val ARG_CLOUD_ID = "cloudId"
        const val ARG_CLOUD_RECORDING_ID = "cloudRecordingId"
        const val ARG_CLOUD_TITLE = "cloudTitle"
        const val ARG_CLOUD_FILENAME = "cloudFileName"
        const val ARG_CLOUD_TIMESTAMP_SECONDS = "cloudTimestampSeconds"
        const val ARG_CLOUD_AUDIO_URL = "cloudAudioUrl"

        val ROUTE_PATTERN = "recording_details" +
                "?$ARG_LOCAL_FILE_PATH={$ARG_LOCAL_FILE_PATH}" +
                "&$ARG_CLOUD_ID={$ARG_CLOUD_ID}" +
                "&$ARG_CLOUD_RECORDING_ID={$ARG_CLOUD_RECORDING_ID}" +
                "&$ARG_CLOUD_TITLE={$ARG_CLOUD_TITLE}" +
                "&$ARG_CLOUD_FILENAME={$ARG_CLOUD_FILENAME}" +
                "&$ARG_CLOUD_TIMESTAMP_SECONDS={$ARG_CLOUD_TIMESTAMP_SECONDS}" +
                "&$ARG_CLOUD_AUDIO_URL={$ARG_CLOUD_AUDIO_URL}"

        val arguments = listOf(
            navArgument(ARG_LOCAL_FILE_PATH) { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument(ARG_CLOUD_ID) { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument(ARG_CLOUD_RECORDING_ID) { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument(ARG_CLOUD_TITLE) { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument(ARG_CLOUD_FILENAME) { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument(ARG_CLOUD_TIMESTAMP_SECONDS) { type = NavType.LongType; defaultValue = 0L },
            navArgument(ARG_CLOUD_AUDIO_URL) { type = NavType.StringType; nullable = true; defaultValue = null }
        )

        fun createLocalRoute(filePath: String): String {
            return "recording_details?$ARG_LOCAL_FILE_PATH=${Uri.encode(filePath)}"
        }

        fun createCloudRoute(
            id: String,
            recordingId: String,
            title: String?,
            fileName: String?,
            timestampSeconds: Long?,
            audioUrl: String?
        ): String {
            if (id.isBlank()) {
                Log.e("Screen.RecordingDetails", "Cannot create cloud route, primary 'id' is null or blank.")
                return "recording_details/error"
            }

            val encodedTitle = Uri.encode(title ?: fileName ?: "Cloud Recording")
            val encodedFileName = Uri.encode(fileName ?: "Unknown Filename")
            val timestamp = timestampSeconds ?: 0L
            val encodedAudioUrl = Uri.encode(audioUrl ?: "")

            return "recording_details?$ARG_CLOUD_ID=$id" +
                    "&$ARG_CLOUD_RECORDING_ID=$recordingId" +
                    "&$ARG_CLOUD_TITLE=$encodedTitle" +
                    "&$ARG_CLOUD_FILENAME=$encodedFileName" +
                    "&$ARG_CLOUD_TIMESTAMP_SECONDS=$timestamp" +
                    "&$ARG_CLOUD_AUDIO_URL=$encodedAudioUrl"
        }
    }
}