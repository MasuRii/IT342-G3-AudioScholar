package edu.cit.audioscholar.domain.model

import androidx.annotation.StringRes
import edu.cit.audioscholar.R

enum class QualitySetting(@StringRes val labelResId: Int) {
    Low(R.string.settings_quality_low),
    Medium(R.string.settings_quality_medium),
    High(R.string.settings_quality_high);

    companion object {
        const val PREF_KEY = "pref_quality"
        const val DEFAULT = "Medium"
    }
} 