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
import edu.cit.audioscholar.data.local.UserDataStore
import edu.cit.audioscholar.data.local.file.RecordingFileHandler
import edu.cit.audioscholar.data.remote.service.ApiService
import edu.cit.audioscholar.domain.repository.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val PRIMARY_BASE_URL = "https://mastodon-balanced-randomly.ngrok-free.app/"
    private const val FALLBACK_BASE_URL = "http://192.168.254.104:8080/"
    private const val PREFS_NAME = "AudioScholarPrefs"
    private const val TAG = "NetworkModule"

    @Singleton
    class AuthInterceptor @Inject constructor(
        private val prefs: SharedPreferences
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val token = prefs.getString("auth_token", null)
            val originalRequest = chain.request()

            val requestBuilder = originalRequest.newBuilder()
            if (!token.isNullOrBlank()) {
                Log.d(TAG, "AuthInterceptor: Adding Authorization header")
                requestBuilder.addHeader("Authorization", "Bearer $token")
            } else {
                Log.d(TAG, "AuthInterceptor: No token found, proceeding without Authorization header")
            }

            val request = requestBuilder.build()
            return chain.proceed(request)
        }
    }

    @Provides
    @Singleton
    fun provideHttpLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
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
            var primaryResponse: Response? = null
            var attemptFallback = false
            var primaryException: IOException? = null
            var primaryResponseCode = -1

            if (primaryHttpUrl != null && originalRequest.url.host == primaryHttpUrl.host) {
                try {
                    Log.d(TAG, "Attempting request to primary URL: ${originalRequest.url}")
                    primaryResponse = chain.proceed(originalRequest)
                    primaryResponseCode = primaryResponse.code

                    if (primaryResponseCode >= 500) {
                        Log.w(TAG, "Primary URL request returned server error code: $primaryResponseCode. Will attempt fallback.")
                        attemptFallback = true
                        primaryResponse.close()
                        primaryResponse = null
                    } else {
                        Log.d(TAG, "Primary URL request returned code: $primaryResponseCode. Not falling back.")
                        return@Interceptor primaryResponse
                    }

                } catch (e: IOException) {
                    Log.e(TAG, "Primary URL request failed with IOException (${e::class.java.simpleName}): ${e.message}. Attempting fallback.")
                    primaryException = e
                    attemptFallback = true
                    primaryResponse?.close()
                    primaryResponse = null
                }
            } else {
                val reason = if (primaryHttpUrl == null) "primary URL invalid" else "host mismatch (${originalRequest.url.host} != ${primaryHttpUrl.host})"
                Log.d(TAG, "Request URL does not match primary host or primary URL invalid ($reason). Proceeding without fallback interceptor logic.")
                return@Interceptor chain.proceed(originalRequest)
            }

            if (attemptFallback && fallbackHttpUrl != null) {
                val fallbackUrl = originalRequest.url.newBuilder()
                    .scheme(fallbackHttpUrl.scheme)
                    .host(fallbackHttpUrl.host)
                    .port(fallbackHttpUrl.port)
                    .build()
                val fallbackRequest = originalRequest.newBuilder()
                    .url(fallbackUrl)
                    .build()

                Log.w(TAG, "Attempting fallback request to: $fallbackUrl")
                try {
                    val fallbackResponse = chain.proceed(fallbackRequest)
                    Log.d(TAG, "Fallback URL request finished (code: ${fallbackResponse.code})")
                    return@Interceptor fallbackResponse
                } catch (fallbackException: IOException) {
                    Log.e(TAG, "Fallback URL request also failed with IOException (${fallbackException::class.java.simpleName}): ${fallbackException.message}")
                    val combinedMessage = "Primary request failed${if (primaryException != null) " (IOException: ${primaryException.message})" else " (HTTP code: $primaryResponseCode)"}, and fallback attempt also failed: ${fallbackException.message}"
                    throw IOException(combinedMessage, fallbackException)
                }
            } else if (attemptFallback) {
                Log.e(TAG, "Primary request failed${if (primaryException != null) "" else " (HTTP code: $primaryResponseCode)"}, but fallback URL is not configured or invalid. Cannot fallback.")
                throw primaryException ?: IOException("Primary request to ${originalRequest.url} failed (HTTP code: $primaryResponseCode), but no valid fallback URL configured.")
            } else {
                Log.e(TAG, "Fallback logic reached unexpectedly. Should have returned or thrown earlier. Primary Code: $primaryResponseCode")
                throw IOException("Unexpected state in fallback interceptor.")
            }
        }
    }


    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        fallbackInterceptor: Interceptor,
        authInterceptor: AuthInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(fallbackInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
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
    fun provideAuthRepository(
        apiService: ApiService,
        application: Application,
        gson: Gson,
        userDataStore: UserDataStore
    ): AuthRepository {
        return AuthRepositoryImpl(apiService, application, gson, userDataStore)
    }

    @Provides
    @Singleton
    fun provideRecordingFileHandler(@ApplicationContext context: Context): RecordingFileHandler {
        return RecordingFileHandler(context)
    }

    @Provides
    @Singleton
    fun provideLocalAudioRepository(
        @ApplicationContext context: Context,
        application: Application,
        gson: Gson,
        recordingFileHandler: RecordingFileHandler
    ): LocalAudioRepository {
        return LocalAudioRepositoryImpl(context, application, gson, recordingFileHandler)
    }

    @Provides
    @Singleton
    fun provideRemoteAudioRepository(
        apiService: ApiService,
        application: Application,
        gson: Gson
    ): RemoteAudioRepository {
        return RemoteAudioRepositoryImpl(apiService, application, gson)
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

    @Provides
    @Singleton
    fun provideUserDataStore(@ApplicationContext context: Context): UserDataStore {
        return UserDataStore(context)
    }
}