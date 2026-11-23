package com.deliveryapp.customer.core.network

import com.deliveryapp.customer.BuildConfig
import com.deliveryapp.customer.core.storage.TokenManager
import com.deliveryapp.customer.data.api.IdentityApi
import com.deliveryapp.customer.data.dto.RefreshRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class TokenRefreshAuthenticator(
    private val tokenManager: TokenManager
) : Authenticator {
    private val refreshApi: IdentityApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(IdentityApi::class.java)
    }

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null
        val refreshToken = runBlocking { tokenManager.currentRefreshToken() } ?: return null

        synchronized(this) {
            val requestToken = response.request.header("Authorization")?.removePrefix("Bearer ")
            val latestToken = runBlocking { tokenManager.currentAccessToken() }
            if (!latestToken.isNullOrBlank() && latestToken != requestToken) {
                return response.request.withBearer(latestToken)
            }

            val tokenResponse = runBlocking {
                runCatching { refreshApi.refresh(RefreshRequest(refreshToken)) }.getOrNull()
            }

            return if (tokenResponse != null) {
                runBlocking { tokenManager.save(tokenResponse.accessToken, tokenResponse.refreshToken) }
                response.request.withBearer(tokenResponse.accessToken)
            } else {
                runBlocking { tokenManager.clear() }
                null
            }
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private fun Request.withBearer(token: String): Request = newBuilder()
        .header("Authorization", "Bearer $token")
        .build()
}
