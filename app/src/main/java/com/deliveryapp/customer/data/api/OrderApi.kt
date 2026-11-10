package com.deliveryapp.customer.data.api

import com.deliveryapp.customer.data.dto.CancelOrderRequest
import com.deliveryapp.customer.data.dto.CreateOrderRequest
import com.deliveryapp.customer.data.dto.OrderPricePreview
import com.deliveryapp.customer.data.dto.OrderResponse
import com.deliveryapp.customer.data.dto.PagedResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface OrderApi {
    @POST("api/order/preview") suspend fun preview(@Body request: CreateOrderRequest): OrderPricePreview
    @POST("api/order") suspend fun create(@Body request: CreateOrderRequest): OrderResponse
    @GET("api/order") suspend fun getOrders(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
        @Query("status") status: String? = null
    ): PagedResponse<OrderResponse>
    @GET("api/order/{orderId}") suspend fun getById(@Path("orderId") orderId: String): OrderResponse
    @POST("api/order/{orderId}/cancel") suspend fun cancel(
        @Path("orderId") orderId: String,
        @Body request: CancelOrderRequest
    ): OrderResponse
}
