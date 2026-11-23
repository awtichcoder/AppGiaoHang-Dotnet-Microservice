package com.deliveryapp.customer.feature.rewards

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.deliveryapp.customer.AppContainer
import com.deliveryapp.customer.core.network.ApiResult
import com.deliveryapp.customer.core.network.isSessionExpired
import com.deliveryapp.customer.data.dto.LoyaltyAccountResponse
import com.deliveryapp.customer.data.repository.LoyaltyRepository
import kotlinx.coroutines.launch

class RewardsViewModel(private val loyaltyRepository: LoyaltyRepository) : ViewModel() {
    var state by mutableStateOf(RewardsUiState())
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            state = state.copy(isLoading = true, errorMessage = null, sessionExpired = false)
            state = when (val result = loyaltyRepository.getLoyalty()) {
                is ApiResult.Success -> state.copy(
                    isLoading = false,
                    loyalty = result.data,
                    errorMessage = null
                )
                is ApiResult.Error -> state.copy(
                    isLoading = false,
                    errorMessage = result.message,
                    sessionExpired = result.isSessionExpired()
                )
                ApiResult.Loading -> state.copy(isLoading = true)
            }
        }
    }

    fun consumeSessionExpired() {
        state = state.copy(sessionExpired = false)
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                RewardsViewModel(container.loyaltyRepository) as T
        }
    }
}

data class RewardsUiState(
    val isLoading: Boolean = false,
    val loyalty: LoyaltyAccountResponse? = null,
    val errorMessage: String? = null,
    val sessionExpired: Boolean = false
)
