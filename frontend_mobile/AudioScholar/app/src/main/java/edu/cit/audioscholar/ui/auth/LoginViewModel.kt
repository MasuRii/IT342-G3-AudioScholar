package edu.cit.audioscholar.ui.auth

import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import dagger.hilt.android.lifecycle.HiltViewModel
import edu.cit.audioscholar.BuildConfig
import edu.cit.audioscholar.data.remote.dto.FirebaseTokenRequest
import edu.cit.audioscholar.data.remote.dto.GitHubCodeRequest
import edu.cit.audioscholar.domain.repository.AudioRepository
import edu.cit.audioscholar.ui.main.SplashActivity
import edu.cit.audioscholar.util.Resource
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val navigateToRecordScreen: Boolean = false
)

sealed class LoginScreenEvent {
    data class ShowInfoMessage(val message: String) : LoginScreenEvent()
    object LaunchGoogleSignIn : LoginScreenEvent()
    data class LaunchGitHubSignIn(val url: Uri) : LoginScreenEvent()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val audioRepository: AudioRepository,
    private val prefs: SharedPreferences,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _loginScreenEventFlow = MutableSharedFlow<LoginScreenEvent>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val loginScreenEventFlow = _loginScreenEventFlow.asSharedFlow()

    private val _gitHubLoginCompleteSignal = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val gitHubLoginCompleteSignal = _gitHubLoginCompleteSignal.asSharedFlow()


    companion object {
        private const val KEY_GITHUB_AUTH_STATE = "github_auth_state"
        const val KEY_AUTH_TOKEN = "auth_token"
        private const val TAG = "LoginViewModel"

        private const val DEV_EMAIL = "testingdev@gmail.com"
        private const val DEV_PASSWORD = "testingdev"
        private const val DEV_OFFLINE_TOKEN = "OFFLINE_DEV_TOKEN"

        private const val GITHUB_CLIENT_ID = "Iv23liMzUNGL8JuXu40i"
        const val GITHUB_REDIRECT_URI_SCHEME = "audioscholar"
        const val GITHUB_REDIRECT_URI_HOST = "github-callback"
        private const val GITHUB_REDIRECT_URI = "audioscholar://github-callback"
        private const val GITHUB_AUTH_URL = "https://github.com/login/oauth/authorize"
        private const val GITHUB_SCOPE = "read:user user:email"
    }

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, errorMessage = null) }
    }
    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, errorMessage = null) }
    }

    fun onLoginClick() {
        if (_uiState.value.isLoading) return
        val email = _uiState.value.email.trim()
        val password = _uiState.value.password

        if (email.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Email and password cannot be empty.") }
            return
        }

        if (BuildConfig.DEBUG && email == DEV_EMAIL && password == DEV_PASSWORD) {
            Log.w(TAG, "!!! USING OFFLINE DEV LOGIN BYPASS !!!")
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }
                with(prefs.edit()) {
                    putString(KEY_AUTH_TOKEN, DEV_OFFLINE_TOKEN)
                    putBoolean(SplashActivity.KEY_IS_LOGGED_IN, true)
                    apply()
                }
                _uiState.update { it.copy(isLoading = false, navigateToRecordScreen = true) }
                Log.w(TAG, "!!! OFFLINE DEV LOGIN SUCCESSFUL !!!")
            }
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid email address.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                Log.d(TAG, "Attempting Firebase sign-in for: $email")
                val authResult = firebaseAuth.signInWithEmailAndPassword(email, password).await()
                val firebaseUser = authResult.user

                if (firebaseUser == null) {
                    Log.w(TAG, "Firebase sign-in successful but user object is null.")
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Login failed: Could not retrieve user details.") }
                    return@launch
                }
                Log.d(TAG, "Firebase sign-in successful. UID: ${firebaseUser.uid}")

                Log.d(TAG, "Fetching Firebase ID token...")
                val idTokenResult = firebaseUser.getIdToken(true).await()
                val firebaseIdToken = idTokenResult.token

                if (firebaseIdToken == null) {
                    Log.w(TAG, "Firebase ID token retrieval failed (token is null).")
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Login failed: Could not retrieve authentication token.") }
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
                            _uiState.update { it.copy(isLoading = false, navigateToRecordScreen = true) }
                        } else {
                            Log.w(TAG, "Backend verification successful but API JWT was null in response.")
                            _uiState.update { it.copy(isLoading = false, errorMessage = backendResult.message ?: "Login failed: Missing API token from server.") }
                            firebaseAuth.signOut()
                        }
                    }
                    is Resource.Error -> {
                        Log.w(TAG, "Backend verification failed: ${backendResult.message}")
                        _uiState.update { it.copy(isLoading = false, errorMessage = backendResult.message ?: "Login failed: Server validation error.") }
                        firebaseAuth.signOut()
                    }
                    is Resource.Loading -> {}
                }

            } catch (e: FirebaseAuthException) {
                Log.w(TAG, "Firebase sign-in failed: ${e.errorCode} - ${e.message}")
                val message = when (e.errorCode) {
                    "ERROR_INVALID_CREDENTIAL", "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND" -> "Invalid email or password."
                    "ERROR_USER_DISABLED" -> "This account has been disabled."
                    "ERROR_INVALID_EMAIL" -> "Please enter a valid email address."
                    else -> "Login failed: ${e.localizedMessage ?: "An unknown authentication error occurred."}"
                }
                _uiState.update { it.copy(isLoading = false, errorMessage = message) }
            } catch (e: Exception) {
                Log.e(TAG, "An unexpected error occurred during login: ${e.message}", e)
                _uiState.update { it.copy(isLoading = false, errorMessage = "Login failed: ${e.localizedMessage ?: "An unexpected error occurred."}") }
            } finally {
                if (_uiState.value.isLoading && !_uiState.value.navigateToRecordScreen) {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    fun onForgotPasswordClick() {
        viewModelScope.launch {
            _loginScreenEventFlow.emit(LoginScreenEvent.ShowInfoMessage("Forgot Password feature not implemented yet."))
        }
    }

    fun onGoogleSignInClick() {
        viewModelScope.launch {
            if (!_uiState.value.isLoading) {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                _loginScreenEventFlow.emit(LoginScreenEvent.LaunchGoogleSignIn)
            }
        }
    }

    fun handleGoogleSignInResult(account: GoogleSignInAccount?) {
        if (account?.idToken != null) {
            Log.i(TAG, "[GoogleSignIn] Google Sign-In successful. Verifying...")
            if (!_uiState.value.isLoading) {
                Log.w(TAG, "[GoogleSignIn] handleGoogleSignInResult called while not loading. Setting isLoading=true.")
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            }
            viewModelScope.launch {
                val tokenRequest = FirebaseTokenRequest(idToken = account.idToken!!)
                Log.d(TAG, "[GoogleSignIn] Sending Google ID token to backend for verification...")
                when (val backendResult = audioRepository.verifyGoogleToken(tokenRequest)) {
                    is Resource.Success -> {
                        val apiJwt = backendResult.data?.token
                        if (apiJwt != null) {
                            Log.i(TAG, "[GoogleSignIn] Backend Success. JWT received.")
                            try {
                                Log.d(TAG, "[GoogleSignIn] Saving prefs...")
                                with(prefs.edit()) {
                                    putString(KEY_AUTH_TOKEN, apiJwt)
                                    putBoolean(SplashActivity.KEY_IS_LOGGED_IN, true)
                                    apply()
                                }
                                Log.d(TAG, "[GoogleSignIn] Prefs saved.")
                                _uiState.update { it.copy(isLoading = false, navigateToRecordScreen = true) }
                                Log.d(TAG, "[GoogleSignIn] State updated for navigation.")
                            } catch (e: Exception) {
                                Log.e(TAG, "[GoogleSignIn] CRITICAL Exception during pref saving!", e)
                                _uiState.update { it.copy(isLoading = false, errorMessage = "Error processing login after Google verification.") }
                            }
                        } else {
                            Log.w(TAG, "[GoogleSignIn] Backend Success but JWT null.")
                            _uiState.update { it.copy(isLoading = false, errorMessage = backendResult.message ?: "Login failed: Missing API token from server after Google Sign-In.") }
                        }
                    }
                    is Resource.Error -> {
                        Log.w(TAG, "[GoogleSignIn] Backend Error: ${backendResult.message}")
                        _uiState.update { it.copy(isLoading = false, errorMessage = backendResult.message ?: "Login failed: Server validation error for Google Sign-In.") }
                    }
                    is Resource.Loading -> {}
                    else -> { Log.w(TAG, "[GoogleSignIn] Unknown Resource state received from repository.") }
                }
            }
        } else {
            Log.w(TAG, "[GoogleSignIn] Google Sign-In failed or token missing.")
            if (_uiState.value.isLoading && _uiState.value.errorMessage == null) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Google Sign-In failed or cancelled.") }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onGitHubSignInClick() {
        viewModelScope.launch {
            if (!_uiState.value.isLoading) {
                val state = UUID.randomUUID().toString()
                with(prefs.edit()) {
                    putString(KEY_GITHUB_AUTH_STATE, state)
                    apply()
                }
                Log.d(TAG, "Generated and saved GitHub state to SharedPreferences: $state")

                val authUri = Uri.parse(GITHUB_AUTH_URL).buildUpon()
                    .appendQueryParameter("client_id", GITHUB_CLIENT_ID)
                    .appendQueryParameter("redirect_uri", GITHUB_REDIRECT_URI)
                    .appendQueryParameter("scope", GITHUB_SCOPE)
                    .appendQueryParameter("state", state)
                    .build()

                Log.d(TAG, "Constructed GitHub Auth URL: $authUri")
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                _loginScreenEventFlow.emit(LoginScreenEvent.LaunchGitHubSignIn(authUri))
            }
        }
    }

    fun handleGitHubRedirect(code: String?, state: String?) {
        Log.d(TAG, "[GitHubRedirect] Handling redirect. Code: ${code?.take(10)}..., State: $state")

        if (!_uiState.value.isLoading) {
            Log.w(TAG, "[GitHubRedirect] handleGitHubRedirect called while not loading. Setting isLoading=true.")
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        }

        if (code.isNullOrBlank()) {
            Log.w(TAG, "[GitHubRedirect] GitHub redirect failed: Code is null or blank.")
            _uiState.update { it.copy(isLoading = false, errorMessage = "GitHub login failed: Authorization code missing.") }
            with(prefs.edit()) { remove(KEY_GITHUB_AUTH_STATE).apply() }
            return
        }

        Log.d(TAG, "[GitHubRedirect] Attempting to retrieve state from SharedPreferences with key: $KEY_GITHUB_AUTH_STATE")
        val expectedState = prefs.getString(KEY_GITHUB_AUTH_STATE, null)
        Log.d(TAG, "[GitHubRedirect] Retrieved state from SharedPreferences: $expectedState")

        with(prefs.edit()) {
            remove(KEY_GITHUB_AUTH_STATE)
            apply()
        }

        if (expectedState == null) {
            Log.e(TAG, "[GitHubRedirect] GitHub redirect failed: Could not retrieve expected state from SharedPreferences.")
            _uiState.update { it.copy(isLoading = false, errorMessage = "GitHub login failed: Security check error (state missing).") }
            return
        }

        if (state == null || state != expectedState) {
            Log.e(TAG, "[GitHubRedirect] GitHub redirect failed: State mismatch. Expected='$expectedState', Received='$state'")
            _uiState.update { it.copy(isLoading = false, errorMessage = "GitHub login failed: Security check error (state mismatch).") }
            return
        }
        Log.i(TAG, "[GitHubRedirect] GitHub state verified successfully.")


        viewModelScope.launch {
            val request = GitHubCodeRequest(code = code, state = state)
            Log.d(TAG, "[GitHubRedirect] Sending GitHub code to backend repository...")

            var finalState = _uiState.value.copy(isLoading = false)
            var signalActivity = false

            when (val backendResult = audioRepository.verifyGitHubCode(request)) {
                is Resource.Success -> {
                    val apiJwt = backendResult.data?.token
                    if (apiJwt != null) {
                        Log.i(TAG, "[GitHubRedirect] Backend Success. JWT received.")
                        try {
                            Log.d(TAG, "[GitHubRedirect] Saving prefs...")
                            with(prefs.edit()) {
                                putString(KEY_AUTH_TOKEN, apiJwt)
                                putBoolean(SplashActivity.KEY_IS_LOGGED_IN, true)
                                apply()
                            }
                            Log.d(TAG, "[GitHubRedirect] Prefs saved.")
                            signalActivity = true
                            finalState = finalState.copy(errorMessage = null)
                        } catch (e: Exception) {
                            Log.e(TAG, "[GitHubRedirect] CRITICAL Exception during pref saving!", e)
                            finalState = finalState.copy(errorMessage = "Critical error after login verification.")
                        }
                    } else {
                        Log.w(TAG, "[GitHubRedirect] Backend Success but JWT null.")
                        finalState = finalState.copy(errorMessage = backendResult.message ?: "Login failed: Missing API token from server after GitHub Sign-In.")
                    }
                }
                is Resource.Error -> {
                    Log.w(TAG, "[GitHubRedirect] Backend Error: ${backendResult.message}")
                    finalState = finalState.copy(errorMessage = backendResult.message ?: "Login failed: Server validation error for GitHub Sign-In.")
                }
                is Resource.Loading -> {
                    finalState = _uiState.value.copy(isLoading = true)
                }
            }

            _uiState.value = finalState
            Log.d(TAG, "[GitHubRedirect] Final UI state updated. isLoading=${finalState.isLoading}")

            if (signalActivity) {
                Log.d(TAG, "[GitHubRedirect] Emitting gitHubLoginCompleteSignal to MainActivity...")
                _gitHubLoginCompleteSignal.emit(Unit)
            }
        }
    }

    fun onNavigationHandled() {
        Log.d(TAG, "onNavigationHandled called, resetting navigateToRecordScreen flag.")
        _uiState.update { it.copy(navigateToRecordScreen = false) }
    }

    fun consumeErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

}