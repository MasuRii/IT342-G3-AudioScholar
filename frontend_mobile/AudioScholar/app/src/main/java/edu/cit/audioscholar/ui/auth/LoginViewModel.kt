package edu.cit.audioscholar.ui.auth

import android.content.SharedPreferences
import android.util.Log
import android.util.Patterns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import dagger.hilt.android.lifecycle.HiltViewModel
import edu.cit.audioscholar.BuildConfig
import edu.cit.audioscholar.data.remote.dto.FirebaseTokenRequest
import edu.cit.audioscholar.domain.repository.AudioRepository
import edu.cit.audioscholar.ui.main.SplashActivity
import edu.cit.audioscholar.util.Resource
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

sealed class LoginEvent {
    object LoginSuccess : LoginEvent()
    data class ShowInfoMessage(val message: String) : LoginEvent()
    object LaunchGoogleSignIn : LoginEvent()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val audioRepository: AudioRepository,
    private val prefs: SharedPreferences,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    var uiState by mutableStateOf(LoginUiState())
        private set

    private val _eventFlow = MutableSharedFlow<LoginEvent>()
    val eventFlow = _eventFlow.asSharedFlow()

    companion object {
        const val KEY_AUTH_TOKEN = "auth_token"
        private const val TAG = "LoginViewModel"

        private const val DEV_EMAIL = "testingdev@gmail.com"
        private const val DEV_PASSWORD = "testingdev"
        private const val DEV_OFFLINE_TOKEN = "OFFLINE_DEV_TOKEN"
    }

    fun onEmailChange(email: String) {
        uiState = uiState.copy(email = email, errorMessage = null)
    }

    fun onPasswordChange(password: String) {
        uiState = uiState.copy(password = password, errorMessage = null)
    }

    fun onLoginClick() {
        if (uiState.isLoading) return

        val email = uiState.email.trim()
        val password = uiState.password

        if (email.isBlank() || password.isBlank()) {
            uiState = uiState.copy(errorMessage = "Email and password cannot be empty.")
            return
        }


        if (BuildConfig.DEBUG && email == DEV_EMAIL && password == DEV_PASSWORD) {
            Log.w(TAG, "!!! USING OFFLINE DEV LOGIN BYPASS !!!")
            viewModelScope.launch {
                uiState = uiState.copy(isLoading = true)
                with(prefs.edit()) {
                    putString(KEY_AUTH_TOKEN, DEV_OFFLINE_TOKEN)
                    putBoolean(SplashActivity.KEY_IS_LOGGED_IN, true)
                    apply()
                }
                uiState = uiState.copy(isLoading = false)
                _eventFlow.emit(LoginEvent.LoginSuccess)
                Log.w(TAG, "!!! OFFLINE DEV LOGIN SUCCESSFUL !!!")
            }
            return
        }


        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            uiState = uiState.copy(errorMessage = "Please enter a valid email address.")
            return
        }

        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, errorMessage = null)
            try {
                Log.d(TAG, "Attempting Firebase sign-in for: $email")
                val authResult = firebaseAuth.signInWithEmailAndPassword(email, password).await()
                val firebaseUser = authResult.user

                if (firebaseUser == null) {
                    Log.w(TAG, "Firebase sign-in successful but user object is null.")
                    uiState = uiState.copy(isLoading = false, errorMessage = "Login failed: Could not retrieve user details.")
                    return@launch
                }
                Log.d(TAG, "Firebase sign-in successful. UID: ${firebaseUser.uid}")

                Log.d(TAG, "Fetching Firebase ID token...")
                val idTokenResult = firebaseUser.getIdToken(true).await()
                val firebaseIdToken = idTokenResult.token

                if (firebaseIdToken == null) {
                    Log.w(TAG, "Firebase ID token retrieval failed (token is null).")
                    uiState = uiState.copy(isLoading = false, errorMessage = "Login failed: Could not retrieve authentication token.")
                    firebaseAuth.signOut()
                    return@launch
                }
                Log.d(TAG, "Firebase ID token fetched successfully.")

                val tokenRequest = FirebaseTokenRequest(idToken = firebaseIdToken)
                Log.d(TAG, "Sending Firebase ID token to backend for verification...")
                when (val backendResult = audioRepository.verifyFirebaseToken(tokenRequest)) {
                    is Resource.Success -> {
                        val apiJwt = backendResult.data?.token
                        if (apiJwt != null) {
                            Log.i(TAG, "Backend verification successful. API JWT received.")
                            with(prefs.edit()) {
                                putString(KEY_AUTH_TOKEN, apiJwt)
                                putBoolean(SplashActivity.KEY_IS_LOGGED_IN, true)
                                apply()
                            }
                            uiState = uiState.copy(isLoading = false)
                            _eventFlow.emit(LoginEvent.LoginSuccess)
                        } else {
                            Log.w(TAG, "Backend verification successful but API JWT was null in response.")
                            uiState = uiState.copy(
                                isLoading = false,
                                errorMessage = backendResult.message ?: "Login failed: Missing API token from server."
                            )
                            firebaseAuth.signOut()
                        }
                    }
                    is Resource.Error -> {
                        Log.w(TAG, "Backend verification failed: ${backendResult.message}")
                        uiState = uiState.copy(
                            isLoading = false,
                            errorMessage = backendResult.message ?: "Login failed: Server validation error."
                        )
                        firebaseAuth.signOut()
                    }
                    is Resource.Loading -> {
                    }
                }

            } catch (e: FirebaseAuthException) {
                Log.w(TAG, "Firebase sign-in failed: ${e.errorCode} - ${e.message}")
                val message = when (e.errorCode) {
                    "ERROR_INVALID_CREDENTIAL", "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND" -> "Invalid email or password."
                    "ERROR_USER_DISABLED" -> "This account has been disabled."
                    "ERROR_INVALID_EMAIL" -> "Please enter a valid email address."
                    else -> "Login failed: ${e.localizedMessage ?: "An unknown authentication error occurred."}"
                }
                uiState = uiState.copy(isLoading = false, errorMessage = message)
            } catch (e: Exception) {
                Log.e(TAG, "An unexpected error occurred during login: ${e.message}", e)
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = "Login failed: ${e.localizedMessage ?: "An unexpected error occurred."}"
                )
            } finally {
                if (uiState.isLoading) {
                    uiState = uiState.copy(isLoading = false)
                }
            }
        }
    }

    fun onForgotPasswordClick() {
        viewModelScope.launch {
            _eventFlow.emit(LoginEvent.ShowInfoMessage("Forgot Password feature not implemented yet."))
        }
    }

    fun onGoogleSignInClick() {
        viewModelScope.launch {
            if (!uiState.isLoading) {
                uiState = uiState.copy(isLoading = true, errorMessage = null)
                _eventFlow.emit(LoginEvent.LaunchGoogleSignIn)
            }
        }
    }

    fun handleGoogleSignInResult(account: GoogleSignInAccount?) {
        if (account != null) {
            val googleIdToken = account.idToken
            if (googleIdToken != null) {
                Log.i(TAG, "Google Sign-In successful. ID Token received. Verifying with backend...")
                viewModelScope.launch {
                    val tokenRequest = FirebaseTokenRequest(idToken = googleIdToken)
                    when (val backendResult = audioRepository.verifyGoogleToken(tokenRequest)) {
                        is Resource.Success -> {
                            val apiJwt = backendResult.data?.token
                            if (apiJwt != null) {
                                Log.i(TAG, "Backend Google verification successful. API JWT received.")
                                with(prefs.edit()) {
                                    putString(KEY_AUTH_TOKEN, apiJwt)
                                    putBoolean(SplashActivity.KEY_IS_LOGGED_IN, true)
                                    apply()
                                }
                                uiState = uiState.copy(isLoading = false)
                                _eventFlow.emit(LoginEvent.LoginSuccess)
                            } else {
                                Log.w(TAG, "Backend Google verification successful but API JWT was null.")
                                uiState = uiState.copy(
                                    isLoading = false,
                                    errorMessage = backendResult.message ?: "Login failed: Missing API token from server after Google Sign-In."
                                )
                            }
                        }
                        is Resource.Error -> {
                            Log.w(TAG, "Backend Google verification failed: ${backendResult.message}")
                            uiState = uiState.copy(
                                isLoading = false,
                                errorMessage = backendResult.message ?: "Login failed: Server validation error for Google Sign-In."
                            )
                        }
                        is Resource.Loading -> {
                        }
                    }
                }
            } else {
                Log.w(TAG, "Google Sign-In successful but ID Token is null.")
                uiState = uiState.copy(isLoading = false, errorMessage = "Google Sign-In failed: Could not retrieve token.")
            }
        } else {
            Log.w(TAG, "Google Sign-In failed or was cancelled by user.")
            if (uiState.errorMessage == null) {
                uiState = uiState.copy(isLoading = false, errorMessage = "Google Sign-In failed or cancelled.")
            } else {
                uiState = uiState.copy(isLoading = false)
            }
        }
    }


    fun onGitHubSignInClick() {
        viewModelScope.launch {
            _eventFlow.emit(LoginEvent.ShowInfoMessage("GitHub Sign-In not implemented yet."))
        }
    }

    fun consumeErrorMessage() {
        uiState = uiState.copy(errorMessage = null)
    }

    fun consumeInfoMessage() {
    }
}