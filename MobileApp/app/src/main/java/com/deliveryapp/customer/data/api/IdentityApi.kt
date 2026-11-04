package com.deliveryapp.customer.data.api

import com.deliveryapp.customer.data.dto.LoginRequest
import com.deliveryapp.customer.data.dto.LogoutRequest
import com.deliveryapp.customer.data.dto.LogoutResponse
import com.deliveryapp.customer.data.dto.RefreshRequest
import com.deliveryapp.customer.data.dto.RegisterRequest
import com.deliveryapp.customer.data.dto.TokenResponse
import com.deliveryapp.customer.data.dto.UserResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface IdentityApi {
    @POST("api/identity/register") suspend fun register(@Body request: RegisterRequest): UserResponse
    @POST("api/identity/login") suspend fun login(@Body request: LoginRequest): TokenResponse
    @POST("api/identity/refresh") suspend fun refresh(@Body request: RefreshRequest): TokenResponse
    @POST("api/identity/logout") suspend fun logout(@Body request: LogoutRequest): LogoutResponse
    @GET("api/identity/me") suspend fun getMe(): UserResponse
}
