package edu.cit.audioscholar.ui.settings

import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import edu.cit.audioscholar.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

enum class ThemeSetting(@StringRes val labelResId: Int) {
    Light(R.string.settings_theme_light),
    Dark(R.string.settings_theme_dark),
    System(R.string.settings_theme_system)
}

enum class QualitySetting(@StringRes val labelResId: Int) {
    Low(R.string.settings_quality_low),
    Medium(R.string.settings_quality_medium),
    High(R.string.settings_quality_high)
}

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

    private val _selectedQuality = MutableStateFlow(QualitySetting.Medium)
    val selectedQuality: StateFlow<QualitySetting> = _selectedQuality.asStateFlow()


    init {
        loadSettings()
    }

    private fun loadSettings() {
        val savedThemeName = prefs.getString(PREF_KEY_THEME, ThemeSetting.System.name)
        _selectedTheme.value = ThemeSetting.values().firstOrNull { it.name == savedThemeName }
            ?: ThemeSetting.System

        val savedQualityName = prefs.getString(PREF_KEY_QUALITY, QualitySetting.Medium.name)
        _selectedQuality.value = QualitySetting.values().firstOrNull { it.name == savedQualityName }
            ?: QualitySetting.Medium
    }

    fun updateTheme(theme: ThemeSetting) {
        _selectedTheme.value = theme
        with(prefs.edit()) {
            putString(PREF_KEY_THEME, theme.name)
            apply()
        }
    }

    fun updateQuality(quality: QualitySetting) {
        _selectedQuality.value = quality
        with(prefs.edit()) {
            putString(PREF_KEY_QUALITY, quality.name)
            apply()
        }
    }
}