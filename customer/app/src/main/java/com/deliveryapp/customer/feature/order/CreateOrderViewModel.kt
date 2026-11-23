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
import com.deliveryapp.customer.data.dto.CreateOrderRequest
import com.deliveryapp.customer.data.dto.GeoPointRequest
import com.deliveryapp.customer.data.dto.OrderPricePreview
import com.deliveryapp.customer.data.dto.PromotionListItem
import com.deliveryapp.customer.data.repository.GeoPoint
import com.deliveryapp.customer.data.repository.OrderDraft
import com.deliveryapp.customer.data.repository.OrderGateway
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class PointKind { PICKUP, DROPOFF }

class CreateOrderViewModel(
    private val gateway: OrderGateway,
    draft: OrderDraft? = null,
    private val previewDebounceMs: Long = 600,
    private val newRequestId: () -> String = { UUID.randomUUID().toString() }
) : ViewModel() {
    var state by mutableStateOf(CreateOrderUiState())
        private set

    /** Same id for retries of one submission, so a lost response cannot create a second order. */
    private var clientRequestId: String? = null
    private var previewJob: Job? = null

    init {
        draft?.let {
            state = state.copy(
                pickupAddress = it.pickupAddress, pickup = it.pickup,
                dropoffAddress = it.dropoffAddress, dropoff = it.dropoff,
                receiverPhone = it.receiverPhone
            )
        }
        loadOptions()
        schedulePreview()
    }

    fun loadOptions() {
        viewModelScope.launch {
            val promotions = async { gateway.promotions() }
            val loyalty = async { gateway.loyalty() }
            val promotionResult = promotions.await()
            val loyaltyResult = loyalty.await()
            state = state.copy(
                promotions = (promotionResult as? ApiResult.Success)?.data.orEmpty(),
                availablePoints = (loyaltyResult as? ApiResult.Success)?.data?.availablePoints,
                sessionExpired = state.sessionExpired ||
                    listOf(promotionResult, loyaltyResult).any { it is ApiResult.Error && it.isSessionExpired() }
            )
        }
    }

    fun onAddressChange(kind: PointKind, value: String) = edit(preview = false) {
        when (kind) {
            PointKind.PICKUP -> copy(pickupAddress = value, pickupError = null)
            PointKind.DROPOFF -> copy(dropoffAddress = value, dropoffError = null)
        }
    }

    /** [suggestedAddress] comes from reverse geocoding; it only fills an empty address field. */
    fun onPointPicked(kind: PointKind, point: GeoPoint, suggestedAddress: String?) = edit {
        when (kind) {
            PointKind.PICKUP -> copy(
                pickup = point, pickupError = null,
                pickupAddress = pickupAddress.ifBlank { suggestedAddress.orEmpty() }
            )
            PointKind.DROPOFF -> copy(
                dropoff = point, dropoffError = null,
                dropoffAddress = dropoffAddress.ifBlank { suggestedAddress.orEmpty() }
            )
        }
    }

    fun onPhoneChange(value: String) = edit(preview = false) { copy(receiverPhone = value, phoneError = null) }

    fun onPromotionChange(code: String) = edit {
        copy(promotionCode = code.uppercase().trim(), promotionError = null)
    }

    fun onPromotionToggle(code: String) = onPromotionChange(if (state.promotionCode == code) "" else code)

    fun onPointsChange(value: String) = edit {
        copy(pointsText = value.filter(Char::isDigit).take(7), pointsError = null)
    }

    private fun edit(preview: Boolean = true, change: CreateOrderUiState.() -> CreateOrderUiState) {
        clientRequestId = null
        state = state.change().copy(submitError = null)
        if (preview) schedulePreview()
    }

    private fun schedulePreview() {
        previewJob?.cancel()
        val pickup = state.pickup
        val dropoff = state.dropoff
        if (pickup == null || dropoff == null) {
            state = state.copy(preview = null, pointsDiscount = 0, previewLoading = false, previewError = null)
            return
        }
        previewJob = viewModelScope.launch {
            if (previewDebounceMs > 0) delay(previewDebounceMs)
            state = state.copy(previewLoading = true, previewError = null)
            runPreview(pickup, dropoff)
            state = state.copy(previewLoading = false)
        }
    }

    private suspend fun runPreview(pickup: GeoPoint, dropoff: GeoPoint) {
        val code = state.promotionCode.ifBlank { null }
        var result = gateway.preview(previewRequest(pickup, dropoff, code))
        var promotionError: String? = null
        if (code != null && result is ApiResult.Error && result.code in PROMOTION_ERRORS) {
            // Show the fee without the code and explain why the code does not apply.
            promotionError = result.message
            result = gateway.preview(previewRequest(pickup, dropoff, null))
        }
        when (result) {
            is ApiResult.Success -> {
                state = state.copy(preview = result.data, promotionError = promotionError)
                previewPoints(result.data)
            }
            is ApiResult.Error -> state = state.copy(
                preview = null, pointsDiscount = 0, previewError = result.message, promotionError = promotionError,
                sessionExpired = state.sessionExpired || result.isSessionExpired()
            )
            ApiResult.Loading -> Unit
        }
    }

    private suspend fun previewPoints(price: OrderPricePreview) {
        val points = state.points
        if (points <= 0) {
            state = state.copy(pointsDiscount = 0)
            return
        }
        when (val result = gateway.previewPoints(points, price.amountAfterPromotion)) {
            is ApiResult.Success -> state = state.copy(
                pointsDiscount = result.data.discountAmount,
                pointsError = if (result.data.usablePoints < points)
                    "Chỉ dùng được tối đa ${result.data.usablePoints} điểm cho đơn này." else null
            )
            is ApiResult.Error -> state = state.copy(pointsDiscount = 0, pointsError = result.message)
            ApiResult.Loading -> Unit
        }
    }

    fun submit() {
        if (state.submitting) return
        val validated = validate(state)
        state = validated
        if (validated.hasFieldErrors) return
        val pickup = validated.pickup ?: return
        val dropoff = validated.dropoff ?: return
        val requestId = clientRequestId ?: newRequestId().also { clientRequestId = it }
        viewModelScope.launch {
            state = state.copy(submitting = true, submitError = null)
            val result = gateway.create(
                request(pickup, dropoff, state.promotionCode.ifBlank { null }, state.points, requestId)
            )
            state = when (result) {
                is ApiResult.Success -> state.copy(submitting = false, createdOrderId = result.data.orderId)
                is ApiResult.Error -> state.copy(
                    submitting = false,
                    submitError = result.message,
                    promotionError = if (result.code in PROMOTION_ERRORS) result.message else state.promotionError,
                    pointsError = if (result.code?.startsWith("LOYALTY_") == true) result.message else state.pointsError,
                    sessionExpired = result.isSessionExpired()
                )
                ApiResult.Loading -> state
            }
        }
    }

    fun consumeCreated() { state = state.copy(createdOrderId = null) }
    fun consumeSessionExpired() { state = state.copy(sessionExpired = false) }

    private fun request(pickup: GeoPoint, dropoff: GeoPoint, code: String?, points: Int, requestId: String) =
        CreateOrderRequest(
            pickupAddress = state.pickupAddress.trim(),
            dropoffAddress = state.dropoffAddress.trim(),
            pickupLocation = GeoPointRequest(pickup.latitude, pickup.longitude),
            dropoffLocation = GeoPointRequest(dropoff.latitude, dropoff.longitude),
            receiverPhone = normalizePhone(state.receiverPhone),
            promotionCode = code,
            pointsToUse = points,
            clientRequestId = requestId
        )

    /**
     * Preview only prices the route and checks the code. It runs while the customer is still typing,
     * so the text fields (validated by the backend) get fixed valid values.
     */
    private fun previewRequest(pickup: GeoPoint, dropoff: GeoPoint, code: String?) = CreateOrderRequest(
        pickupAddress = "Điểm lấy hàng",
        dropoffAddress = "Điểm giao hàng",
        pickupLocation = GeoPointRequest(pickup.latitude, pickup.longitude),
        dropoffLocation = GeoPointRequest(dropoff.latitude, dropoff.longitude),
        receiverPhone = "0900000000",
        promotionCode = code,
        pointsToUse = 0,
        clientRequestId = PREVIEW_REQUEST_ID
    )

    companion object {
        private const val PREVIEW_REQUEST_ID = "00000000-0000-0000-0000-000000000000"
        private val PROMOTION_ERRORS = setOf("PROMOTION_INVALID", "PROMOTION_NOT_FOUND", "PROMOTION_REJECTED")
        private val PHONE = Regex("^0\\d{9}$")

        fun normalizePhone(value: String): String =
            value.filter { it.isDigit() || it == '+' }.let { if (it.startsWith("+84")) "0" + it.drop(3) else it }

        fun validate(state: CreateOrderUiState): CreateOrderUiState = state.copy(
            pickupError = when {
                state.pickup == null -> "Chọn điểm lấy hàng trên bản đồ"
                state.pickupAddress.isBlank() -> "Nhập địa chỉ lấy hàng"
                else -> null
            },
            dropoffError = when {
                state.dropoff == null -> "Chọn điểm giao hàng trên bản đồ"
                state.dropoffAddress.isBlank() -> "Nhập địa chỉ giao hàng"
                state.pickup != null && state.pickup == state.dropoff -> "Điểm giao phải khác điểm lấy hàng"
                else -> null
            },
            phoneError = when {
                state.receiverPhone.isBlank() -> "Nhập số điện thoại người nhận"
                !PHONE.matches(normalizePhone(state.receiverPhone)) -> "Số điện thoại gồm 10 số, bắt đầu bằng 0"
                else -> null
            },
            pointsError = when {
                state.availablePoints != null && state.points > state.availablePoints ->
                    "Bạn chỉ có ${state.availablePoints} điểm khả dụng"
                else -> state.pointsError
            }
        )

        fun factory(container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                CreateOrderViewModel(container.orderRepository, container.orderDrafts.take()) as T
        }
    }
}

data class CreateOrderUiState(
    val pickupAddress: String = "",
    val pickup: GeoPoint? = null,
    val dropoffAddress: String = "",
    val dropoff: GeoPoint? = null,
    val receiverPhone: String = "",
    val promotionCode: String = "",
    val pointsText: String = "",
    val pickupError: String? = null,
    val dropoffError: String? = null,
    val phoneError: String? = null,
    val promotionError: String? = null,
    val pointsError: String? = null,
    val promotions: List<PromotionListItem> = emptyList(),
    val availablePoints: Int? = null,
    val preview: OrderPricePreview? = null,
    val pointsDiscount: Long = 0,
    val previewLoading: Boolean = false,
    val previewError: String? = null,
    val submitting: Boolean = false,
    val submitError: String? = null,
    val createdOrderId: String? = null,
    val sessionExpired: Boolean = false
) {
    val points: Int get() = pointsText.toIntOrNull() ?: 0
    val total: Long? get() = preview?.let { maxOf(0, it.amountAfterPromotion - pointsDiscount) }
    val hasFieldErrors: Boolean get() =
        listOf(pickupError, dropoffError, phoneError, promotionError, pointsError).any { it != null }
}
