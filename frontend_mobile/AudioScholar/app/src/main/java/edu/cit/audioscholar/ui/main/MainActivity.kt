package edu.cit.audioscholar.ui.main

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.AndroidEntryPoint
import edu.cit.audioscholar.service.NAVIGATE_TO_EXTRA
import edu.cit.audioscholar.service.UPLOAD_SCREEN_VALUE
import edu.cit.audioscholar.ui.recording.RecordingScreen
import edu.cit.audioscholar.ui.theme.AudioScholarTheme
import edu.cit.audioscholar.ui.upload.UploadScreen

const val RecordingScreenRoute = "recording_screen"
const val UploadScreenRoute = "upload_screen"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private lateinit var navController: NavHostController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate called. Intent received: $intent")
        logIntentExtras("onCreate", intent)

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("MainActivity", "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }
            val token = task.result
            Log.d("MainActivity", "Manually fetched FCM token: $token")
        }

        setContent {
            AudioScholarTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    navController = rememberNavController()

                    LaunchedEffect(intent) {
                        Log.d("MainActivity", "LaunchedEffect in onCreate triggered.")
                        handleNavigationIntent(intent, navController)
                    }

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
                                    navController.popBackStack(RecordingScreenRoute, inclusive = false)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d("MainActivity", "onNewIntent called. Intent received: $intent")
        logIntentExtras("onNewIntent", intent)

        setIntent(intent)
        if (::navController.isInitialized) {
            handleNavigationIntent(intent, navController)
        } else {
            Log.e("MainActivity", "onNewIntent called but navController not initialized yet.")
        }
    }

    private fun logIntentExtras(source: String, intent: Intent?) {
        if (intent == null) {
            Log.d("MainActivity", "[$source] Intent is null.")
            return
        }
        intent.extras?.let { bundle ->
            Log.d("MainActivity", "[$source] Intent extras:")
            for (key in bundle.keySet()) {
                Log.d("MainActivity", "  Key=$key, Value=${bundle.get(key)}")
            }
        } ?: Log.d("MainActivity", "[$source] Intent has no extras.")
    }

    private fun handleNavigationIntent(intent: Intent?, navController: NavHostController) {
        Log.d("MainActivity", "[handleNavigationIntent] Checking intent...")
        logIntentExtras("handleNavigationIntent", intent)

        val navigateTo = intent?.getStringExtra(NAVIGATE_TO_EXTRA)
        Log.d("MainActivity", "[handleNavigationIntent] Value from getExtra(NAVIGATE_TO_EXTRA): $navigateTo")

        if (navigateTo == UPLOAD_SCREEN_VALUE) {
            if (navController.currentDestination?.route != UploadScreenRoute) {
                Log.d("MainActivity", "Navigating to UploadScreenRoute via navController.")
                navController.navigate(UploadScreenRoute) {
                    popUpTo(RecordingScreenRoute) {
                        inclusive = false
                    }
                    launchSingleTop = true
                }
            } else {
                Log.d("MainActivity", "Already on UploadScreenRoute, no navigation needed.")
            }
        } else {
            Log.d("MainActivity", "[handleNavigationIntent] Intent does not specify navigation to UploadScreenRoute.")
        }
    }
}