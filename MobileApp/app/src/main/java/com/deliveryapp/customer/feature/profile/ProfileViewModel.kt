package com.deliveryapp.customer.feature.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.deliveryapp.customer.AppContainer
import com.deliveryapp.customer.core.network.ApiResult
import com.deliveryapp.customer.core.network.isSessionExpired
import com.deliveryapp.customer.data.dto.UserResponse
import com.deliveryapp.customer.data.repository.AuthGateway
import com.deliveryapp.customer.data.repository.LoyaltyRepository
import com.deliveryapp.customer.data.repository.ProfileRepository
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val loyaltyRepository: LoyaltyRepository,
    private val authGateway: AuthGateway
) : ViewModel() {
    var state by mutableStateOf(ProfileUiState())
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            state = state.copy(isLoading = true, errorMessage = null, sessionExpired = false)
            val profileResult = profileRepository.getProfile()
            if (profileResult is ApiResult.Error) {
                state = state.copy(
                    isLoading = false,
                    errorMessage = profileResult.message,
                    sessionExpired = profileResult.isSessionExpired()
                )
                return@launch
            }

            val loyaltyResult = loyaltyRepository.getLoyalty()
            state = when {
                profileResult is ApiResult.Success && loyaltyResult is ApiResult.Success -> state.copy(
                    isLoading = false,
                    user = profileResult.data,
                    tier = loyaltyResult.data.tier,
                    points = loyaltyResult.data.points,
                    errorMessage = null
                )
                profileResult is ApiResult.Success && loyaltyResult is ApiResult.Error -> state.copy(
                    isLoading = false,
                    user = profileResult.data,
                    errorMessage = loyaltyResult.message,
                    sessionExpired = loyaltyResult.isSessionExpired()
                )
                else -> state.copy(isLoading = false)
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            state = state.copy(isLoggingOut = true)
            authGateway.logout()
            state = state.copy(isLoggingOut = false, logoutCompleted = true)
        }
    }

    fun consumeLogoutCompleted() {
        state = state.copy(logoutCompleted = false)
    }

    fun consumeSessionExpired() {
        state = state.copy(sessionExpired = false)
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ProfileViewModel(container.profileRepository, container.loyaltyRepository, container.authRepository) as T
        }
    }
}

data class ProfileUiState(
    val isLoading: Boolean = false,
    val isLoggingOut: Boolean = false,
    val user: UserResponse? = null,
    val tier: String = "Member",
    val points: Int = 0,
    val errorMessage: String? = null,
    val sessionExpired: Boolean = false,
    val logoutCompleted: Boolean = false
)
