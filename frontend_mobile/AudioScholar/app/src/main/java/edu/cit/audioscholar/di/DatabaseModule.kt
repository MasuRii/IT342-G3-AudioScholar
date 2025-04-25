package edu.cit.audioscholar.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import edu.cit.audioscholar.data.local.dao.CloudCacheDao
import edu.cit.audioscholar.data.local.dao.RecordingMetadataDao
import edu.cit.audioscholar.data.local.db.AppDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {



    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "audioscholar_database"
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    @Singleton
    fun provideRecordingMetadataDao(appDatabase: AppDatabase): RecordingMetadataDao {
        return appDatabase.recordingMetadataDao()
    }

    @Provides
    @Singleton
    fun provideCloudCacheDao(appDatabase: AppDatabase): CloudCacheDao {
        return appDatabase.cloudCacheDao()
    }
} 