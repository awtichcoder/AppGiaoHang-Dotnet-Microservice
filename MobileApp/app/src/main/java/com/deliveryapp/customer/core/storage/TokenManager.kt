package com.deliveryapp.customer.core.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "customer_session")

class TokenManager(private val storage: TokenStorage) {
    constructor(context: Context) : this(DataStoreTokenStorage(context.sessionDataStore))
    constructor(dataStore: DataStore<Preferences>) : this(DataStoreTokenStorage(dataStore))

    val accessToken: Flow<String?> = storage.accessToken
    val refreshToken: Flow<String?> = storage.refreshToken

    suspend fun currentAccessToken(): String? = accessToken.first()
    suspend fun currentRefreshToken(): String? = refreshToken.first()

    suspend fun hasSession(): Boolean = !currentAccessToken().isNullOrBlank() && !currentRefreshToken().isNullOrBlank()

    suspend fun save(accessToken: String, refreshToken: String) {
        storage.save(accessToken, refreshToken)
    }

    suspend fun clear() {
        storage.clear()
    }
}

interface TokenStorage {
    val accessToken: Flow<String?>
    val refreshToken: Flow<String?>
    suspend fun save(accessToken: String, refreshToken: String)
    suspend fun clear()
}

class DataStoreTokenStorage(private val dataStore: DataStore<Preferences>) : TokenStorage {
    private object Keys {
        val AccessToken = stringPreferencesKey("access_token")
        val RefreshToken = stringPreferencesKey("refresh_token")
    }

    override val accessToken: Flow<String?> = dataStore.data.map { it[Keys.AccessToken] }
    override val refreshToken: Flow<String?> = dataStore.data.map { it[Keys.RefreshToken] }

    override suspend fun save(accessToken: String, refreshToken: String) {
        dataStore.edit {
            it[Keys.AccessToken] = accessToken
            it[Keys.RefreshToken] = refreshToken
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}
