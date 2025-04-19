package edu.cit.audioscholar.di

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import edu.cit.audioscholar.data.remote.service.ApiService
import edu.cit.audioscholar.data.repository.AudioRepositoryImpl
import edu.cit.audioscholar.domain.repository.AudioRepository
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val PRIMARY_BASE_URL = "https://mastodon-balanced-randomly.ngrok-free.app/"
    private const val FALLBACK_BASE_URL = "http://192.168.254.100:8080/"

    private const val PREFS_NAME = "AudioScholarPrefs"
    private const val TAG = "NetworkModule"

    @Provides
    @Singleton
    fun provideHttpLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    @Provides
    @Singleton
    fun provideFallbackInterceptor(): Interceptor {
        val primaryHttpUrl = PRIMARY_BASE_URL.toHttpUrlOrNull()
        val fallbackHttpUrl = FALLBACK_BASE_URL.toHttpUrlOrNull()

        if (primaryHttpUrl == null) {
            Log.e(TAG, "FATAL: Could not parse PRIMARY_BASE_URL: $PRIMARY_BASE_URL")
        }
        if (fallbackHttpUrl == null) {
            Log.e(TAG, "ERROR: Could not parse FALLBACK_BASE_URL: $FALLBACK_BASE_URL. Fallback will not work.")
        }

        return Interceptor { chain ->
            val originalRequest: Request = chain.request()
            var response: Response? = null
            var exception: IOException? = null

            try {
                Log.d(TAG, "Attempting request to primary URL: ${originalRequest.url}")
                response = chain.proceed(originalRequest)
                if (response.isSuccessful || response.code >= 400) {
                    Log.d(TAG, "Primary URL request returned code: ${response.code}. Not falling back.")
                    return@Interceptor response
                }
                exception = IOException("Request to primary URL failed unexpectedly, code: ${response.code}")
                response.close()

            } catch (e: ConnectException) {
                Log.w(TAG, "Primary URL connection failed (ConnectException): ${e.message}")
                exception = e
            } catch (e: UnknownHostException) {
                Log.w(TAG, "Primary URL host unknown (UnknownHostException): ${e.message}")
                exception = e
            } catch (e: SocketTimeoutException) {
                Log.w(TAG, "Primary URL timed out (SocketTimeoutException): ${e.message}")
                exception = e
            } catch (e: IOException) {
                Log.w(TAG, "Primary URL IO Exception: ${e.message}. Attempting fallback.")
                exception = e
            }

            if (exception != null) {
                if (originalRequest.url.host == primaryHttpUrl?.host && fallbackHttpUrl != null) {
                    try {
                        val fallbackUrl = originalRequest.url.newBuilder()
                            .scheme(fallbackHttpUrl.scheme)
                            .host(fallbackHttpUrl.host)
                            .port(fallbackHttpUrl.port)
                            .build()

                        val fallbackRequest = originalRequest.newBuilder()
                            .url(fallbackUrl)
                            .build()

                        Log.w(TAG, "Primary URL failed. Falling back to: $fallbackUrl")
                        response?.close()

                        response = chain.proceed(fallbackRequest)
                        Log.d(TAG, "Fallback URL request returned code: ${response.code}")
                        return@Interceptor response

                    } catch (e: IOException) {
                        Log.e(TAG, "Fallback URL also failed: ${e.message}")
                        exception.addSuppressed(e)
                    }
                } else {
                    if (originalRequest.url.host != primaryHttpUrl?.host) {
                        Log.d(TAG, "Request was not to primary host (${primaryHttpUrl?.host}), not falling back.")
                    } else if (fallbackHttpUrl == null) {
                        Log.e(TAG, "Fallback URL is invalid ($FALLBACK_BASE_URL), cannot fallback.")
                    } else {
                        Log.d(TAG, "Fallback condition not met, not falling back.")
                    }
                }
            }

            throw exception ?: IOException("Unknown network error after attempting primary and potentially fallback URLs.")
        }
    }


    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        fallbackInterceptor: Interceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(fallbackInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return Gson()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl(PRIMARY_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService {
        return retrofit.create(ApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideAudioRepository(
        apiService: ApiService,
        application: Application,
        gson: Gson
    ): AudioRepository {
        return AudioRepositoryImpl(apiService, application, gson)
    }

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        return FirebaseAuth.getInstance()
    }

}