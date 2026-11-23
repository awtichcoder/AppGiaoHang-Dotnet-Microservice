package com.deliveryapp.customer.feature.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.deliveryapp.customer.AppContainer
import com.deliveryapp.customer.data.repository.AuthGateway
import kotlinx.coroutines.launch

class SessionViewModel(private val authGateway: AuthGateway) : ViewModel() {
    var state by mutableStateOf(SessionUiState())
        private set

    init {
        viewModelScope.launch {
            val authenticated = authGateway.validateStoredSession()
            state = SessionUiState(isChecking = false, isAuthenticated = authenticated)
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SessionViewModel(container.authRepository) as T
        }
    }
}

data class SessionUiState(
    val isChecking: Boolean = true,
    val isAuthenticated: Boolean = false
)
