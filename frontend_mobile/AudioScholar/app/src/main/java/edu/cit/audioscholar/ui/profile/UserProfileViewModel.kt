package edu.cit.audioscholar.ui.profile

import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import edu.cit.audioscholar.domain.repository.AuthRepository
import edu.cit.audioscholar.ui.auth.LoginViewModel
import edu.cit.audioscholar.ui.main.SplashActivity
import edu.cit.audioscholar.util.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UserProfileUiState(
    val isLoading: Boolean = false,
    val name: String = "",
    val email: String = "",
    val profileImageUrl: String? = null,
    val errorMessage: String? = null,
    val navigateToLogin: Boolean = false
)

@HiltViewModel
class UserProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val prefs: SharedPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserProfileUiState())
    val uiState: StateFlow<UserProfileUiState> = _uiState.asStateFlow()

    init {
        loadUserProfile()
    }

    fun loadUserProfile() {
        if (_uiState.value.isLoading) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            Log.d("UserProfileViewModel", "Calling repository.getUserProfile()")
            when (val result = authRepository.getUserProfile()) {
                is Resource.Success -> {
                    val profileData = result.data
                    if (profileData != null) {
                        Log.i("UserProfileViewModel", "Profile loaded successfully: Name=${profileData.displayName}, Email=${profileData.email}")
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                name = profileData.displayName ?: "N/A",
                                email = profileData.email ?: "N/A",
                                profileImageUrl = profileData.profileImageUrl
                            )
                        }
                    } else {
                        Log.w("UserProfileViewModel", "Profile fetch succeeded but data was null")
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "Failed to retrieve profile data."
                            )
                        }
                    }
                }
                is Resource.Error -> {
                    Log.e("UserProfileViewModel", "Error loading profile: ${result.message}")
                    if (result.message?.contains("Unauthorized", ignoreCase = true) == true ||
                        result.message?.contains("401", ignoreCase = true) == true ||
                        result.message?.contains("403", ignoreCase = true) == true) {
                        Log.w("UserProfileViewModel", "Unauthorized error detected, clearing session and navigating to login.")
                        clearSessionAndNavigateToLogin()
                    } else {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = result.message ?: "An unexpected error occurred while loading the profile."
                            )
                        }
                    }
                }
                is Resource.Loading -> {
                    Log.d("UserProfileViewModel", "Profile loading in progress...")
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            Log.d("UserProfileViewModel", "Logout initiated by user")
            clearSessionAndNavigateToLogin()
        }
    }

    private fun clearSessionAndNavigateToLogin() {
        viewModelScope.launch {
            Log.d("UserProfileViewModel", "Clearing local session data.")
            with(prefs.edit()) {
                remove(LoginViewModel.KEY_AUTH_TOKEN)
                putBoolean(SplashActivity.KEY_IS_LOGGED_IN, false)
                apply()
            }
            Log.d("UserProfileViewModel", "Auth token and login status cleared. Triggering navigation.")
            _uiState.update { it.copy(isLoading = false, navigateToLogin = true) }
        }
    }

    fun onLoginNavigationComplete() {
        _uiState.update { it.copy(navigateToLogin = false) }
    }

    fun consumeErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}