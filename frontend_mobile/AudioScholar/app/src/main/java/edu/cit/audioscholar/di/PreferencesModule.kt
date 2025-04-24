package edu.cit.audioscholar.di

import android.content.Context
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule {
    
    const val SETTINGS_PREFERENCES = "settings_preferences"
    
    @Provides
    @Singleton
    @Named(SETTINGS_PREFERENCES)
    fun provideSettingsSharedPreferences(
        @ApplicationContext context: Context
    ): SharedPreferences {
        return context.getSharedPreferences(SETTINGS_PREFERENCES, Context.MODE_PRIVATE)
    }
} 