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
import javax.inject.Named
import edu.cit.audioscholar.di.PreferencesModule
import edu.cit.audioscholar.domain.model.QualitySetting

enum class SyncMode(@StringRes val labelResId: Int) {
    Automatic(R.string.settings_sync_mode_automatic),
    Manual(R.string.settings_sync_mode_manual),
    WifiOnly(R.string.settings_sync_mode_wifi_only)
}

enum class SyncFrequency(@StringRes val labelResId: Int) {
    Daily(R.string.settings_sync_freq_daily),
    Weekly(R.string.settings_sync_freq_weekly),
    Monthly(R.string.settings_sync_freq_monthly)
}

enum class ThemeSetting(@StringRes val labelResId: Int) {
    Light(R.string.settings_theme_light),
    Dark(R.string.settings_theme_dark),
    System(R.string.settings_theme_system)
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @Named(PreferencesModule.SETTINGS_PREFERENCES) private val prefs: SharedPreferences
) : ViewModel() {

    companion object {
        const val PREF_KEY_THEME = "pref_theme"
        const val PREF_KEY_SYNC_MODE = "pref_sync_mode"
        const val PREF_KEY_SYNC_FREQUENCY = "pref_sync_frequency"
    }

    private val _selectedTheme = MutableStateFlow(ThemeSetting.System)
    val selectedTheme: StateFlow<ThemeSetting> = _selectedTheme.asStateFlow()

    private val _selectedQuality = MutableStateFlow(QualitySetting.Medium)
    val selectedQuality: StateFlow<QualitySetting> = _selectedQuality.asStateFlow()

    private val _selectedSyncMode = MutableStateFlow(SyncMode.Automatic)
    val selectedSyncMode: StateFlow<SyncMode> = _selectedSyncMode.asStateFlow()

    private val _selectedSyncFrequency = MutableStateFlow(SyncFrequency.Daily)
    val selectedSyncFrequency: StateFlow<SyncFrequency> = _selectedSyncFrequency.asStateFlow()


    init {
        loadSettings()
    }

    private fun loadSettings() {
        val savedThemeName = prefs.getString(PREF_KEY_THEME, ThemeSetting.System.name)
        _selectedTheme.value = ThemeSetting.values().firstOrNull { it.name == savedThemeName }
            ?: ThemeSetting.System

        val savedQualityName = prefs.getString(QualitySetting.PREF_KEY, QualitySetting.DEFAULT)
        _selectedQuality.value = QualitySetting.values().firstOrNull { it.name == savedQualityName }
            ?: QualitySetting.Medium

        val savedSyncModeName = prefs.getString(PREF_KEY_SYNC_MODE, SyncMode.Automatic.name)
        _selectedSyncMode.value = SyncMode.values().firstOrNull { it.name == savedSyncModeName }
            ?: SyncMode.Automatic

        val savedSyncFrequencyName = prefs.getString(PREF_KEY_SYNC_FREQUENCY, SyncFrequency.Daily.name)
        _selectedSyncFrequency.value = SyncFrequency.values().firstOrNull { it.name == savedSyncFrequencyName }
            ?: SyncFrequency.Daily
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
            putString(QualitySetting.PREF_KEY, quality.name)
            apply()
        }
    }

    fun updateSyncMode(mode: SyncMode) {
        _selectedSyncMode.value = mode
        with(prefs.edit()) {
            putString(PREF_KEY_SYNC_MODE, mode.name)
            apply()
        }
    }

    fun updateSyncFrequency(frequency: SyncFrequency) {
        _selectedSyncFrequency.value = frequency
        with(prefs.edit()) {
            putString(PREF_KEY_SYNC_FREQUENCY, frequency.name)
            apply()
        }
    }
}