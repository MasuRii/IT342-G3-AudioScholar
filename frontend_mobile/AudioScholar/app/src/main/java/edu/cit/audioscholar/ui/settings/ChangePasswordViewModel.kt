package edu.cit.audioscholar.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChangePasswordUiState(
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val currentPasswordVisible: Boolean = false,
    val newPasswordVisible: Boolean = false,
    val confirmPasswordVisible: Boolean = false,
    val currentPasswordError: String? = null,
    val newPasswordErrors: List<String> = emptyList(),
    val confirmPasswordError: String? = null,
    val passwordStrength: PasswordStrength = PasswordStrength.NONE,
    val isLoading: Boolean = false,
    val changeSuccess: Boolean = false,
    val generalMessage: String? = null
)

@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChangePasswordUiState())
    val uiState: StateFlow<ChangePasswordUiState> = _uiState.asStateFlow()

    fun onCurrentPasswordChange(password: String) {
        _uiState.update {
            it.copy(
                currentPassword = password,
                currentPasswordError = if (it.currentPasswordError != null && password.isNotBlank()) null else it.currentPasswordError
            )
        }
    }

    fun onNewPasswordChange(password: String) {
        val errors = validateNewPasswordCriteria(password)
        val strength = calculateStrength(password)
        val confirmError = if (password != _uiState.value.confirmPassword && _uiState.value.confirmPassword.isNotEmpty()) {
            "Passwords do not match"
        } else {
            null
        }
        _uiState.update {
            it.copy(
                newPassword = password,
                newPasswordErrors = errors,
                passwordStrength = strength,
                confirmPasswordError = confirmError ?: it.confirmPasswordError?.takeIf { msg -> msg == "Passwords do not match" }
            )
        }
    }

    fun onConfirmPasswordChange(password: String) {
        val error = if (_uiState.value.newPassword != password && password.isNotEmpty()) {
            "Passwords do not match"
        } else {
            null
        }
        _uiState.update {
            it.copy(
                confirmPassword = password,
                confirmPasswordError = error
            )
        }
    }

    fun toggleCurrentPasswordVisibility() {
        _uiState.update { it.copy(currentPasswordVisible = !it.currentPasswordVisible) }
    }

    fun toggleNewPasswordVisibility() {
        _uiState.update { it.copy(newPasswordVisible = !it.newPasswordVisible) }
    }

    fun toggleConfirmPasswordVisibility() {
        _uiState.update { it.copy(confirmPasswordVisible = !it.confirmPasswordVisible) }
    }

    private fun validateNewPasswordCriteria(password: String): List<String> {
        val errors = mutableListOf<String>()
        if (password.length < 8) errors.add("Minimum 8 characters")
        if (!password.any { it.isUpperCase() }) errors.add("At least 1 uppercase letter")
        if (!password.any { it.isLowerCase() }) errors.add("At least 1 lowercase letter")
        if (!password.any { it.isDigit() }) errors.add("At least 1 number")
        if (!password.any { """!"#$%&'()*+,-./:;<=>?@[\]^_`{|}~""".contains(it) }) errors.add("At least one special character")
        return errors
    }

    private fun calculateStrength(password: String): PasswordStrength {
        var score = 0
        if (password.length >= 8) score++
        if (password.any { it.isUpperCase() }) score++
        if (password.any { it.isLowerCase() }) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++

        return when {
            score >= 5 -> PasswordStrength.STRONG
            score >= 3 -> PasswordStrength.MEDIUM
            score >= 1 -> PasswordStrength.WEAK
            else -> PasswordStrength.NONE
        }
    }

    private fun runFinalValidation(): Boolean {
        val currentPassword = _uiState.value.currentPassword
        val newPassword = _uiState.value.newPassword
        val confirmPassword = _uiState.value.confirmPassword

        val currentError = if (currentPassword.isBlank()) "Current password is required" else null
        val newErrors = validateNewPasswordCriteria(newPassword)
        val confirmError = if (newPassword != confirmPassword) "Passwords do not match" else null

        _uiState.update {
            it.copy(
                currentPasswordError = currentError,
                newPasswordErrors = newErrors,
                confirmPasswordError = confirmError
            )
        }

        return currentError == null && newErrors.isEmpty() && confirmError == null
    }

    fun changePassword() {
        if (!runFinalValidation()) {
            _uiState.update { it.copy(generalMessage = "Please correct the errors above.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, generalMessage = null) }
            try {
                delay(1500)
                Log.d("ChangePasswordVM", "Simulated password change successful.")
                _uiState.update { it.copy(isLoading = false, changeSuccess = true) }

            } catch (e: Exception) {
                Log.e("ChangePasswordVM", "Password change failed", e)
                _uiState.update { it.copy(isLoading = false, generalMessage = "Failed to change password. Please try again.") }
            }
        }
    }

    fun consumeGeneralMessage() {
        _uiState.update { it.copy(generalMessage = null) }
    }

    fun resetChangeSuccessFlag() {
        _uiState.update { it.copy(changeSuccess = false) }
    }
}