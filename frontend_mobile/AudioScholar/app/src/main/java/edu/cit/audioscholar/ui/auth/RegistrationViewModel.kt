package edu.cit.audioscholar.ui.auth

import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import edu.cit.audioscholar.data.remote.dto.FirebaseTokenRequest
import edu.cit.audioscholar.data.remote.dto.RegistrationRequest
import edu.cit.audioscholar.domain.repository.AuthRepository
import edu.cit.audioscholar.ui.main.SplashActivity
import edu.cit.audioscholar.util.Resource
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.regex.Pattern
import javax.inject.Inject

data class PasswordValidationResult(
    val meetsLength: Boolean = false,
    val hasUppercase: Boolean = false,
    val hasLowercase: Boolean = false,
    val hasDigit: Boolean = false,
    val hasSpecial: Boolean = false
) {
    val isValid: Boolean
        get() = meetsLength && hasUppercase && hasLowercase && hasDigit && hasSpecial
}

data class RegistrationUiState(
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val termsAccepted: Boolean = false,
    val isPasswordVisible: Boolean = false,
    val registrationInProgress: Boolean = false,
    val isGoogleRegistrationLoading: Boolean = false,
    val isGitHubRegistrationLoading: Boolean = false,
    val errorMessage: String? = null,
    val registrationSuccess: Boolean = false
) {
    val isAnyLoading: Boolean
        get() = registrationInProgress || isGoogleRegistrationLoading || isGitHubRegistrationLoading
}

sealed class RegistrationScreenEvent {
    data class ShowMessage(val message: String) : RegistrationScreenEvent()
    object LaunchGoogleSignIn : RegistrationScreenEvent()
    data class LaunchGitHubSignIn(val url: Uri) : RegistrationScreenEvent()
}

@HiltViewModel
class RegistrationViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val prefs: SharedPreferences,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegistrationUiState())
    val uiState: StateFlow<RegistrationUiState> = _uiState.asStateFlow()

    private val _registrationScreenEventFlow = MutableSharedFlow<RegistrationScreenEvent>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val registrationScreenEventFlow = _registrationScreenEventFlow.asSharedFlow()

    private val nameValidationRegex = Pattern.compile("^[\\p{L}\\s'-]+$")
    private val uppercaseRegex = Pattern.compile(".*[A-Z].*")
    private val lowercaseRegex = Pattern.compile(".*[a-z].*")
    private val digitRegex = Pattern.compile(".*\\d.*")
    private val specialCharRegex = Pattern.compile(".*[!@#$%^&*()].*")
    private val minPasswordLength = 8

    val passwordValidationResult: StateFlow<PasswordValidationResult> = uiState
        .map { getPasswordValidationDetails(it.password) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PasswordValidationResult()
        )

    companion object {
        private const val KEY_GITHUB_AUTH_STATE = "github_auth_state"
        const val KEY_AUTH_TOKEN = "auth_token"
        private const val TAG = "RegistrationViewModel"
        private const val GITHUB_CLIENT_ID = "Iv23liMzUNGL8JuXu40i"
        const val GITHUB_REDIRECT_URI_SCHEME = "audioscholar"
        const val GITHUB_REDIRECT_URI_HOST = "github-callback"
        private const val GITHUB_REDIRECT_URI = "audioscholar://github-callback"
        private const val GITHUB_AUTH_URL = "https://github.com/login/oauth/authorize"
        private const val GITHUB_SCOPE = "read:user user:email"
    }

    fun onFirstNameChange(value: String) {
        _uiState.update { it.copy(firstName = value, errorMessage = null) }
    }
    fun onLastNameChange(value: String) {
        _uiState.update { it.copy(lastName = value, errorMessage = null) }
    }
    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, errorMessage = null) }
    }
    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, errorMessage = null) }
    }
    fun onConfirmPasswordChange(value: String) {
        _uiState.update { it.copy(confirmPassword = value, errorMessage = null) }
    }
    fun onTermsAcceptedChange(accepted: Boolean) {
        _uiState.update { it.copy(termsAccepted = accepted, errorMessage = null) }
    }
    fun togglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    private fun isValidName(name: String): Boolean {
        return name.isNotBlank() && nameValidationRegex.matcher(name.trim()).matches()
    }

    private fun getPasswordValidationDetails(password: String): PasswordValidationResult {

        return PasswordValidationResult(
            meetsLength = password.length >= minPasswordLength,
            hasUppercase = uppercaseRegex.matcher(password).matches(),
            hasLowercase = lowercaseRegex.matcher(password).matches(),
            hasDigit = digitRegex.matcher(password).matches(),
            hasSpecial = specialCharRegex.matcher(password).matches()
        )
    }

    val isFormValid: Boolean
        get() {
            val state = _uiState.value
            val passwordResult = passwordValidationResult.value
            val isEmailValid = Patterns.EMAIL_ADDRESS.matcher(state.email.trim()).matches()
            val isFirstNameValid = isValidName(state.firstName)
            val isLastNameValid = isValidName(state.lastName)

            return isFirstNameValid &&
                    isLastNameValid &&
                    state.email.isNotBlank() && isEmailValid &&
                    passwordResult.isValid &&
                    state.confirmPassword.isNotBlank() &&
                    state.password == state.confirmPassword &&
                    state.termsAccepted
        }

    fun registerUser() {
        if (_uiState.value.isAnyLoading) return
        val state = _uiState.value
        val passwordResult = passwordValidationResult.value

        val validationError = validateRegistrationForm(state, passwordResult)
        if (validationError != null) {
            _uiState.update { it.copy(errorMessage = validationError) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(registrationInProgress = true, errorMessage = null) }
            val request = RegistrationRequest(
                firstName = state.firstName.trim(),
                lastName = state.lastName.trim(),
                email = state.email.trim(),
                password = state.password
            )

            when (val result = authRepository.registerUser(request)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            registrationInProgress = false,
                            registrationSuccess = false,
                            errorMessage = null
                        )
                    }
                    _registrationScreenEventFlow.tryEmit(RegistrationScreenEvent.ShowMessage("Registration successful! Please log in."))
                }
                is Resource.Error -> {
                    handleFailedLogin(result.message ?: "An unknown error occurred during registration.", "Email/Pass")
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun onGoogleRegisterClick() {
        if (_uiState.value.isAnyLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isGoogleRegistrationLoading = true, errorMessage = null) }
            _registrationScreenEventFlow.emit(RegistrationScreenEvent.LaunchGoogleSignIn)
        }
    }

    fun handleGoogleSignInResult(account: GoogleSignInAccount?) {
        val idToken = account?.idToken
        if (idToken != null) {
            Log.i(TAG, "[GoogleSignIn] Registration attempt successful. Verifying token...")
            if (!_uiState.value.isGoogleRegistrationLoading) {
                _uiState.update { it.copy(isGoogleRegistrationLoading = true, errorMessage = null) }
            }
            viewModelScope.launch {
                val tokenRequest = FirebaseTokenRequest(idToken = idToken)
                Log.d(TAG, "[GoogleSignIn] Sending Google ID token to backend for verification/registration...")
                when (val backendResult = authRepository.verifyGoogleToken(tokenRequest)) {
                    is Resource.Success -> handleSuccessfulLogin(backendResult.data?.token, "Google")
                    is Resource.Error -> handleFailedLogin(backendResult.message ?: "Google Sign-In failed: Server validation error.", "Google")
                    is Resource.Loading -> {}
                }
            }
        } else {
            Log.w(TAG, "[GoogleSignIn] Registration attempt failed or token missing.")
            if (_uiState.value.isGoogleRegistrationLoading) {
                _uiState.update { it.copy(isGoogleRegistrationLoading = false, errorMessage = "Google Sign-In failed or cancelled.") }
            }
        }
    }

    fun onGitHubRegisterClick() {
        if (_uiState.value.isAnyLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isGitHubRegistrationLoading = true, errorMessage = null) }
            val state = UUID.randomUUID().toString()
            with(prefs.edit()) {
                putString(KEY_GITHUB_AUTH_STATE, state)
                apply()
            }
            Log.d(TAG, "Generated and saved GitHub state for registration: $state")
            val authUri = Uri.parse(GITHUB_AUTH_URL).buildUpon()
                .appendQueryParameter("client_id", GITHUB_CLIENT_ID)
                .appendQueryParameter("redirect_uri", GITHUB_REDIRECT_URI)
                .appendQueryParameter("scope", GITHUB_SCOPE)
                .appendQueryParameter("state", state)
                .build()
            Log.d(TAG, "Constructed GitHub Auth URL for registration: $authUri")
            _registrationScreenEventFlow.emit(RegistrationScreenEvent.LaunchGitHubSignIn(authUri))
        }
    }

    fun handleGitHubLaunchFailed() {
        if (_uiState.value.isGitHubRegistrationLoading) {
            _uiState.update { it.copy(isGitHubRegistrationLoading = false, errorMessage = "Could not open browser for GitHub login.") }
        }
    }

    private fun validateRegistrationForm(state: RegistrationUiState, passwordResult: PasswordValidationResult): String? {
        val isEmailValid = Patterns.EMAIL_ADDRESS.matcher(state.email.trim()).matches()
        val passwordsMatch = state.password == state.confirmPassword
        val terms = state.termsAccepted
        val isFirstNameValid = isValidName(state.firstName)
        val isLastNameValid = isValidName(state.lastName)

        return when {
            state.firstName.isBlank() || state.lastName.isBlank() || state.email.isBlank() || state.password.isBlank() || state.confirmPassword.isBlank() ->
                "Please fill all required fields."
            !isFirstNameValid || !isLastNameValid ->
                "Name fields can only contain letters, spaces, hyphens, and apostrophes."
            !isEmailValid -> "Please enter a valid email address."
            !passwordResult.isValid ->
                "Password does not meet all requirements."

            !passwordsMatch -> "Passwords do not match."
            !terms -> "You must accept the terms and conditions."
            else -> null
        }
    }

    private fun handleSuccessfulLogin(apiJwt: String?, source: String) {
        if (apiJwt != null) {
            Log.i(TAG, "[$source] Backend verification/registration successful. API JWT received.")
            viewModelScope.launch {
                with(prefs.edit()) {
                    putString(KEY_AUTH_TOKEN, apiJwt)
                    putBoolean(SplashActivity.KEY_IS_LOGGED_IN, true)
                    apply()
                }
                _uiState.update {
                    it.copy(
                        registrationInProgress = false,
                        isGoogleRegistrationLoading = false,
                        isGitHubRegistrationLoading = false,
                        registrationSuccess = true,
                        errorMessage = null
                    )
                }
            }
        } else {
            Log.w(TAG, "[$source] Backend verification successful but API JWT was null.")
            handleFailedLogin("Login failed: Missing API token from server.", source)
        }
    }

    private fun handleFailedLogin(errorMessage: String, source: String) {
        Log.w(TAG, "[$source] Login/Registration failed: $errorMessage")
        _uiState.update {
            it.copy(
                registrationInProgress = false,
                isGoogleRegistrationLoading = false,
                isGitHubRegistrationLoading = false,
                registrationSuccess = false,
                errorMessage = errorMessage
            )
        }
    }

    fun registrationNavigated() {
        _uiState.update { it.copy(registrationSuccess = false) }
    }

    fun consumeErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}