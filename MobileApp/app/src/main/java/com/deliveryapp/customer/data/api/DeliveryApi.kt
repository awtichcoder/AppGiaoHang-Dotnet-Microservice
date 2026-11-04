package com.deliveryapp.customer.data.api

import com.deliveryapp.customer.data.dto.DeliveryResponse
import com.deliveryapp.customer.data.dto.DriverLocationResponse
import com.deliveryapp.customer.data.dto.MatchingStatusResponse
import retrofit2.http.GET
import retrofit2.http.Path

interface DeliveryApi {
    @GET("api/delivery/order/{orderId}") suspend fun getByOrder(@Path("orderId") orderId: String): DeliveryResponse
    @GET("api/delivery/order/{orderId}/matching-status") suspend fun getMatchingStatus(
        @Path("orderId") orderId: String
    ): MatchingStatusResponse
    @GET("api/delivery/{deliveryId}/location") suspend fun getDriverLocation(
        @Path("deliveryId") deliveryId: String
    ): DriverLocationResponse
}
