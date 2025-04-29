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
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.*
import io.ktor.http.encodedPath
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.*

@Singleton
class TimeoutInterceptor @Inject constructor() : Interceptor {

    companion object {
        private const val UPLOAD_WRITE_TIMEOUT_SECONDS = 280L
        private const val UPLOAD_READ_TIMEOUT_SECONDS =120L
        private const val TAG = "TimeoutInterceptor"
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        val isUploadRequest = request.method == "POST" &&
                request.url.encodedPath.endsWith("/api/audio/upload")

        if (isUploadRequest) {
            Log.d(TAG, "Applying longer timeouts for /api/audio/upload request.")
            val newChain = chain
                .withConnectTimeout(chain.connectTimeoutMillis(), TimeUnit.MILLISECONDS)
                .withWriteTimeout(UPLOAD_WRITE_TIMEOUT_SECONDS.toInt(), TimeUnit.SECONDS)
                .withReadTimeout(UPLOAD_READ_TIMEOUT_SECONDS.toInt(), TimeUnit.SECONDS)

            return newChain.proceed(request)
        } else {
            return chain.proceed(request)
        }
    }
}


@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val PRIMARY_BASE_URL = "https://it342-g3-audioscholar-onrender-com.onrender.com/"
    private const val FALLBACK_URL_1 = "https://mastodon-balanced-randomly.ngrok-free.app/"
    private const val FALLBACK_URL_2 = "http://192.168.254.104:8080/"

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
        val fallback1HttpUrl = FALLBACK_URL_1.toHttpUrlOrNull()
        val fallback2HttpUrl = FALLBACK_URL_2.toHttpUrlOrNull()

        if (primaryHttpUrl == null) Log.e(TAG, "FATAL: Could not parse PRIMARY_BASE_URL: $PRIMARY_BASE_URL")
        if (fallback1HttpUrl == null) Log.w(TAG, "WARNING: Could not parse FALLBACK_URL_1: $FALLBACK_URL_1.")
        if (fallback2HttpUrl == null) Log.w(TAG, "WARNING: Could not parse FALLBACK_URL_2: $FALLBACK_URL_2.")

        return Interceptor { chain ->
            val originalRequest: Request = chain.request()
            var lastException: IOException? = null
            var lastResponseCode: Int = -1

            val isUploadRequest = originalRequest.method == "POST" &&
                    originalRequest.url.encodedPath.endsWith("/api/audio/upload")

            if (isUploadRequest) {
                Log.d(TAG, "Upload request detected. Fallback interceptor will NOT retry this request.")
                try {
                    return@Interceptor chain.proceed(originalRequest)
                } catch (e: IOException) {
                    Log.e(TAG, "Upload request failed with IOException, rethrowing without fallback.", e)
                    throw e
                }
            }


            if (primaryHttpUrl != null) {
                val primaryUrl = originalRequest.url.newBuilder()
                    .scheme(primaryHttpUrl.scheme)
                    .host(primaryHttpUrl.host)
                    .port(primaryHttpUrl.port)
                    .build()
                val primaryRequest = originalRequest.newBuilder().url(primaryUrl).build()
                Log.d(TAG, "[Fallback] Attempting request to primary URL: ${primaryRequest.url}")
                try {
                    val response = chain.proceed(primaryRequest)
                    lastResponseCode = response.code
                    if (response.isSuccessful || response.code < 500) {
                        Log.d(TAG, "[Fallback] Primary URL request successful or client error (code: ${response.code}).")
                        return@Interceptor response
                    } else {
                        Log.w(TAG, "[Fallback] Primary URL request failed with server error code: ${response.code}. Attempting fallback 1.")
                        response.close()
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "[Fallback] Primary URL request failed with IOException (${e::class.java.simpleName}): ${e.message}. Attempting fallback 1.")
                    lastException = e
                }
            } else {
                Log.e(TAG, "[Fallback] Primary URL is invalid. Skipping primary attempt.")
                lastException = IOException("Primary Base URL '$PRIMARY_BASE_URL' is invalid.")
            }

            if (fallback1HttpUrl != null) {
                val fallback1Url = originalRequest.url.newBuilder()
                    .scheme(fallback1HttpUrl.scheme)
                    .host(fallback1HttpUrl.host)
                    .port(fallback1HttpUrl.port)
                    .build()
                val fallback1Request = originalRequest.newBuilder().url(fallback1Url).build()
                Log.w(TAG, "[Fallback] Attempting fallback request 1 to: $fallback1Url")
                try {
                    val response = chain.proceed(fallback1Request)
                    lastResponseCode = response.code
                    lastException = null
                    if (response.isSuccessful || response.code < 500) {
                        Log.d(TAG, "[Fallback] Fallback URL 1 request successful (code: ${response.code}).")
                        return@Interceptor response
                    } else {
                        Log.w(TAG, "[Fallback] Fallback URL 1 request failed with server error code: ${response.code}. Attempting fallback 2.")
                        response.close()
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "[Fallback] Fallback URL 1 request failed with IOException (${e::class.java.simpleName}): ${e.message}. Attempting fallback 2.")
                    lastException = e
                }
            } else {
                Log.w(TAG, "[Fallback] Fallback URL 1 is invalid. Skipping fallback 1 attempt.")
                if (lastException == null && lastResponseCode >= 500) {
                    lastException = IOException("Primary request failed (HTTP $lastResponseCode) and Fallback URL 1 '$FALLBACK_URL_1' is invalid.")
                } else if (lastException == null) {
                    lastException = IOException("Primary and Fallback URL 1 are invalid.")
                }
            }

            if (fallback2HttpUrl != null) {
                val fallback2Url = originalRequest.url.newBuilder()
                    .scheme(fallback2HttpUrl.scheme)
                    .host(fallback2HttpUrl.host)
                    .port(fallback2HttpUrl.port)
                    .build()
                val fallback2Request = originalRequest.newBuilder().url(fallback2Url).build()
                Log.w(TAG, "[Fallback] Attempting fallback request 2 to: $fallback2Url")
                try {
                    val response = chain.proceed(fallback2Request)
                    lastResponseCode = response.code
                    lastException = null
                    if (response.isSuccessful || response.code < 500) {
                        Log.d(TAG, "[Fallback] Fallback URL 2 request successful (code: ${response.code}).")
                        return@Interceptor response
                    } else {
                        Log.w(TAG, "[Fallback] Fallback URL 2 request also failed with server error code: ${response.code}. All attempts failed.")
                        response.close()
                        lastException = IOException("All attempts failed. Last attempt (Fallback 2) resulted in HTTP code: $lastResponseCode")
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "[Fallback] Fallback URL 2 request also failed with IOException (${e::class.java.simpleName}): ${e.message}. All attempts failed.")
                    lastException = e
                }
            } else {
                Log.w(TAG, "[Fallback] Fallback URL 2 is invalid. Skipping fallback 2 attempt.")
                if (lastException == null && lastResponseCode >= 500) {
                    lastException = IOException("Previous attempts failed (last code: $lastResponseCode) and Fallback URL 2 '$FALLBACK_URL_2' is invalid.")
                } else if (lastException == null) {
                    lastException = IOException("All Base URLs (Primary, Fallback 1, Fallback 2) are invalid.")
                }
            }

            Log.e(TAG, "[Fallback] All URL attempts (Primary, Fallback 1, Fallback 2) failed for non-upload request.")
            throw lastException ?: IOException("All attempts failed. Last known HTTP code: $lastResponseCode")
        }
    }

    @Provides
    @Singleton
    fun provideTimeoutInterceptor(): TimeoutInterceptor {
        return TimeoutInterceptor()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        fallbackInterceptor: Interceptor,
        authInterceptor: AuthInterceptor,
        timeoutInterceptor: TimeoutInterceptor
    ): OkHttpClient {
        Log.d(TAG, "Providing OkHttpClient instance")
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(fallbackInterceptor)
            .addInterceptor(timeoutInterceptor)
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
        Log.d(TAG, "Providing Retrofit instance with base URL: $PRIMARY_BASE_URL")
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
    fun provideRecordingFileHandler(
        @ApplicationContext context: Context,
        @Named(PreferencesModule.SETTINGS_PREFERENCES) prefs: SharedPreferences
    ): RecordingFileHandler {
        return RecordingFileHandler(context, prefs)
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

    @Provides
    @Singleton
    fun provideKtorHttpClient(prefs: SharedPreferences): HttpClient {
        Log.d(TAG, "Creating Ktor HttpClient instance.")
        return HttpClient(OkHttp) {

            engine {
            }

            defaultRequest {
                 url(PRIMARY_BASE_URL)
                 Log.d("$TAG-Ktor", "Applying defaultRequest with base URL: $PRIMARY_BASE_URL")
            }

            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                })
                Log.d("$TAG-Ktor", "Installed ContentNegotiation with Kotlinx JSON.")
            }

            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        Log.v("$TAG-Ktor-Logger", message)
                    }
                }
                level = LogLevel.ALL
                 Log.d("$TAG-Ktor", "Installed Ktor Logging plugin.")
            }

            install(Auth) {
                 bearer {
                     loadTokens {
                         val token = prefs.getString("auth_token", null)
                         if (!token.isNullOrBlank()) {
                             Log.d("$TAG-Ktor", "Auth Plugin: Loading token from prefs.")
                             BearerTokens(token, "")
                         } else {
                             Log.d("$TAG-Ktor", "Auth Plugin: No token found in prefs.")
                             null
                         }
                     }

                     sendWithoutRequest { request ->
                         val path = request.url.encodedPath
                         val shouldNotSend = path.startsWith("/api/auth/")
                         Log.d("$TAG-Ktor", "Auth Plugin: Checking sendWithoutRequest for path: $path. Should NOT send: $shouldNotSend")
                         shouldNotSend
                     }
                 }
                 Log.d("$TAG-Ktor", "Installed Ktor Auth plugin with Bearer provider.")
            }
            Log.i("$TAG-Ktor", "Ktor HttpClient configuration complete.")
        }
    }

    @Provides
    @Singleton
    fun provideNotificationRepository(
        httpClient: HttpClient,
        prefs: SharedPreferences
    ): NotificationRepository {
         Log.d(TAG, "Providing NotificationRepository implementation.")
         return edu.cit.audioscholar.domain.repository.NotificationRepositoryImpl(httpClient, prefs)
    }
}