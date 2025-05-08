package edu.cit.audioscholar.ui.subscription

import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import edu.cit.audioscholar.domain.repository.AuthRepository
import edu.cit.audioscholar.util.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PaymentUiState(
    val isLoading: Boolean = false,
    val userId: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class PaymentViewModel @Inject constructor(
    val authRepository: AuthRepository,
    private val prefs: SharedPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaymentUiState())
    val uiState: StateFlow<PaymentUiState> = _uiState.asStateFlow()
    
    private val TAG = "PaymentViewModel"

    init {
        loadUserData()
    }

    private fun loadUserData() {
        viewModelScope.launch {
            Log.d(TAG, "Starting to collect user profile flow.")
            _uiState.update { it.copy(isLoading = true) }
            
            authRepository.getUserProfile()
                .collect { result ->
                    when (result) {
                        is Resource.Success -> {
                            val profileData = result.data
                            if (profileData != null) {
                                Log.i(TAG, "Profile loaded/updated, userId: ${profileData.userId}")
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        userId = profileData.userId,
                                        errorMessage = null
                                    )
                                }
                            } else {
                                Log.w(TAG, "Profile fetch Resource.Success but data was null")
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        errorMessage = "Failed to retrieve user data."
                                    )
                                }
                            }
                        }
                        is Resource.Error -> {
                            Log.e(TAG, "Error loading profile: ${result.message}")
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    errorMessage = result.message ?: "An unexpected error occurred."
                                )
                            }
                        }
                        is Resource.Loading -> {
                        }
                    }
                }
        }
    }

    fun updateUserRoleToPremium(userId: String): Boolean {
        var success = false
        viewModelScope.launch {
            Log.d(TAG, "Updating user role to premium for userId: $userId")
            val result = authRepository.updateUserRole(userId, "ROLE_PREMIUM")
            success = result is Resource.Success
            if (success) {
                Log.i(TAG, "User role updated successfully to ROLE_PREMIUM")
            } else if (result is Resource.Error) {
                Log.e(TAG, "Failed to update user role: ${result.message}")
                _uiState.update { it.copy(errorMessage = result.message) }
            }
        }
        return success
    }
    
    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }
} 