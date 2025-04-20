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
import edu.cit.audioscholar.domain.repository.AudioRepositoryImpl
import edu.cit.audioscholar.domain.repository.AudioRepository
import edu.cit.audioscholar.ui.auth.LoginViewModel
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
            val token = prefs.getString(LoginViewModel.KEY_AUTH_TOKEN, null)
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
            var primaryException: IOException? = null
            var primaryResponse: Response? = null

            if (primaryHttpUrl != null && originalRequest.url.host == primaryHttpUrl.host) {
                try {
                    Log.d(TAG, "Attempting request to primary URL: ${originalRequest.url}")
                    primaryResponse = chain.proceed(originalRequest)

                    if (primaryResponse.isSuccessful) {
                        Log.d(TAG, "Primary URL request successful (code: ${primaryResponse.code}). Not falling back.")
                        return@Interceptor primaryResponse
                    } else {
                        Log.w(TAG, "Primary URL request returned unsuccessful code: ${primaryResponse.code}. Will attempt fallback.")
                        primaryResponse.close()
                        primaryResponse = null
                    }

                } catch (e: IOException) {
                    Log.w(TAG, "Primary URL request failed with IOException (${e::class.java.simpleName}): ${e.message}. Will attempt fallback.")
                    primaryException = e
                    primaryResponse?.close()
                    primaryResponse = null
                }
            } else {
                Log.d(TAG, "Request URL host (${originalRequest.url.host}) does not match primary host (${primaryHttpUrl?.host}). Proceeding without fallback interceptor logic.")
                return@Interceptor chain.proceed(originalRequest)
            }


            if (fallbackHttpUrl != null) {
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
                    primaryException?.addSuppressed(fallbackException)
                    throw primaryException ?: fallbackException
                }
            } else {
                Log.e(TAG, "Primary URL failed, but fallback URL is not configured or invalid. Cannot fallback.")
                throw primaryException ?: IOException("Primary request to ${originalRequest.url} failed (unsuccessful response) and no valid fallback URL configured.")
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