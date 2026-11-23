package com.deliveryapp.customer.data.repository

import com.deliveryapp.customer.core.network.ApiResult
import com.deliveryapp.customer.core.network.safeApiCall
import com.deliveryapp.customer.core.storage.TokenManager
import com.deliveryapp.customer.data.api.IdentityApi
import com.deliveryapp.customer.data.dto.LoginRequest
import com.deliveryapp.customer.data.dto.LogoutRequest
import com.deliveryapp.customer.data.dto.RegisterRequest
import com.deliveryapp.customer.data.dto.TokenResponse
import com.deliveryapp.customer.data.dto.UserResponse

interface AuthGateway {
    suspend fun register(request: RegisterRequest): ApiResult<UserResponse>
    suspend fun login(request: LoginRequest): ApiResult<TokenResponse>
    suspend fun logout()
    suspend fun validateStoredSession(): Boolean
}

class AuthRepository(
    private val identityApi: IdentityApi,
    private val tokenManager: TokenManager
) : AuthGateway {
    override suspend fun register(request: RegisterRequest): ApiResult<UserResponse> =
        safeApiCall { identityApi.register(request) }

    override suspend fun login(request: LoginRequest): ApiResult<TokenResponse> {
        val result = safeApiCall { identityApi.login(request) }
        if (result is ApiResult.Success) {
            tokenManager.save(result.data.accessToken, result.data.refreshToken)
        }
        return result
    }

    override suspend fun logout() {
        val refreshToken = tokenManager.currentRefreshToken()
        try {
            if (!refreshToken.isNullOrBlank()) {
                identityApi.logout(LogoutRequest(refreshToken))
            }
        } finally {
            tokenManager.clear()
        }
    }

    override suspend fun validateStoredSession(): Boolean {
        if (!tokenManager.hasSession()) return false
        return when (safeApiCall { identityApi.getMe() }) {
            is ApiResult.Success -> true
            else -> {
                tokenManager.clear()
                false
            }
        }
    }
}
