package com.deliveryapp.customer.data.dto

data class RegisterRequest(val email: String, val password: String, val fullName: String, val phone: String)
data class LoginRequest(val email: String, val password: String)
data class RefreshRequest(val refreshToken: String)
data class LogoutRequest(val refreshToken: String)
data class UserResponse(val userId: String, val fullName: String, val email: String, val phone: String, val role: String)
data class TokenResponse(val accessToken: String, val refreshToken: String, val expiresAt: String)
data class LogoutResponse(val success: Boolean, val revokedAt: String)
