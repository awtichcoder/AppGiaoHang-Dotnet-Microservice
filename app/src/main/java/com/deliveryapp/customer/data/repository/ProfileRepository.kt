package com.deliveryapp.customer.data.repository

import com.deliveryapp.customer.core.network.ApiResult
import com.deliveryapp.customer.core.network.safeApiCall
import com.deliveryapp.customer.data.api.IdentityApi
import com.deliveryapp.customer.data.dto.UserResponse

class ProfileRepository(private val identityApi: IdentityApi) {
    suspend fun getProfile(): ApiResult<UserResponse> = safeApiCall { identityApi.getMe() }
}
