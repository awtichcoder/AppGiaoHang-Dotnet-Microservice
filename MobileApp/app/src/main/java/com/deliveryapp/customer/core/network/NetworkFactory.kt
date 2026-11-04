package com.deliveryapp.customer.core.network

import com.deliveryapp.customer.BuildConfig
import com.deliveryapp.customer.core.storage.TokenManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NetworkFactory {
    fun create(tokenManager: TokenManager): Retrofit {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
            redactHeader("Authorization")
        }
        val client = OkHttpClient.Builder()
            .authenticator(TokenRefreshAuthenticator(tokenManager))
            .addInterceptor(AuthInterceptor(tokenManager))
            .addInterceptor(logging)
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
