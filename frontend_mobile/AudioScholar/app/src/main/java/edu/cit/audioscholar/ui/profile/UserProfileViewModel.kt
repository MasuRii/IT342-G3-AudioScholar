package edu.cit.audioscholar.ui.profile

import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import edu.cit.audioscholar.domain.repository.AudioRepository
import edu.cit.audioscholar.ui.auth.LoginViewModel
import edu.cit.audioscholar.ui.main.SplashActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UserProfileUiState(
    val isLoading: Boolean = false,
    val name: String = "Loading...",
    val email: String = "Loading...",
    val errorMessage: String? = null,
    val navigateToLogin: Boolean = false
)

@HiltViewModel
class UserProfileViewModel @Inject constructor(
    private val audioRepository: AudioRepository,
    private val prefs: SharedPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserProfileUiState())
    val uiState: StateFlow<UserProfileUiState> = _uiState.asStateFlow()

    init {
        loadUserProfile()
    }

    fun loadUserProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {

                kotlinx.coroutines.delay(500)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        name = "Math Lee (Placeholder)",
                        email = "mathlee.biacolo@cit.edu (Placeholder)"
                    )
                }

            } catch (e: Exception) {
                Log.e("UserProfileViewModel", "Error loading profile", e)
                _uiState.update { it.copy(isLoading = false, errorMessage = "An unexpected error occurred.") }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            Log.d("UserProfileViewModel", "Logout initiated")
            with(prefs.edit()) {
                remove(LoginViewModel.KEY_AUTH_TOKEN)
                putBoolean(SplashActivity.KEY_IS_LOGGED_IN, false)
                apply()
            }
            Log.d("UserProfileViewModel", "Auth token and login status cleared")
            _uiState.update { it.copy(navigateToLogin = true) }
        }
    }

    fun onLoginNavigationComplete() {
        _uiState.update { it.copy(navigateToLogin = false) }
    }

    fun consumeErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}