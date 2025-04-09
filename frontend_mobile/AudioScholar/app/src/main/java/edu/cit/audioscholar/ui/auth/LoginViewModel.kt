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
        if (uiState.email.isBlank() || uiState.password.isBlank()) {
            uiState = uiState.copy(errorMessage = "Email and password cannot be empty.")
            return
        }

        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, errorMessage = null)
            delay(1500)

            if (uiState.email.contains("@") && uiState.password.length >= 6) {
                with(prefs.edit()) {
                    putBoolean(SplashActivity.KEY_IS_LOGGED_IN, true)
                    apply()
                }
                uiState = uiState.copy(isLoading = false)
                _eventFlow.emit(LoginEvent.LoginSuccess)
            } else {
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = "Invalid email or password. (Mock)"
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