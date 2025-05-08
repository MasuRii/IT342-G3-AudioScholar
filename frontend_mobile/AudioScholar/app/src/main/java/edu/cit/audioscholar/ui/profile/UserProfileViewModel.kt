package edu.cit.audioscholar.ui.profile

import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import edu.cit.audioscholar.domain.repository.AuthRepository
import edu.cit.audioscholar.ui.auth.LoginViewModel
import edu.cit.audioscholar.ui.main.SplashActivity
import edu.cit.audioscholar.util.PremiumStatusManager
import edu.cit.audioscholar.util.Resource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
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
    val isPremium: Boolean = false,
    val errorMessage: String? = null,
    val navigateToLogin: Boolean = false
)

@HiltViewModel
class UserProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val prefs: SharedPreferences,
    private val premiumStatusManager: PremiumStatusManager
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
                                
                                premiumStatusManager.updatePremiumStatus(profileData)
                                val isPremium = premiumStatusManager.isPremiumUser()
                                
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        isDataAvailable = true,
                                        name = profileData.displayName ?: "",
                                        email = profileData.email ?: "",
                                        profileImageUrl = profileData.profileImageUrl,
                                        isPremium = isPremium,
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
                            Log.e("UserProfileViewModel", "Error loading profile data: ${result.message}")
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    errorMessage = result.message ?: "Failed to retrieve profile details."
                                )
                            }
                        }
                        is Resource.Loading -> {
                            Log.d("UserProfileViewModel", "Profile loading in progress (Resource.Loading)...")
                            val cachedData = result.data
                            if (cachedData != null && !_uiState.value.isDataAvailable) {
                                Log.d("UserProfileViewModel", "Displaying cached data while loading network.")
                                
                                val isPremium = if (cachedData.roles?.contains("ROLE_PREMIUM") == true) {
                                    true
                                } else {
                                    premiumStatusManager.isPremiumUser()
                                }
                                
                                _uiState.update {
                                    it.copy(
                                        isLoading = true,
                                        isDataAvailable = true,
                                        name = cachedData.displayName ?: "",
                                        email = cachedData.email ?: "",
                                        profileImageUrl = cachedData.profileImageUrl,
                                        isPremium = isPremium,
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
            Log.d("UserProfileViewModel", "Logout initiated by user. Calling API.")

            val result = authRepository.logout()

            when (result) {
                is Resource.Success -> {
                    Log.i("UserProfileViewModel", "API logout successful.")
                }
                is Resource.Error -> {
                    Log.w("UserProfileViewModel", "API logout failed: ${result.message}. Proceeding with local logout.")
                }
                is Resource.Loading -> {
                }
            }

            Log.d("UserProfileViewModel", "Proceeding to clear local session data.")
            clearSessionAndNavigateToLogin()
        }
    }

    private fun clearSessionAndNavigateToLogin() {
        viewModelScope.launch {
            Log.d("UserProfileViewModel", "Clearing local user cache (DataStore).")
            authRepository.clearLocalUserCache()

            premiumStatusManager.clearPremiumStatus()

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