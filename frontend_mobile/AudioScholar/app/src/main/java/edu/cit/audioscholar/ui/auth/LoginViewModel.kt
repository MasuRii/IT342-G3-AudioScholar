package edu.cit.audioscholar.ui.auth

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import edu.cit.audioscholar.ui.main.SplashActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.util.Patterns

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
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val prefs: SharedPreferences
) : ViewModel() {

    var uiState by mutableStateOf(LoginUiState())
        private set

    private val _eventFlow = MutableSharedFlow<LoginEvent>()
    val eventFlow = _eventFlow.asSharedFlow()

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

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            uiState = uiState.copy(errorMessage = "Please enter a valid email address.")
            return
        }

        if (password.length < 8) {
            uiState = uiState.copy(errorMessage = "Password must be at least 8 characters long.")
            return
        }


        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, errorMessage = null)
            delay(1500)

            val isValidFormat = Patterns.EMAIL_ADDRESS.matcher(email).matches()
            val isValidLength = password.length >= 8

            if (isValidFormat && isValidLength) {
                with(prefs.edit()) {
                    putBoolean(SplashActivity.KEY_IS_LOGGED_IN, true)
                    apply()
                }
                uiState = uiState.copy(isLoading = false)
                _eventFlow.emit(LoginEvent.LoginSuccess)
            } else {
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = "Incorrect email or password. Please try again."
                )
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
            _eventFlow.emit(LoginEvent.ShowInfoMessage("Google Sign-In not implemented yet."))
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
        uiState = uiState.copy(infoMessage = null)
    }
}