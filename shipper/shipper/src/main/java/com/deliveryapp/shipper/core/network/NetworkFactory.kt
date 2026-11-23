package com.deliveryapp.shipper.core.network

import com.deliveryapp.shipper.BuildConfig
import com.deliveryapp.shipper.core.storage.TokenStore
import com.deliveryapp.shipper.core.storage.TokenPair
import com.deliveryapp.shipper.data.api.*
import com.deliveryapp.shipper.data.model.RepositoryException
import com.google.gson.Gson
import com.google.gson.JsonParseException
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NetworkFactory {
    fun create(tokens: TokenStore, baseUrl: String = BuildConfig.API_BASE_URL): Retrofit {
        val bareClient = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
        val refreshApi = retrofit(baseUrl, bareClient).create(RefreshApi::class.java)
        val logger = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
            redactHeader("Authorization")
        }
        val client = bareClient.newBuilder()
            .addInterceptor { chain ->
                val request = chain.request()
                val publicEndpoint = request.url.encodedPath.endsWith("/login")
                val pair = if (publicEndpoint) null else runBlocking { tokens.current() }
                chain.proceed(request.newBuilder().apply {
                    pair?.let { header("Authorization", "Bearer ${it.accessToken}") }
                }.build())
            }
            .addInterceptor(logger)
            .authenticator(RefreshAuthenticator(tokens, refreshApi))
            .build()
        return retrofit(baseUrl, client)
    }
    private fun retrofit(url: String, client: OkHttpClient) = Retrofit.Builder()
        .baseUrl(url).client(client).addConverterFactory(GsonConverterFactory.create()).build()
}
internal class RefreshAuthenticator(private val tokens: TokenStore, private val api: RefreshApi) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? = synchronized(this) {
        if (response.priorResponse != null || response.request.header("Authorization") == null) return@synchronized null
        val before = runBlocking { tokens.current() } ?: return@synchronized null
        val sent = response.request.header("Authorization")
        if (sent != "Bearer ${before.accessToken}") return@synchronized response.request.withToken(before.accessToken)
        val refreshed = try { api.refresh(RefreshRequest(before.refreshToken)).execute() }
        catch (_: IOException) { return@synchronized null }
        catch (_: JsonParseException) { return@synchronized null }
        val body = refreshed.body()
        if (refreshed.isSuccessful && body != null) {
            // A malformed success response must never replace a valid saved session.
            if (body.accessToken.isNullOrBlank() || body.refreshToken.isNullOrBlank()) return@synchronized null
            val pair = TokenPair(body.accessToken, body.refreshToken)
            if (runBlocking { tokens.replaceIfCurrent(before, pair) }) response.request.withToken(pair.accessToken) else null
        } else {
            refreshed.errorBody()?.close()
            if (refreshed.code() == 400 || refreshed.code() == 401) runBlocking { tokens.replaceIfCurrent(before, null) }
            null
        }
    }
    private fun Request.withToken(token: String) = newBuilder().header("Authorization", "Bearer $token").build()
}
data class ApiError(val code: String?, val message: String?, val details: Any?)
suspend fun <T> apiCall(block: suspend () -> T): T = try { block() }
catch (e: CancellationException) { throw e }
catch (e: HttpException) {
    val error = runCatching { Gson().fromJson(e.response()?.errorBody()?.string(), ApiError::class.java) }.getOrNull()
    throw RepositoryException(error?.code ?: "HTTP_${e.code()}", error?.message ?: "Máy chủ chưa xử lý được yêu cầu.")
}
catch (_: IOException) { throw RepositoryException("NETWORK", "Không kết nối được máy chủ. Vui lòng thử lại.") }
