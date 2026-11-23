package com.deliveryapp.shipper.core.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow

private val Context.preferences by preferencesDataStore("shipper_preferences")
interface SettingsPreferences {
    val largeText: Flow<Boolean>
    suspend fun largeText(value: Boolean)
}
class SettingsStore(context: Context) : SettingsPreferences {
    private val store = context.applicationContext.preferences
    private val large = booleanPreferencesKey("large_text")
    override val largeText = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { it[large] ?: false }
    override suspend fun largeText(value: Boolean) { store.edit { it[large] = value } }
}
