package edu.cit.audioscholar.ui.profile

import android.util.Log
import androidx.lifecycle.SavedStateHandle
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

data class EditProfileUiState(
    val firstName: String = "",
    val lastName: String = "",
    val username: String = "",
    val email: String = "",
    val isLoading: Boolean = false,
    val firstNameError: String? = null,
    val lastNameError: String? = null,
    val usernameError: String? = null,
    val saveSuccess: Boolean = false,
    val generalMessage: String? = null
)

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditProfileUiState())
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                delay(300)
                _uiState.update {
                    it.copy(
                        firstName = "Math (Placeholder)",
                        lastName = "Lee (Placeholder)",
                        username = "mathlee_pl",
                        email = "mathlee.biacolo@cit.edu",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Log.e("EditProfileViewModel", "Failed to load initial data", e)
                _uiState.update { it.copy(isLoading = false, generalMessage = "Failed to load profile data.") }
            }
        }
    }

    fun onFirstNameChange(newName: String) {
        _uiState.update {
            it.copy(
                firstName = newName,
                firstNameError = if (it.firstNameError != null && newName.isNotBlank()) null else it.firstNameError
            )
        }
    }

    fun onLastNameChange(newName: String) {
        _uiState.update {
            it.copy(
                lastName = newName,
                lastNameError = if (it.lastNameError != null && newName.isNotBlank()) null else it.lastNameError
            )
        }
    }

    fun onUsernameChange(newName: String) {
        _uiState.update {
            it.copy(
                username = newName,
                usernameError = if (it.usernameError != null && newName.isNotBlank()) null else it.usernameError
            )
        }
    }

    private fun validateFields(): Boolean {
        val firstName = _uiState.value.firstName.trim()
        val lastName = _uiState.value.lastName.trim()
        val username = _uiState.value.username.trim()

        val fnError = if (firstName.isBlank()) "First name is required" else null
        val lnError = if (lastName.isBlank()) "Last name is required" else null
        val unError = if (username.isBlank()) "Username is required" else null

        _uiState.update {
            it.copy(
                firstNameError = fnError,
                lastNameError = lnError,
                usernameError = unError
            )
        }

        return fnError == null && lnError == null && unError == null
    }

    fun saveProfile() {
        if (!validateFields()) {
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, generalMessage = null) }
            try {
                delay(1000)
                Log.d("EditProfileViewModel", "Simulated profile update successful.")
                _uiState.update { it.copy(isLoading = false, saveSuccess = true) }

            } catch (e: Exception) {
                Log.e("EditProfileViewModel", "Failed to save profile", e)
                _uiState.update { it.copy(isLoading = false, generalMessage = "Failed to save profile. Please try again.") }
            }
        }
    }

    fun consumeGeneralMessage() {
        _uiState.update { it.copy(generalMessage = null) }
    }

    fun resetSaveSuccessFlag() {
        _uiState.update { it.copy(saveSuccess = false) }
    }
}