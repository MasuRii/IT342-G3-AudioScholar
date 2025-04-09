package edu.cit.audioscholar.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RegistrationUiState(
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val termsAccepted: Boolean = false,
    val isPasswordVisible: Boolean = false,
    val isConfirmPasswordVisible: Boolean = false,
    val registrationInProgress: Boolean = false,
    val registrationError: String? = null,
    val registrationSuccess: Boolean = false
)

enum class PasswordStrength {
    NONE, WEAK, MEDIUM, STRONG
}

@HiltViewModel
class RegistrationViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(RegistrationUiState())
    val uiState: StateFlow<RegistrationUiState> = _uiState.asStateFlow()

    fun onFirstNameChange(value: String) {
        _uiState.update { it.copy(firstName = value, registrationError = null) }
    }

    fun onLastNameChange(value: String) {
        _uiState.update { it.copy(lastName = value, registrationError = null) }
    }

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, registrationError = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, registrationError = null) }
    }

    fun onConfirmPasswordChange(value: String) {
        _uiState.update { it.copy(confirmPassword = value, registrationError = null) }
    }

    fun onTermsAcceptedChange(accepted: Boolean) {
        _uiState.update { it.copy(termsAccepted = accepted, registrationError = null) }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun toggleConfirmPasswordVisibility() {
        _uiState.update { it.copy(isConfirmPasswordVisible = !it.isConfirmPasswordVisible) }
    }

    val isFormValid: Boolean
        get() = _uiState.value.firstName.isNotBlank() &&
                _uiState.value.lastName.isNotBlank() &&
                _uiState.value.email.isNotBlank() &&
                _uiState.value.password.isNotBlank() &&
                _uiState.value.confirmPassword.isNotBlank() &&
                _uiState.value.password == _uiState.value.confirmPassword &&
                _uiState.value.termsAccepted

    val passwordStrength: PasswordStrength
        get() {
            val password = _uiState.value.password
            return when {
                password.length == 0 -> PasswordStrength.NONE
                password.length < 8 -> PasswordStrength.WEAK
                password.any { it.isDigit() } && password.any { it.isLetter() } -> PasswordStrength.STRONG
                else -> PasswordStrength.MEDIUM
            }
        }

    fun registerUser() {
        if (!isFormValid) {
            _uiState.update { it.copy(registrationError = "Please fill all fields correctly and accept terms.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(registrationInProgress = true, registrationError = null) }
            kotlinx.coroutines.delay(1500)
            _uiState.update { it.copy(registrationInProgress = false, registrationSuccess = true) }
        }
    }

    fun registrationNavigated() {
        _uiState.update { it.copy(registrationSuccess = false) }
    }
}