package com.deliveryapp.customer.data.repository

import com.deliveryapp.customer.core.network.ApiResult
import com.deliveryapp.customer.core.network.safeApiCall
import com.deliveryapp.customer.data.api.LoyaltyApi
import com.deliveryapp.customer.data.dto.LoyaltyAccountResponse

class LoyaltyRepository(private val loyaltyApi: LoyaltyApi) {
    suspend fun getLoyalty(): ApiResult<LoyaltyAccountResponse> = safeApiCall { loyaltyApi.getMe() }
}

