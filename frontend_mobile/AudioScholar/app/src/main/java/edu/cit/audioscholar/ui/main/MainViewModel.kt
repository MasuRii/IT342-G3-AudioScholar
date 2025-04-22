package edu.cit.audioscholar.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import edu.cit.audioscholar.data.remote.dto.UserProfileDto
import edu.cit.audioscholar.domain.repository.AuthRepository
import edu.cit.audioscholar.util.Resource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository
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

    fun clearUserCacheOnLogout() {
        viewModelScope.launch {
            authRepository.clearLocalUserCache()
        }
    }
}

