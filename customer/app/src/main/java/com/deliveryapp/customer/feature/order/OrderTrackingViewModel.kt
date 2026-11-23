package com.deliveryapp.customer.feature.order

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.deliveryapp.customer.AppContainer
import com.deliveryapp.customer.core.network.ApiResult
import com.deliveryapp.customer.core.network.isSessionExpired
import com.deliveryapp.customer.data.dto.DeliveryResponse
import com.deliveryapp.customer.data.dto.DriverLocationResponse
import com.deliveryapp.customer.data.dto.MatchingStatusResponse
import com.deliveryapp.customer.data.dto.OrderResponse
import com.deliveryapp.customer.data.repository.GeoPoint
import com.deliveryapp.customer.data.repository.OrderDraft
import com.deliveryapp.customer.data.repository.OrderDraftHolder
import com.deliveryapp.customer.data.repository.OrderGateway
import kotlinx.coroutines.launch

class OrderTrackingViewModel(
    val orderId: String,
    private val gateway: OrderGateway,
    private val drafts: OrderDraftHolder
) : ViewModel() {
    var state by mutableStateOf(OrderTrackingUiState(isLoading = true))
        private set

    /** The screen keeps polling while this is true (every [POLL_INTERVAL_MS]). */
    val shouldPoll: Boolean get() = state.order?.status !in OrderStatuses.final && !state.sessionExpired

    /** One refresh of everything the screen shows. */
    suspend fun refresh() {
        val orderResult = gateway.order(orderId)
        if (orderResult is ApiResult.Error) {
            state = state.copy(
                isLoading = false,
                // Keep showing the last data on a transient failure; only an empty screen shows the error.
                errorMessage = if (state.order == null) orderResult.message else null,
                refreshWarning = if (state.order != null) "Mất kết nối, đang thử lại…" else null,
                sessionExpired = orderResult.isSessionExpired()
            )
            return
        }
        val order = (orderResult as ApiResult.Success).data
        // The delivery exists once the order outbox has handed the order to Delivery Service (404 before).
        val delivery = (gateway.delivery(orderId) as? ApiResult.Success)?.data ?: state.delivery
        val matching = if (order.status == OrderStatuses.PENDING || order.status == OrderStatuses.NO_DRIVER_FOUND)
            (gateway.matching(orderId) as? ApiResult.Success)?.data ?: state.matching else null

        var location: DriverLocationResponse? = null
        var locationNote: String? = null
        if (order.status in OrderStatuses.trackable && delivery?.driverId != null) {
            when (val result = gateway.driverLocation(delivery.deliveryId)) {
                is ApiResult.Success -> location = result.data
                is ApiResult.Error -> locationNote = when (result.code) {
                    "DRIVER_LOCATION_STALE" -> "Vị trí tài xế tạm thời chưa cập nhật (mất tín hiệu GPS)."
                    "DRIVER_LOCATION_NOT_FOUND" -> "Tài xế chưa gửi vị trí."
                    else -> "Chưa lấy được vị trí tài xế."
                }
                ApiResult.Loading -> Unit
            }
        }
        state = state.copy(
            isLoading = false, errorMessage = null, refreshWarning = null,
            order = order, delivery = delivery, matching = matching,
            driverLocation = location, locationNote = locationNote
        )
    }

    fun reload() {
        state = state.copy(isLoading = state.order == null, errorMessage = null)
        viewModelScope.launch { refresh() }
    }

    fun askCancel() { state = state.copy(confirmCancel = true, cancelError = null) }
    fun dismissCancel() { state = state.copy(confirmCancel = false) }

    fun cancel() {
        val order = state.order ?: return
        state = state.copy(confirmCancel = false, cancelling = true, cancelError = null)
        viewModelScope.launch {
            when (val result = gateway.cancel(orderId, order.version)) {
                is ApiResult.Success -> {
                    state = state.copy(cancelling = false, order = result.data)
                    refresh()
                }
                is ApiResult.Error -> {
                    state = state.copy(
                        cancelling = false,
                        cancelError = when (result.code) {
                            "ORDER_VERSION_CONFLICT", "ORDER_CANNOT_CANCEL" ->
                                "Không thể hủy: trạng thái đơn vừa thay đổi (tài xế có thể đã lấy hàng)."
                            else -> result.message
                        },
                        sessionExpired = result.isSessionExpired()
                    )
                    refresh()
                }
                ApiResult.Loading -> Unit
            }
        }
    }

    /** Stores the route of this order so the create-order screen opens pre-filled. */
    fun prepareReorder() {
        val order = state.order ?: return
        val delivery = state.delivery
        drafts.put(
            OrderDraft(
                pickupAddress = order.pickupAddress,
                pickup = delivery.point(pickup = true),
                dropoffAddress = order.dropoffAddress,
                dropoff = delivery.point(pickup = false),
                receiverPhone = order.receiverPhone
            )
        )
    }

    fun consumeSessionExpired() { state = state.copy(sessionExpired = false) }

    companion object {
        const val POLL_INTERVAL_MS = 4_000L

        fun factory(container: AppContainer, orderId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    OrderTrackingViewModel(orderId, container.orderRepository, container.orderDrafts) as T
            }
    }
}

fun DeliveryResponse?.point(pickup: Boolean): GeoPoint? {
    if (this == null) return null
    val lat = if (pickup) pickupLatitude else dropoffLatitude
    val lon = if (pickup) pickupLongitude else dropoffLongitude
    return if (lat != null && lon != null && !(lat == 0.0 && lon == 0.0)) GeoPoint(lat, lon) else null
}

data class OrderTrackingUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val refreshWarning: String? = null,
    val order: OrderResponse? = null,
    val delivery: DeliveryResponse? = null,
    val matching: MatchingStatusResponse? = null,
    val driverLocation: DriverLocationResponse? = null,
    val locationNote: String? = null,
    val confirmCancel: Boolean = false,
    val cancelling: Boolean = false,
    val cancelError: String? = null,
    val sessionExpired: Boolean = false
)
