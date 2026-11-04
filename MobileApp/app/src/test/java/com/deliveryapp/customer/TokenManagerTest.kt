package com.deliveryapp.customer

import com.deliveryapp.customer.core.storage.TokenManager
import com.deliveryapp.customer.core.storage.TokenStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenManagerTest {
    @Test
    fun saveAndClear_persistsTokenPair() = runTest {
        val tokenManager = TokenManager(InMemoryTokenStorage())

        tokenManager.save("access-token", "refresh-token")

        assertEquals("access-token", tokenManager.currentAccessToken())
        assertEquals("refresh-token", tokenManager.currentRefreshToken())
        assertTrue(tokenManager.hasSession())

        tokenManager.clear()

        assertNull(tokenManager.currentAccessToken())
        assertNull(tokenManager.currentRefreshToken())
        assertFalse(tokenManager.hasSession())
    }
}

private class InMemoryTokenStorage : TokenStorage {
    override val accessToken = MutableStateFlow<String?>(null)
    override val refreshToken = MutableStateFlow<String?>(null)

    override suspend fun save(accessToken: String, refreshToken: String) {
        this.accessToken.value = accessToken
        this.refreshToken.value = refreshToken
    }

    override suspend fun clear() {
        accessToken.value = null
        refreshToken.value = null
    }
}
