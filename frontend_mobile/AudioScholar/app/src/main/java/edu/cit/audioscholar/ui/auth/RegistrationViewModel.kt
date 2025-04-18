package edu.cit.audioscholar.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import edu.cit.audioscholar.data.remote.dto.RegistrationRequest
import edu.cit.audioscholar.domain.repository.AudioRepository
import edu.cit.audioscholar.util.Resource
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
class RegistrationViewModel @Inject constructor(
    private val audioRepository: AudioRepository
) : ViewModel() {

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
        get() {
            val state = _uiState.value
            val isEmailValid = android.util.Patterns.EMAIL_ADDRESS.matcher(state.email).matches()
            val isPasswordStrongEnough = passwordStrength != PasswordStrength.WEAK && passwordStrength != PasswordStrength.NONE

            return state.firstName.isNotBlank() &&
                    state.lastName.isNotBlank() &&
                    state.email.isNotBlank() && isEmailValid &&
                    state.password.isNotBlank() && isPasswordStrongEnough &&
                    state.confirmPassword.isNotBlank() &&
                    state.password == state.confirmPassword &&
                    state.termsAccepted
        }

    val passwordStrength: PasswordStrength
        get() {
            val password = _uiState.value.password
            val hasLetter = password.any { it.isLetter() }
            val hasDigit = password.any { it.isDigit() }
            val hasSpecial = password.any { !it.isLetterOrDigit() }

            return when {
                password.length == 0 -> PasswordStrength.NONE
                password.length < 8 -> PasswordStrength.WEAK
                password.length >= 12 && hasLetter && hasDigit && hasSpecial -> PasswordStrength.STRONG
                password.length >= 8 && hasLetter && hasDigit -> PasswordStrength.MEDIUM
                else -> PasswordStrength.WEAK
            }
        }

    fun registerUser() {
        val state = _uiState.value
        val isEmailValid = android.util.Patterns.EMAIL_ADDRESS.matcher(state.email).matches()
        val passwordsMatch = state.password == state.confirmPassword
        val strength = passwordStrength
        val terms = state.termsAccepted

        var errorMessage = ""
        if (state.firstName.isBlank() || state.lastName.isBlank() || state.email.isBlank() || state.password.isBlank() || state.confirmPassword.isBlank()) {
            errorMessage = "Please fill all required fields."
        } else if (!isEmailValid) {
            errorMessage = "Please enter a valid email address."
        } else if (strength == PasswordStrength.WEAK || strength == PasswordStrength.NONE) {
            errorMessage = "Password is too weak. Please use at least 8 characters including letters and numbers."
        } else if (!passwordsMatch) {
            errorMessage = "Passwords do not match."
        } else if (!terms) {
            errorMessage = "You must accept the terms and conditions."
        }

        if (errorMessage.isNotEmpty()) {
            _uiState.update { it.copy(registrationError = errorMessage) }
            return
        }


        viewModelScope.launch {
            _uiState.update { it.copy(registrationInProgress = true, registrationError = null) }

            val currentState = _uiState.value
            val request = RegistrationRequest(
                firstName = currentState.firstName.trim(),
                lastName = currentState.lastName.trim(),
                email = currentState.email.trim(),
                password = currentState.password
            )

            when (val result = audioRepository.registerUser(request)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            registrationInProgress = false,
                            registrationSuccess = true,
                            registrationError = null
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            registrationInProgress = false,
                            registrationSuccess = false,
                            registrationError = result.message ?: "An unknown error occurred during registration."
                        )
                    }
                }
                is Resource.Loading -> {
                }
            }
        }
    }

    fun registrationNavigated() {
        _uiState.update { it.copy(registrationSuccess = false) }
    }
}