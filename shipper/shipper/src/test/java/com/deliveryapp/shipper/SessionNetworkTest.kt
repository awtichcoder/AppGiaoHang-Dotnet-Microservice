package com.deliveryapp.shipper

import com.deliveryapp.shipper.core.network.NetworkFactory
import com.deliveryapp.shipper.core.storage.TokenPair
import com.deliveryapp.shipper.core.storage.TokenStore
import com.deliveryapp.shipper.data.api.IdentityApi
import com.deliveryapp.shipper.data.api.LoginRequest
import com.google.gson.Gson
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import retrofit2.HttpException
import java.util.concurrent.TimeUnit

class SessionNetworkTest {
    private lateinit var server: MockWebServer
    private lateinit var store: MemoryTokens
    private lateinit var api: IdentityApi
    private val original = TokenPair("old-access", "old-refresh")
    private val newPair = TokenPair("new-access", "new-refresh")
    private val userJson = """{"userId":"driver","fullName":"Driver","email":"driver@example.com","phone":null,"role":"DRIVER"}"""
    private val tokenJson = """{"accessToken":"new-access","refreshToken":"new-refresh","expiresAt":"2026-11-01T00:00:00Z"}"""

    @Before fun before() {
        server = MockWebServer().apply { start() }
        store = MemoryTokens(original)
        api = NetworkFactory.create(store, server.url("/").toString()).create(IdentityApi::class.java)
    }
    @After fun after() { server.shutdown() }

    @Test fun loginDoesNotSendStoredBearer() = runTest {
        server.enqueue(MockResponse().setBody(tokenJson))
        api.login(LoginRequest("driver@example.com", "demo"))
        assertNull(request().getHeader("Authorization"))
        assertEquals(1, server.requestCount)
    }

    @Test fun refreshRetriesOnceWithNewTokenAndNoBearerOnRefresh() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setBody(tokenJson))
        server.enqueue(MockResponse().setBody(userJson))
        assertEquals("DRIVER", api.me().role)
        assertEquals("Bearer old-access", request().getHeader("Authorization"))
        val refresh = request()
        assertEquals("/api/identity/refresh", refresh.path)
        assertNull(refresh.getHeader("Authorization"))
        assertTrue(refresh.body.readUtf8().contains("old-refresh"))
        assertEquals("Bearer new-access", request().getHeader("Authorization"))
        assertEquals(newPair, store.current())
    }

    @Test fun repeatedUnauthorizedDoesNotLoop() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setBody(tokenJson))
        server.enqueue(MockResponse().setResponseCode(401))
        unauthorized()
        assertEquals(3, server.requestCount)
    }

    @Test fun transientRefreshFailurePreservesSession() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(503))
        unauthorized()
        assertEquals(original, store.current())
        assertEquals(2, server.requestCount)
    }

    @Test fun rejectedRefreshClearsSession() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(400))
        unauthorized()
        assertNull(store.current())
    }

    @Test fun missingRefreshFieldsCannotOverwriteSavedTokens() = runTest {
        for (body in listOf("{}", """{"accessToken":"new-access"}""", """{"accessToken":"","refreshToken":"x"}""", "{not-json")) {
            server.enqueue(MockResponse().setResponseCode(401))
            server.enqueue(MockResponse().setBody(body))
            unauthorized()
            assertEquals(original, store.current())
        }
        val decoded = Gson().fromJson("{}", TokenPair::class.java)
        assertFalse(decoded.isValid())
    }

    @Test fun logoutWhileRefreshIsRunningCannotResurrectSession() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return if (request.path == "/api/identity/refresh") {
                    store.value = null
                    MockResponse().setBody(tokenJson)
                } else MockResponse().setResponseCode(401)
            }
        }
        unauthorized()
        assertNull(store.current())
        assertEquals(2, server.requestCount)
    }

    private fun request() = requireNotNull(server.takeRequest(2, TimeUnit.SECONDS))
    private suspend fun unauthorized() {
        try { api.me(); fail("Expected unauthorized response") }
        catch (e: HttpException) { assertEquals(401, e.code()) }
    }

    private class MemoryTokens(@Volatile var value: TokenPair?) : TokenStore {
        override suspend fun current() = value
        override suspend fun save(pair: TokenPair) { value = pair }
        override suspend fun clear() { value = null }
        override suspend fun replaceIfCurrent(expected: TokenPair, replacement: TokenPair?): Boolean = synchronized(this) {
            if (value != expected) false else { value = replacement; true }
        }
    }
}
