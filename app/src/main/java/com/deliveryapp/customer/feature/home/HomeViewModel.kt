package com.deliveryapp.customer.feature.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.deliveryapp.customer.AppContainer
import com.deliveryapp.customer.core.network.ApiResult
import com.deliveryapp.customer.core.network.isSessionExpired
import com.deliveryapp.customer.data.repository.ProfileRepository
import kotlinx.coroutines.launch

class HomeViewModel(private val profileRepository: ProfileRepository) : ViewModel() {
    var state by mutableStateOf(HomeUiState())
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            state = state.copy(isLoading = true, errorMessage = null, sessionExpired = false)
            state = when (val result = profileRepository.getProfile()) {
                is ApiResult.Success -> state.copy(
                    isLoading = false,
                    fullName = result.data.fullName,
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
                HomeViewModel(container.profileRepository) as T
        }
    }
}

data class HomeUiState(
    val isLoading: Boolean = false,
    val fullName: String = "Khách hàng",
    val errorMessage: String? = null,
    val sessionExpired: Boolean = false
)
