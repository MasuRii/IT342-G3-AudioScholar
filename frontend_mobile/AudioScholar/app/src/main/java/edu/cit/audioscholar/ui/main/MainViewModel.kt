package edu.cit.audioscholar.ui.main

import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import edu.cit.audioscholar.data.remote.dto.UserProfileDto
import edu.cit.audioscholar.domain.repository.AuthRepository
import edu.cit.audioscholar.ui.auth.LoginViewModel
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
    private val prefs: SharedPreferences
) : ViewModel() {

    val userProfileState: StateFlow<Resource<UserProfileDto?>> = authRepository.getUserProfile()
        .map { resource ->
            when (resource) {
                is Resource.Success -> Resource.Success<UserProfileDto?>(resource.data)
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
            Log.d("MainViewModel", "Performing logout: Calling API and clearing cache/prefs.")
            val result = authRepository.logout()
            when (result) {
                is Resource.Success -> Log.i("MainViewModel", "API logout successful.")
                is Resource.Error -> Log.w("MainViewModel", "API logout failed: ${result.message}. Proceeding with local logout.")
                is Resource.Loading -> {}
            }

            authRepository.clearLocalUserCache()
            Log.d("MainViewModel", "Local user cache cleared.")

            Log.d("MainViewModel", "Clearing SharedPreferences.")
            with(prefs.edit()) {
                putBoolean(SplashActivity.KEY_IS_LOGGED_IN, false)
                remove(LoginViewModel.KEY_AUTH_TOKEN)
                apply()
            }
            Log.d("MainViewModel", "SharedPreferences cleared.")

            Log.d("MainViewModel", "Sending logout complete event.")
            _logoutCompleteEventChannel.send(Unit)
        }
    }

    fun clearUserCacheOnLogout() {
        viewModelScope.launch {
            authRepository.clearLocalUserCache()
        }
    }
}