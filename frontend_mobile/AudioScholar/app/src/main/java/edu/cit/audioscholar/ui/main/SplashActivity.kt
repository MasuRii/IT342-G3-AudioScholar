package edu.cit.audioscholar.ui.main

import android.annotation.SuppressLint
import android.content.Context // Import Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint // Add Hilt EntryPoint if using Hilt for SharedPreferences later
import edu.cit.audioscholar.R
import edu.cit.audioscholar.ui.theme.AudioScholarTheme
import kotlinx.coroutines.delay

@SuppressLint("CustomSplashScreen")
// @AndroidEntryPoint // Uncomment if you inject SharedPreferences via Hilt
class SplashActivity : ComponentActivity() {

    companion object {
        const val PREFS_NAME = "AudioScholarPrefs"
        const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        const val EXTRA_START_DESTINATION = "start_destination"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AudioScholarTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SplashScreenImage()
                }

                LaunchedEffect(Unit) {
                    // Check if onboarding is complete
                    val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val isOnboardingComplete = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)

                    // Determine the target destination
                    val targetDestination = if (isOnboardingComplete) {
                        Screen.Record.route // Navigate to main screen (Record)
                    } else {
                        Screen.Onboarding.route // Navigate to Onboarding
                    }

                    // Wait for a short duration
                    delay(1500) // Increased delay slightly to 1.5 seconds as per task description

                    // Create intent for MainActivity and pass the start destination
                    val intent = Intent(this@SplashActivity, MainActivity::class.java).apply {
                        putExtra(EXTRA_START_DESTINATION, targetDestination)
                        // Ensure MainActivity isn't stacked on top of existing instances unnecessarily
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                    finish() // Finish SplashActivity so it's not in the back stack
                }
            }
        }
    }
}

@Composable
fun SplashScreenImage() {
    val isDarkTheme = isSystemInDarkTheme()

    val imageRes = if (isDarkTheme) {
        R.drawable.ic_audioscholar_light
    } else {
        R.drawable.ic_audioscholar_dark
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Image(
            painter = painterResource(id = imageRes),
            contentDescription = stringResource(id = R.string.app_name),
            modifier = Modifier
                .size(150.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    }
}