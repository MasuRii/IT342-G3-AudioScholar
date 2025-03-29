package edu.cit.audioscholar.ui.main // Or edu.cit.audioscholar if it's directly under the root

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
import dagger.hilt.android.AndroidEntryPoint // Add Hilt Entry Point
import edu.cit.audioscholar.ui.recording.RecordingScreen // Import your new screen
import edu.cit.audioscholar.ui.theme.AudioScholarTheme

@AndroidEntryPoint // Needed for Hilt ViewModel injection in Composables
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AudioScholarTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Set up the NavHost
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController,
                        startDestination = RecordingScreenRoute // Define a route string
                    ) {
                        // Define the Recording Screen destination
                        composable(route = RecordingScreenRoute) {
                            RecordingScreen(navController = navController)
                        }

                        // Add other destinations later:
                        // composable("other_screen_route") { OtherScreen(navController) }
                    }
                }
            }
        }
    }
}

// Define route constants (good practice)
const val RecordingScreenRoute = "recording_screen"
// const val OtherScreenRoute = "other_screen"