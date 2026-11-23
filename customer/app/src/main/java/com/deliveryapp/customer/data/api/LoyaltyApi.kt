package com.deliveryapp.customer.data.api

import com.deliveryapp.customer.data.dto.LoyaltyAccountResponse
import com.deliveryapp.customer.data.dto.PointsPreviewResponse
import com.deliveryapp.customer.data.dto.PreviewPointsRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface LoyaltyApi {
    @GET("api/loyalty/me") suspend fun getMe(): LoyaltyAccountResponse
    @POST("api/loyalty/preview") suspend fun preview(@Body request: PreviewPointsRequest): PointsPreviewResponse
}
