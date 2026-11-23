package com.deliveryapp.shipper.feature.delivery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deliveryapp.shipper.core.location.DeviceFix
import com.deliveryapp.shipper.core.location.LocationReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LocationUiState(val fix: DeviceFix? = null, val loading: Boolean = false, val message: String? = null, val denied: Boolean = false)
class LocationViewModel(private val reader: LocationReader) : ViewModel() {
    private val mutable = MutableStateFlow(LocationUiState())
    val state = mutable.asStateFlow()
    private var readJob: Job? = null
    fun reset() {
        readJob?.cancel()
        readJob = null
        mutable.value = LocationUiState()
    }
    fun denied() {
        reset()
        mutable.value = LocationUiState(denied = true, message = "Chưa được cấp quyền vị trí. Bạn vẫn có thể xem điểm lấy/giao và mở chỉ đường.")
    }
    fun refresh() {
        if (state.value.loading) return
        mutable.value = mutable.value.copy(loading = true, message = null, denied = false)
        readJob = viewModelScope.launch {
            try {
                val fix = reader.read()
                ensureActive()
                mutable.value = LocationUiState(fix, message = if (fix == null) "Chưa lấy được vị trí mới. Bật GPS hoặc đặt vị trí cho Emulator rồi thử lại." else null)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.value = LocationUiState(message = "Không đọc được vị trí. Vui lòng kiểm tra quyền và GPS.") }
        }
    }
}
