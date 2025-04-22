package edu.cit.audioscholar.domain.repository

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.google.gson.Gson
import edu.cit.audioscholar.R
import edu.cit.audioscholar.data.remote.dto.AuthResponse
import edu.cit.audioscholar.data.remote.dto.ChangePasswordRequest
import edu.cit.audioscholar.data.remote.dto.FirebaseTokenRequest
import edu.cit.audioscholar.data.remote.dto.GitHubCodeRequest
import edu.cit.audioscholar.data.remote.dto.LoginRequest
import edu.cit.audioscholar.data.remote.dto.RegistrationRequest
import edu.cit.audioscholar.data.remote.dto.UpdateUserProfileRequest
import edu.cit.audioscholar.data.remote.dto.UserProfileDto
import edu.cit.audioscholar.data.remote.service.ApiService
import edu.cit.audioscholar.util.Resource
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG_AUTH_REPO = "AuthRepositoryImpl"

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val application: Application,
    private val gson: Gson
) : AuthRepository {

    override suspend fun registerUser(request: RegistrationRequest): AuthResult {
        return try {
            Log.d(TAG_AUTH_REPO, "Attempting registration for email: ${request.email}")
            val response = apiService.registerUser(request)

            if (response.isSuccessful) {
                val authResponse = response.body()
                if (authResponse != null) {
                    Log.i(TAG_AUTH_REPO, "Registration successful: ${authResponse.message ?: "No message"}")
                    Resource.Success(authResponse)
                } else {
                    Log.w(TAG_AUTH_REPO, "Registration successful (Code: ${response.code()}) but response body was null.")
                    Resource.Success(AuthResponse(success = true, message = "Registration successful"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMessage = try {
                    gson.fromJson(errorBody, AuthResponse::class.java)?.message ?: errorBody ?: "Unknown server error"
                } catch (e: Exception) {
                    errorBody ?: "Unknown server error (Code: ${response.code()})"
                }
                Log.e(TAG_AUTH_REPO, "Registration failed: ${response.code()} - $errorMessage")
                Resource.Error(errorMessage)
            }
        } catch (e: IOException) {
            Log.e(TAG_AUTH_REPO, "Network/IO exception during registration: ${e.message}", e)
            Resource.Error(application.getString(R.string.error_network_connection))
        } catch (e: HttpException) {
            Log.e(TAG_AUTH_REPO, "HTTP exception during registration: ${e.code()} - ${e.message()}", e)
            Resource.Error("HTTP Error: ${e.code()} ${e.message()}")
        } catch (e: Exception) {
            Log.e(TAG_AUTH_REPO, "Unexpected exception during registration: ${e.message}", e)
            Resource.Error(application.getString(R.string.error_unexpected_registration, e.message ?: "Unknown error"))
        }
    }

    override suspend fun loginUser(request: LoginRequest): AuthResult {
        return try {
            Log.d(TAG_AUTH_REPO, "Attempting login for email: ${request.email}")
            val response = apiService.loginUser(request)

            if (response.isSuccessful) {
                val authResponse = response.body()
                if (authResponse != null) {
                    if (authResponse.token != null) {
                        Log.i(TAG_AUTH_REPO, "Login successful. Token received.")
                        Resource.Success(authResponse)
                    } else {
                        Log.w(TAG_AUTH_REPO, "Login successful (Code: ${response.code()}) but token was null in response.")
                        Resource.Error("Login successful but token missing.", authResponse)
                    }
                } else {
                    Log.w(TAG_AUTH_REPO, "Login successful (Code: ${response.code()}) but response body was null.")
                    Resource.Error("Login successful but response body was empty.")
                }
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMessage = try {
                    gson.fromJson(errorBody, AuthResponse::class.java)?.message ?: errorBody ?: "Unknown server error"
                } catch (e: Exception) {
                    errorBody ?: "Unknown server error (Code: ${response.code()})"
                }
                Log.e(TAG_AUTH_REPO, "Login failed: ${response.code()} - $errorMessage")
                Resource.Error(errorMessage)
            }
        } catch (e: IOException) {
            Log.e(TAG_AUTH_REPO, "Network/IO exception during login: ${e.message}", e)
            Resource.Error(application.getString(R.string.error_network_connection))
        } catch (e: HttpException) {
            Log.e(TAG_AUTH_REPO, "HTTP exception during login: ${e.code()} - ${e.message()}", e)
            Resource.Error("HTTP Error: ${e.code()} ${e.message()}")
        } catch (e: Exception) {
            Log.e(TAG_AUTH_REPO, "Unexpected exception during login: ${e.message}", e)
            Resource.Error(application.getString(R.string.error_unexpected_login, e.message ?: "Unknown error"))
        }
    }

    override suspend fun verifyFirebaseToken(request: FirebaseTokenRequest): AuthResult {
        return try {
            Log.d(TAG_AUTH_REPO, "Sending Firebase ID token to backend for verification.")
            val response = apiService.verifyFirebaseToken(request)

            if (response.isSuccessful) {
                val authResponse = response.body()
                if (authResponse != null && authResponse.token != null) {
                    Log.i(TAG_AUTH_REPO, "Firebase token verified successfully by backend. API JWT received.")
                    Resource.Success(authResponse)
                } else {
                    val errorMsg = "Backend verification successful but response or API token was null."
                    Log.w(TAG_AUTH_REPO, errorMsg)
                    Resource.Error(errorMsg, authResponse)
                }
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMessage = try {
                    gson.fromJson(errorBody, AuthResponse::class.java)?.message ?: errorBody ?: "Unknown backend verification error"
                } catch (e: Exception) {
                    errorBody ?: "Unknown backend verification error (Code: ${response.code()})"
                }
                Log.e(TAG_AUTH_REPO, "Backend verification failed: ${response.code()} - $errorMessage")
                Resource.Error(errorMessage)
            }
        } catch (e: IOException) {
            Log.e(TAG_AUTH_REPO, "Network/IO exception during token verification call: ${e.message}", e)
            Resource.Error(application.getString(R.string.error_network_connection))
        } catch (e: HttpException) {
            Log.e(TAG_AUTH_REPO, "HTTP exception during token verification call: ${e.code()} - ${e.message()}", e)
            Resource.Error("HTTP Error: ${e.code()} ${e.message()}")
        } catch (e: Exception) {
            Log.e(TAG_AUTH_REPO, "Unexpected exception during token verification call: ${e.message}", e)
            Resource.Error(application.getString(R.string.upload_error_unexpected, e.message ?: "Unknown error"))
        }
    }

    override suspend fun verifyGoogleToken(request: FirebaseTokenRequest): AuthResult {
        return try {
            Log.d(TAG_AUTH_REPO, "Sending Google ID token to backend for verification.")
            val response = apiService.verifyGoogleToken(request)

            if (response.isSuccessful) {
                val authResponse = response.body()
                if (authResponse != null && authResponse.token != null) {
                    Log.i(TAG_AUTH_REPO, "Google token verified successfully by backend. API JWT received.")
                    Resource.Success(authResponse)
                } else {
                    val errorMsg = "Backend Google verification successful but response or API token was null."
                    Log.w(TAG_AUTH_REPO, errorMsg)
                    Resource.Error(errorMsg, authResponse)
                }
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMessage = try {
                    gson.fromJson(errorBody, AuthResponse::class.java)?.message ?: errorBody ?: "Unknown backend Google verification error"
                } catch (e: Exception) {
                    errorBody ?: "Unknown backend Google verification error (Code: ${response.code()})"
                }
                Log.e(TAG_AUTH_REPO, "Backend Google verification failed: ${response.code()} - $errorMessage")
                Resource.Error(errorMessage)
            }
        } catch (e: IOException) {
            Log.e(TAG_AUTH_REPO, "Network/IO exception during Google token verification call: ${e.message}", e)
            Resource.Error(application.getString(R.string.error_network_connection))
        } catch (e: HttpException) {
            Log.e(TAG_AUTH_REPO, "HTTP exception during Google token verification call: ${e.code()} - ${e.message()}", e)
            Resource.Error("HTTP Error: ${e.code()} ${e.message()}")
        } catch (e: Exception) {
            Log.e(TAG_AUTH_REPO, "Unexpected exception during Google token verification call: ${e.message}", e)
            Resource.Error(application.getString(R.string.upload_error_unexpected, e.message ?: "Unknown error"))
        }
    }

    override suspend fun verifyGitHubCode(request: GitHubCodeRequest): AuthResult {
        return try {
            Log.d(TAG_AUTH_REPO, "Sending GitHub code to backend for verification.")
            val response = apiService.verifyGitHubCode(request)

            if (response.isSuccessful) {
                val authResponse = response.body()
                if (authResponse != null && authResponse.token != null) {
                    Log.i(TAG_AUTH_REPO, "GitHub code verified successfully by backend. API JWT received.")
                    Resource.Success(authResponse)
                } else {
                    val errorMsg = "Backend GitHub verification successful but response or API token was null."
                    Log.w(TAG_AUTH_REPO, errorMsg)
                    Resource.Error(errorMsg, authResponse)
                }
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMessage = try {
                    gson.fromJson(errorBody, AuthResponse::class.java)?.message ?: errorBody ?: "Unknown backend GitHub verification error"
                } catch (e: Exception) {
                    errorBody ?: "Unknown backend GitHub verification error (Code: ${response.code()})"
                }
                Log.e(TAG_AUTH_REPO, "Backend GitHub verification failed: ${response.code()} - $errorMessage")
                Resource.Error(errorMessage)
            }
        } catch (e: IOException) {
            Log.e(TAG_AUTH_REPO, "Network/IO exception during GitHub code verification call: ${e.message}", e)
            Resource.Error(application.getString(R.string.error_network_connection))
        } catch (e: HttpException) {
            Log.e(TAG_AUTH_REPO, "HTTP exception during GitHub code verification call: ${e.code()} - ${e.message()}", e)
            Resource.Error("HTTP Error: ${e.code()} ${e.message()}")
        } catch (e: Exception) {
            Log.e(TAG_AUTH_REPO, "Unexpected exception during GitHub code verification call: ${e.message}", e)
            Resource.Error(application.getString(R.string.upload_error_unexpected, e.message ?: "Unknown error"))
        }
    }

    override suspend fun getUserProfile(): UserProfileResult {
        return try {
            Log.d(TAG_AUTH_REPO, "Attempting to fetch user profile.")
            val response = apiService.getUserProfile()

            if (response.isSuccessful) {
                val userProfile = response.body()
                if (userProfile != null) {
                    Log.i(TAG_AUTH_REPO, "User profile fetched successfully. Email: ${userProfile.email}")
                    Resource.Success(userProfile)
                } else {
                    Log.w(TAG_AUTH_REPO, "Get profile successful (Code: ${response.code()}) but response body was null.")
                    Resource.Error("Profile data missing in response.")
                }
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMessage = try {
                    gson.fromJson(errorBody, AuthResponse::class.java)?.message ?: errorBody ?: "Unknown server error"
                } catch (e: Exception) {
                    errorBody ?: "Unknown server error (Code: ${response.code()})"
                }
                Log.e(TAG_AUTH_REPO, "Get profile failed: ${response.code()} - $errorMessage")
                if (response.code() == 401 || response.code() == 403) {
                    Resource.Error(application.getString(R.string.error_unauthorized))
                } else {
                    Resource.Error(errorMessage)
                }
            }
        } catch (e: IOException) {
            Log.e(TAG_AUTH_REPO, "Network/IO exception during get profile: ${e.message}", e)
            Resource.Error(application.getString(R.string.error_network_connection))
        } catch (e: HttpException) {
            Log.e(TAG_AUTH_REPO, "HTTP exception during get profile: ${e.code()} - ${e.message()}", e)
            if (e.code() == 401 || e.code() == 403) {
                Resource.Error(application.getString(R.string.error_unauthorized))
            } else {
                Resource.Error("HTTP Error: ${e.code()} ${e.message()}")
            }
        } catch (e: Exception) {
            Log.e(TAG_AUTH_REPO, "Unexpected exception during get profile: ${e.message}", e)
            Resource.Error(application.getString(R.string.error_unexpected_profile_fetch, e.message ?: "Unknown error"))
        }
    }

    override suspend fun updateUserProfile(request: UpdateUserProfileRequest): UserProfileResult {
        return try {
            Log.d(TAG_AUTH_REPO, "Attempting to update user profile with display name: ${request.displayName ?: "Not Provided"}")
            val response = apiService.updateUserProfile(request)

            if (response.isSuccessful) {
                val updatedProfile = response.body()
                if (updatedProfile != null) {
                    Log.i(TAG_AUTH_REPO, "User profile updated successfully. Email: ${updatedProfile.email}")
                    Resource.Success(updatedProfile)
                } else {
                    Log.w(TAG_AUTH_REPO, "Update profile successful (Code: ${response.code()}) but response body was null.")
                    Resource.Success(UserProfileDto(null, null, null, null, null, null))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMessage = try {
                    gson.fromJson(errorBody, AuthResponse::class.java)?.message ?: errorBody ?: "Unknown server error"
                } catch (e: Exception) {
                    errorBody ?: "Unknown server error (Code: ${response.code()})"
                }
                Log.e(TAG_AUTH_REPO, "Update profile failed: ${response.code()} - $errorMessage")
                if (response.code() == 401 || response.code() == 403) {
                    Resource.Error(application.getString(R.string.error_unauthorized))
                } else if (response.code() == 400) {
                    Resource.Error("Update failed: Invalid data. ${errorMessage ?: ""}".trim())
                }
                else {
                    Resource.Error(errorMessage)
                }
            }
        } catch (e: IOException) {
            Log.e(TAG_AUTH_REPO, "Network/IO exception during update profile: ${e.message}", e)
            Resource.Error(application.getString(R.string.error_network_connection))
        } catch (e: HttpException) {
            Log.e(TAG_AUTH_REPO, "HTTP exception during update profile: ${e.code()} - ${e.message()}", e)
            if (e.code() == 401 || e.code() == 403) {
                Resource.Error(application.getString(R.string.error_unauthorized))
            } else {
                Resource.Error("HTTP Error: ${e.code()} ${e.message()}")
            }
        } catch (e: Exception) {
            Log.e(TAG_AUTH_REPO, "Unexpected exception during update profile: ${e.message}", e)
            Resource.Error(application.getString(R.string.error_unexpected_profile_update, e.message ?: "Unknown error"))
        }
    }

    override suspend fun uploadAvatar(imageUri: Uri): UserProfileResult {
        val contentResolver = application.contentResolver
        var fileName = "avatar_upload"
        var fileSize: Long? = null

        contentResolver.query(imageUri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            cursor.moveToFirst()
            if (nameIndex != -1) {
                fileName = cursor.getString(nameIndex) ?: fileName
            }
            if (sizeIndex != -1) {
                fileSize = cursor.getLong(sizeIndex)
            }
        }

        val mimeType = contentResolver.getType(imageUri)
        if (mimeType == null) {
            Log.e(TAG_AUTH_REPO, "Could not determine MIME type for avatar URI: $imageUri")
            return Resource.Error("Could not determine file type for selected image.")
        }

        Log.d(TAG_AUTH_REPO, "Preparing avatar upload. URI: $imageUri, Name: $fileName, Type: $mimeType, Size: $fileSize")

        return try {
            val requestBody = object : RequestBody() {
                override fun contentType() = mimeType.toMediaTypeOrNull()

                override fun contentLength(): Long = fileSize ?: -1

                override fun writeTo(sink: BufferedSink) {
                    contentResolver.openInputStream(imageUri)?.use { inputStream ->
                        sink.writeAll(inputStream.source())
                    } ?: throw IOException("Could not open input stream for URI: $imageUri")
                }
            }

            val bodyPart = MultipartBody.Part.createFormData(
                "avatar",
                fileName,
                requestBody
            )

            Log.d(TAG_AUTH_REPO, "Attempting to upload avatar via API service.")
            val response = apiService.uploadAvatar(bodyPart)

            if (response.isSuccessful) {
                val updatedProfile = response.body()
                if (updatedProfile != null) {
                    Log.i(TAG_AUTH_REPO, "Avatar uploaded successfully. New profile URL: ${updatedProfile.profileImageUrl}")
                    Resource.Success(updatedProfile)
                } else {
                    Log.w(TAG_AUTH_REPO, "Avatar upload successful (Code: ${response.code()}) but response body was null.")
                    Resource.Error("Avatar upload succeeded but failed to get updated profile data.")
                }
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMessage = try {
                    gson.fromJson(errorBody, AuthResponse::class.java)?.message ?: gson.fromJson(errorBody, UserProfileDto::class.java)?.displayName ?: errorBody ?: "Unknown server error during avatar upload"
                } catch (e: Exception) {
                    errorBody ?: "Unknown server error (Code: ${response.code()})"
                }
                Log.e(TAG_AUTH_REPO, "Avatar upload failed: ${response.code()} - $errorMessage")
                if (response.code() == 401 || response.code() == 403) {
                    Resource.Error(application.getString(R.string.error_unauthorized))
                } else if (response.code() == 413) {
                    Resource.Error(application.getString(R.string.upload_error_size_exceeded_avatar))
                } else if (response.code() == 415) {
                    Resource.Error(application.getString(R.string.upload_error_unsupported_format_avatar))
                }
                else {
                    Resource.Error("Avatar upload failed: $errorMessage")
                }
            }
        } catch (e: IOException) {
            Log.e(TAG_AUTH_REPO, "IOException during avatar upload preparation/call: ${e.message}", e)
            Resource.Error(application.getString(R.string.upload_error_read_failed, e.message ?: "Could not read image file"))
        } catch (e: HttpException) {
            Log.e(TAG_AUTH_REPO, "HTTP exception during avatar upload: ${e.code()} - ${e.message()}", e)
            if (e.code() == 401 || e.code() == 403) {
                Resource.Error(application.getString(R.string.error_unauthorized))
            } else {
                Resource.Error("HTTP Error during avatar upload: ${e.code()} ${e.message()}")
            }
        } catch (e: Exception) {
            Log.e(TAG_AUTH_REPO, "Unexpected exception during avatar upload: ${e.message}", e)
            Resource.Error(application.getString(R.string.upload_error_unexpected, e.message ?: "Unknown error"))
        }
    }

    override suspend fun changePassword(request: ChangePasswordRequest): SimpleResult {
        return try {
            Log.d(TAG_AUTH_REPO, "Attempting to change password.")
            val response = apiService.changePassword(request)

            if (response.isSuccessful) {
                Log.i(TAG_AUTH_REPO, "Password changed successfully via API.")
                Resource.Success(Unit)
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMessage = try {
                    gson.fromJson(errorBody, AuthResponse::class.java)?.message ?: errorBody ?: "Unknown server error"
                } catch (e: Exception) {
                    errorBody ?: "Unknown server error (Code: ${response.code()})"
                }
                Log.e(TAG_AUTH_REPO, "Change password failed: ${response.code()} - $errorMessage")
                when (response.code()) {
                    400 -> Resource.Error(errorMessage ?: application.getString(R.string.error_change_password_invalid_current))
                    401, 403 -> Resource.Error(application.getString(R.string.error_unauthorized))
                    else -> Resource.Error(errorMessage ?: "Failed to change password.")
                }
            }
        } catch (e: IOException) {
            Log.e(TAG_AUTH_REPO, "Network/IO exception during change password: ${e.message}", e)
            Resource.Error(application.getString(R.string.error_network_connection))
        } catch (e: HttpException) {
            Log.e(TAG_AUTH_REPO, "HTTP exception during change password: ${e.code()} - ${e.message()}", e)
            if (e.code() == 401 || e.code() == 403) {
                Resource.Error(application.getString(R.string.error_unauthorized))
            } else {
                Resource.Error("HTTP Error: ${e.code()} ${e.message()}")
            }
        } catch (e: Exception) {
            Log.e(TAG_AUTH_REPO, "Unexpected exception during change password: ${e.message}", e)
            Resource.Error(application.getString(R.string.error_unexpected_change_password, e.message ?: "Unknown error"))
        }
    }
}