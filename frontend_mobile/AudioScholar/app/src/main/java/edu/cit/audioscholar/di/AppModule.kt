package edu.cit.audioscholar.di

import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import edu.cit.audioscholar.util.PremiumStatusManager
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun providePremiumStatusManager(
        sharedPreferences: SharedPreferences
    ): PremiumStatusManager {
        return PremiumStatusManager(sharedPreferences)
    }
} 