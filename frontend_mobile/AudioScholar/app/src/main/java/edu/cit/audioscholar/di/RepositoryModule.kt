package edu.cit.audioscholar.di

import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import edu.cit.audioscholar.data.local.dao.CloudCacheDao
import edu.cit.audioscholar.domain.repository.CloudCacheRepository
import edu.cit.audioscholar.domain.repository.CloudCacheRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideCloudCacheRepository(
        cloudCacheDao: CloudCacheDao,
        gson: Gson
    ): CloudCacheRepository {
        return CloudCacheRepositoryImpl(cloudCacheDao, gson)
    }


} 