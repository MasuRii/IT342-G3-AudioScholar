package edu.cit.audioscholar.ui.settings

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

enum class ThemeSetting { Light, Dark, System }
enum class QualitySetting { Low, Medium, High }

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: SharedPreferences
) : ViewModel() {

    companion object {
        const val PREF_KEY_THEME = "pref_theme"
        const val PREF_KEY_QUALITY = "pref_quality"
    }

    private val _selectedTheme = MutableStateFlow(ThemeSetting.System)
    val selectedTheme: StateFlow<ThemeSetting> = _selectedTheme.asStateFlow()

    val selectedQuality = androidx.compose.runtime.mutableStateOf(QualitySetting.Medium)


    init {
        loadSettings()
    }

    private fun loadSettings() {
        val savedThemeName = prefs.getString(PREF_KEY_THEME, ThemeSetting.System.name)
        _selectedTheme.value = ThemeSetting.values().firstOrNull { it.name == savedThemeName } ?: ThemeSetting.System

        val savedQualityName = prefs.getString(PREF_KEY_QUALITY, QualitySetting.Medium.name)
        selectedQuality.value = QualitySetting.values().firstOrNull { it.name == savedQualityName } ?: QualitySetting.Medium
    }

    fun updateTheme(theme: ThemeSetting) {
        _selectedTheme.value = theme
        with(prefs.edit()) {
            putString(PREF_KEY_THEME, theme.name)
            apply()
        }
    }

    fun updateQuality(quality: QualitySetting) {
        selectedQuality.value = quality
        with(prefs.edit()) {
            putString(PREF_KEY_QUALITY, quality.name)
            apply()
        }
    }
}