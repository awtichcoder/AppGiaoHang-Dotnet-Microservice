package com.deliveryapp.shipper.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deliveryapp.shipper.core.storage.SettingsPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val store: SettingsPreferences) : ViewModel() {
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    val largeText = store.largeText.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    fun largeText(value: Boolean) {
        viewModelScope.launch {
            mutableError.value = null
            try { store.largeText(value) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutableError.value = "Không lưu được cỡ chữ. Kiểm tra dung lượng thiết bị rồi thử lại." }
        }
    }
}
