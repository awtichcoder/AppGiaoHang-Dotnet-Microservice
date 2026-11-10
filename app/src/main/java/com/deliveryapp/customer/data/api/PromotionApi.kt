package com.deliveryapp.customer.data.api

import com.deliveryapp.customer.data.dto.PromotionListItem
import retrofit2.http.GET

interface PromotionApi {
    @GET("api/promotion") suspend fun active(): List<PromotionListItem>
}
