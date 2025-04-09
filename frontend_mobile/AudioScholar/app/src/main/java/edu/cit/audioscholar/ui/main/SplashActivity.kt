package edu.cit.audioscholar.ui.main

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
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
import edu.cit.audioscholar.R
import edu.cit.audioscholar.ui.settings.SettingsViewModel
import edu.cit.audioscholar.ui.settings.ThemeSetting
import edu.cit.audioscholar.ui.theme.AudioScholarTheme
import kotlinx.coroutines.delay

@SuppressLint("CustomSplashScreen")
class SplashActivity : ComponentActivity() {

    companion object {
        const val PREFS_NAME = "AudioScholarPrefs"
        const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        const val EXTRA_START_DESTINATION = "start_destination"
        private const val SETTINGS_PREFS_NAME = "audioscholar_settings_prefs"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val onboardingPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val settingsPrefs = getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)

        val isOnboardingComplete = onboardingPrefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
        val savedThemeName = settingsPrefs.getString(SettingsViewModel.PREF_KEY_THEME, ThemeSetting.System.name)

        val systemIsDark = resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val useDarkTheme = when (savedThemeName) {
            ThemeSetting.Light.name -> false
            ThemeSetting.Dark.name -> true
            else -> systemIsDark
        }

        setContent {
            AudioScholarTheme(darkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SplashScreenImage(useDarkTheme = useDarkTheme)
                }

                LaunchedEffect(Unit) {
                    val targetDestination = if (isOnboardingComplete) {
                        Screen.Record.route
                    } else {
                        Screen.Onboarding.route
                    }

                    delay(500)

                    val intent = Intent(this@SplashActivity, MainActivity::class.java).apply {
                        putExtra(EXTRA_START_DESTINATION, targetDestination)
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                    finish()
                }
            }
        }
    }
}

@Composable
fun SplashScreenImage(useDarkTheme: Boolean) {
    val imageRes = if (useDarkTheme) {
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
            contentScale = ContentScale.Fit
        )
    }
}