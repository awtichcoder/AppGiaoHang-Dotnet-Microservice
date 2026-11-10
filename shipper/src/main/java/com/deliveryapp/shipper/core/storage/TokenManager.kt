package com.deliveryapp.shipper.core.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.first

private val Context.tokenStore by preferencesDataStore("shipper_session")
data class TokenPair(val accessToken: String, val refreshToken: String) {
    // Gson may populate null despite Kotlin's non-null declarations.
    fun isValid() = !accessToken.isNullOrBlank() && !refreshToken.isNullOrBlank()
}
interface TokenStore {
    suspend fun current(): TokenPair?
    suspend fun save(pair: TokenPair)
    suspend fun replaceIfCurrent(expected: TokenPair, replacement: TokenPair?): Boolean
    suspend fun clear()
}

/** Prepared for live auth. Mock mode never writes tokens. DataStore holds one encrypted pair. */
class TokenManager(context: Context) : TokenStore {
    private val store = context.applicationContext.tokenStore
    private val key = stringPreferencesKey("encrypted_pair")
    private val cipher = TokenCipher()
    private val gson = Gson()
    override suspend fun current(): TokenPair? {
        val encoded = store.data.first()[key] ?: return null
        return runCatching { gson.fromJson(cipher.decrypt(encoded), TokenPair::class.java) }.getOrNull()
            ?.takeIf(TokenPair::isValid)
    }
    override suspend fun save(pair: TokenPair) {
        require(pair.isValid()) { "Token pair must not be empty" }
        val encrypted = cipher.encrypt(gson.toJson(pair))
        store.edit { it[key] = encrypted }
    }
    override suspend fun replaceIfCurrent(expected: TokenPair, replacement: TokenPair?): Boolean {
        require(replacement == null || replacement.isValid()) { "Token pair must not be empty" }
        var replaced = false
        store.edit { preferences ->
            val pair = preferences[key]?.let { runCatching { gson.fromJson(cipher.decrypt(it), TokenPair::class.java) }.getOrNull() }
            if (pair == expected) {
                if (replacement == null) preferences.remove(key) else preferences[key] = cipher.encrypt(gson.toJson(replacement))
                replaced = true
            }
        }
        return replaced
    }
    override suspend fun clear() { store.edit { it.remove(key) } }
}
private class TokenCipher {
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("shipper.session.aes", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("shipper.session.aes", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun encrypt(text: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + "." +
            Base64.encodeToString(cipher.doFinal(text.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
    fun decrypt(value: String): String {
        val parts = value.split('.', limit = 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        }
        return cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)).toString(Charsets.UTF_8)
    }
}
