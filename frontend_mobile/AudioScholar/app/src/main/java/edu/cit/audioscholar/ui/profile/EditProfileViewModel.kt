package edu.cit.audioscholar.ui.profile

import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import edu.cit.audioscholar.data.remote.dto.UpdateUserProfileRequest
import edu.cit.audioscholar.data.remote.dto.UserProfileDto
import edu.cit.audioscholar.domain.repository.AuthRepository
import edu.cit.audioscholar.util.Resource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditProfileUiState(
    val firstName: String = "",
    val lastName: String = "",
    val displayName: String = "",
    val email: String = "",
    val profileImageUrl: String? = null,
    val selectedAvatarUri: Uri? = null,

    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isUploadingAvatar: Boolean = false,

    val firstNameError: String? = null,
    val lastNameError: String? = null,
    val displayNameError: String? = null,

    val saveSuccess: Boolean = false,
    val generalMessage: String? = null
)

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditProfileUiState())
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

    private var avatarUploadJob: Job? = null

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = authRepository.getUserProfile()) {
                is Resource.Success -> {
                    val profile: UserProfileDto? = result.data
                    _uiState.update {
                        it.copy(
                            firstName = profile?.firstName ?: "",
                            lastName = profile?.lastName ?: "",
                            displayName = profile?.displayName ?: "",
                            email = profile?.email ?: "",
                            profileImageUrl = profile?.profileImageUrl,
                            selectedAvatarUri = null,
                            isLoading = false
                        )
                    }
                }
                is Resource.Error -> {
                    Log.e("EditProfileViewModel", "Failed to load profile data: ${result.message}")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            generalMessage = result.message ?: "Failed to load profile data."
                        )
                    }
                }
                is Resource.Loading -> {}
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
    fun onDisplayNameChange(newName: String) {
        _uiState.update {
            it.copy(
                displayName = newName,
                displayNameError = if (it.displayNameError != null && newName.isNotBlank()) null else it.displayNameError
            )
        }
    }

    fun onAvatarUriSelected(uri: Uri?) {
        _uiState.update { it.copy(selectedAvatarUri = uri) }
        if (uri != null) {
            uploadSelectedAvatar(uri)
        }
    }

    fun onProfileImageUrlChange(newUrl: String?) {
        _uiState.update { it.copy(profileImageUrl = newUrl, selectedAvatarUri = null) }
    }

    private fun validateFields(): Boolean {
        val firstName = _uiState.value.firstName.trim()
        val lastName = _uiState.value.lastName.trim()
        val displayName = _uiState.value.displayName.trim()

        val fnError = if (firstName.isBlank()) "First name is required" else null
        val lnError = if (lastName.isBlank()) "Last name is required" else null
        val dnError = if (displayName.isBlank()) "Display name is required" else null

        _uiState.update {
            it.copy(
                firstNameError = fnError,
                lastNameError = lnError,
                displayNameError = dnError
            )
        }

        return fnError == null && lnError == null && dnError == null
    }

    private fun uploadSelectedAvatar(uri: Uri) {
        avatarUploadJob?.cancel()

        avatarUploadJob = viewModelScope.launch {
            _uiState.update { it.copy(isUploadingAvatar = true, generalMessage = null) }
            Log.d("EditProfileViewModel", "Starting avatar upload for URI: $uri")

            try {
                when (val result = authRepository.uploadAvatar(uri)) {
                    is Resource.Success -> {
                        val updatedProfile = result.data
                        Log.i("EditProfileViewModel", "Avatar upload successful. New URL: ${updatedProfile?.profileImageUrl}")
                        _uiState.update {
                            it.copy(
                                profileImageUrl = updatedProfile?.profileImageUrl,
                                selectedAvatarUri = null
                            )
                        }
                    }
                    is Resource.Error -> {
                        Log.e("EditProfileViewModel", "Avatar upload failed: ${result.message}")
                        _uiState.update {
                            it.copy(
                                generalMessage = result.message ?: "Failed to upload avatar.",
                                selectedAvatarUri = null
                            )
                        }
                    }
                    is Resource.Loading -> {}
                }
            } catch (e: Exception) {
                Log.e("EditProfileViewModel", "Unexpected error during avatar upload coroutine: ${e.message}", e)
                _uiState.update { it.copy(generalMessage = "An unexpected error occurred during upload.") }
            } finally {
                _uiState.update { it.copy(isUploadingAvatar = false) }
            }
        }
    }


    fun saveProfile() {
        if (!validateFields()) {
            _uiState.update { it.copy(generalMessage = "Please correct the errors above.") }
            return
        }
        if (_uiState.value.isUploadingAvatar) {
            _uiState.update { it.copy(generalMessage = "Please wait for avatar upload to complete.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, generalMessage = null) }

            val currentState = _uiState.value
            val request = UpdateUserProfileRequest(
                firstName = currentState.firstName.trim().takeIf { it.isNotBlank() },
                lastName = currentState.lastName.trim().takeIf { it.isNotBlank() },
                displayName = currentState.displayName.trim().takeIf { it.isNotBlank() },
                profileImageUrl = currentState.profileImageUrl?.trim()?.takeIf { it.isNotBlank() }
            )

            Log.d("EditProfileViewModel", "Sending text profile update request: $request")

            when (val result = authRepository.updateUserProfile(request)) {
                is Resource.Success -> {
                    Log.d("EditProfileViewModel", "Text profile update successful.")
                    val updatedProfile: UserProfileDto? = result.data
                    _uiState.update {
                        it.copy(
                            firstName = updatedProfile?.firstName ?: it.firstName,
                            lastName = updatedProfile?.lastName ?: it.lastName,
                            displayName = updatedProfile?.displayName ?: it.displayName,
                            profileImageUrl = updatedProfile?.profileImageUrl ?: it.profileImageUrl,
                            isSaving = false,
                            saveSuccess = true
                        )
                    }
                }
                is Resource.Error -> {
                    Log.e("EditProfileViewModel", "Failed to save text profile: ${result.message}")
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            generalMessage = result.message ?: "Failed to save profile. Please try again."
                        )
                    }
                }
                is Resource.Loading -> {}
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