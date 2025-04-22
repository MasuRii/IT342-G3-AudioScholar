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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UserProfileUiState(
    val isLoading: Boolean = false,
    val isDataAvailable: Boolean = false,
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

    private var loadProfileJob: Job? = null

    init {
        loadUserProfile()
    }

    fun loadUserProfile() {
        loadProfileJob?.cancel()

        loadProfileJob = viewModelScope.launch {
            Log.d("UserProfileViewModel", "Starting to collect user profile flow.")
            authRepository.getUserProfile()
                .onStart {
                    Log.d("UserProfileViewModel", "Flow started, setting initial loading state if no data yet.")
                    if (!_uiState.value.isDataAvailable) {
                        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                    } else {
                        _uiState.update { it.copy(errorMessage = null) }
                    }
                }
                .catch { e ->
                    Log.e("UserProfileViewModel", "Error collecting profile flow: ${e.message}", e)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "An unexpected error occurred: ${e.message}"
                        )
                    }
                }
                .collect { result ->
                    Log.d("UserProfileViewModel", "Received profile result: ${result::class.simpleName}")
                    when (result) {
                        is Resource.Success -> {
                            val profileData = result.data
                            if (profileData != null) {
                                Log.i("UserProfileViewModel", "Profile loaded/updated: Name=${profileData.displayName}, Email=${profileData.email}")
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        isDataAvailable = true,
                                        name = profileData.displayName ?: "",
                                        email = profileData.email ?: "",
                                        profileImageUrl = profileData.profileImageUrl,
                                        errorMessage = null
                                    )
                                }
                            } else {
                                Log.w("UserProfileViewModel", "Profile fetch Resource.Success but data was null")
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        errorMessage = it.errorMessage ?: "Failed to retrieve profile details."
                                    )
                                }
                            }
                        }
                        is Resource.Error -> {
                            Log.e("UserProfileViewModel", "Error loading profile: ${result.message}")
                            val currentData = _uiState.value
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    errorMessage = result.message ?: "An unexpected error occurred."
                                )
                            }
                            if (result.message?.contains("Unauthorized", ignoreCase = true) == true ||
                                result.message?.contains("401", ignoreCase = true) == true ||
                                result.message?.contains("403", ignoreCase = true) == true) {
                                Log.w("UserProfileViewModel", "Unauthorized error detected, clearing session and navigating to login.")
                                clearSessionAndNavigateToLogin()
                            }
                        }
                        is Resource.Loading -> {
                            Log.d("UserProfileViewModel", "Profile loading in progress (Resource.Loading)...")
                            val cachedData = result.data
                            if (cachedData != null && !_uiState.value.isDataAvailable) {
                                Log.d("UserProfileViewModel", "Displaying cached data while loading network.")
                                _uiState.update {
                                    it.copy(
                                        isLoading = true,
                                        isDataAvailable = true,
                                        name = cachedData.displayName ?: "",
                                        email = cachedData.email ?: "",
                                        profileImageUrl = cachedData.profileImageUrl,
                                        errorMessage = null
                                    )
                                }
                            } else if (!_uiState.value.isDataAvailable) {
                                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                            } else {
                                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                            }
                        }
                    }
                }
        }
    }

    fun logout() {
        viewModelScope.launch {
            Log.d("UserProfileViewModel", "Logout initiated by user")
            authRepository.clearLocalUserCache()
            clearSessionAndNavigateToLogin()
        }
    }

    private fun clearSessionAndNavigateToLogin() {
        viewModelScope.launch {
            Log.d("UserProfileViewModel", "Clearing local session data (Prefs).")
            with(prefs.edit()) {
                remove(LoginViewModel.KEY_AUTH_TOKEN)
                putBoolean(SplashActivity.KEY_IS_LOGGED_IN, false)
                apply()
            }
            Log.d("UserProfileViewModel", "Auth token and login status cleared. Triggering navigation.")
            _uiState.update { UserProfileUiState(navigateToLogin = true) }
        }
    }

    fun onLoginNavigationComplete() {
        _uiState.update { it.copy(navigateToLogin = false) }
    }

    fun consumeErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        loadProfileJob?.cancel()
    }
}