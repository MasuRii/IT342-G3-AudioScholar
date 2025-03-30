package edu.cit.audioscholar.ui.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import edu.cit.audioscholar.ui.recording.RecordingScreen
import edu.cit.audioscholar.ui.theme.AudioScholarTheme
import edu.cit.audioscholar.ui.upload.UploadScreen

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AudioScholarTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController,
                        startDestination = RecordingScreenRoute
                    ) {
                        composable(route = RecordingScreenRoute) {
                            RecordingScreen(navController = navController)
                        }

                        composable(route = UploadScreenRoute) {
                            UploadScreen(
                                onNavigateToRecording = {
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

const val RecordingScreenRoute = "recording_screen"
const val UploadScreenRoute = "upload_screen"