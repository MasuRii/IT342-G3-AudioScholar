package edu.cit.audioscholar.ui.main

import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import edu.cit.audioscholar.data.remote.dto.UserProfileDto
import edu.cit.audioscholar.domain.repository.AuthRepository
import edu.cit.audioscholar.ui.auth.LoginViewModel
import edu.cit.audioscholar.util.PremiumStatusManager
import edu.cit.audioscholar.util.Resource
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val prefs: SharedPreferences,
    private val premiumStatusManager: PremiumStatusManager
) : ViewModel() {

    companion object {
        private const val TAG = "MainViewModel"
    }

    var isPremiumUser by mutableStateOf(premiumStatusManager.isPremiumUser())
        private set

    init {
        Log.d(TAG, "Initial premium status: $isPremiumUser")
    }

    val userProfileState: StateFlow<Resource<UserProfileDto?>> = authRepository.getUserProfile()
        .map { resource ->
            when (resource) {
                is Resource.Success -> {
                    Log.d(TAG, "Profile success with roles: ${resource.data?.roles}")
                    premiumStatusManager.updatePremiumStatus(resource.data)
                    val newPremiumStatus = premiumStatusManager.isPremiumUser()
                    Log.d(TAG, "Premium status after profile update: $newPremiumStatus (changed from $isPremiumUser)")
                    isPremiumUser = newPremiumStatus
                    Resource.Success<UserProfileDto?>(resource.data)
                }
                is Resource.Error -> Resource.Error<UserProfileDto?>(resource.message ?: "Unknown error", resource.data)
                is Resource.Loading -> Resource.Loading<UserProfileDto?>(resource.data)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = Resource.Loading<UserProfileDto?>(null)
        )

    private val _logoutCompleteEventChannel = Channel<Unit>(Channel.BUFFERED)
    val logoutCompleteEventFlow: Flow<Unit> = _logoutCompleteEventChannel.receiveAsFlow()

    fun performLogout() {
        viewModelScope.launch {
            Log.d(TAG, "Logout initiated by user. Calling API.")
            
            val result = authRepository.logout()
            when (result) {
                is Resource.Success -> {
                    Log.i(TAG, "API logout successful.")
                }
                is Resource.Error -> {
                    Log.w(TAG, "API logout failed: ${result.message}. Proceeding with local logout.")
                }
                is Resource.Loading -> {}
            }
            
            Log.d(TAG, "Proceeding to clear local session data.")
            authRepository.clearLocalUserCache()
            
            premiumStatusManager.clearPremiumStatus()
            isPremiumUser = false
            
            with(prefs.edit()) {
                remove(LoginViewModel.KEY_AUTH_TOKEN)
                putBoolean(SplashActivity.KEY_IS_LOGGED_IN, false)
                apply()
            }
            
            Log.d(TAG, "Auth token and login status cleared. Emitting logout event.")
            _logoutCompleteEventChannel.send(Unit)
        }
    }

    fun clearUserCacheOnLogout() {
        viewModelScope.launch {
            authRepository.clearLocalUserCache()
        }
    }
}