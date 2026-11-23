package com.deliveryapp.customer.data.repository

import com.deliveryapp.customer.core.network.ApiResult
import com.deliveryapp.customer.core.network.safeApiCall
import com.deliveryapp.customer.data.api.DeliveryApi
import com.deliveryapp.customer.data.api.LoyaltyApi
import com.deliveryapp.customer.data.api.OrderApi
import com.deliveryapp.customer.data.api.PromotionApi
import com.deliveryapp.customer.data.dto.CancelOrderRequest
import com.deliveryapp.customer.data.dto.CreateOrderRequest
import com.deliveryapp.customer.data.dto.DeliveryResponse
import com.deliveryapp.customer.data.dto.DriverLocationResponse
import com.deliveryapp.customer.data.dto.LoyaltyAccountResponse
import com.deliveryapp.customer.data.dto.MatchingStatusResponse
import com.deliveryapp.customer.data.dto.OrderPricePreview
import com.deliveryapp.customer.data.dto.OrderResponse
import com.deliveryapp.customer.data.dto.PagedResponse
import com.deliveryapp.customer.data.dto.PointsPreviewResponse
import com.deliveryapp.customer.data.dto.PreviewPointsRequest
import com.deliveryapp.customer.data.dto.PromotionListItem

/** Everything the create-order and tracking screens need from the backend (fakeable in tests). */
interface OrderGateway {
    suspend fun promotions(): ApiResult<List<PromotionListItem>>
    suspend fun loyalty(): ApiResult<LoyaltyAccountResponse>
    suspend fun preview(request: CreateOrderRequest): ApiResult<OrderPricePreview>
    suspend fun previewPoints(points: Int, orderAmount: Long): ApiResult<PointsPreviewResponse>
    suspend fun create(request: CreateOrderRequest): ApiResult<OrderResponse>
    suspend fun orders(page: Int = 1, pageSize: Int = 20): ApiResult<PagedResponse<OrderResponse>>
    suspend fun order(orderId: String): ApiResult<OrderResponse>
    suspend fun cancel(orderId: String, version: Int): ApiResult<OrderResponse>
    suspend fun delivery(orderId: String): ApiResult<DeliveryResponse>
    suspend fun matching(orderId: String): ApiResult<MatchingStatusResponse>
    suspend fun driverLocation(deliveryId: String): ApiResult<DriverLocationResponse>
}

class OrderRepository(
    private val orderApi: OrderApi,
    private val deliveryApi: DeliveryApi,
    private val loyaltyApi: LoyaltyApi,
    private val promotionApi: PromotionApi
) : OrderGateway {
    override suspend fun promotions() = safeApiCall { promotionApi.active() }
    override suspend fun loyalty() = safeApiCall { loyaltyApi.getMe() }
    override suspend fun preview(request: CreateOrderRequest) = safeApiCall { orderApi.preview(request) }
    override suspend fun previewPoints(points: Int, orderAmount: Long) =
        safeApiCall { loyaltyApi.preview(PreviewPointsRequest(points, orderAmount)) }
    override suspend fun create(request: CreateOrderRequest) = safeApiCall { orderApi.create(request) }
    override suspend fun orders(page: Int, pageSize: Int) = safeApiCall { orderApi.getOrders(page, pageSize) }
    override suspend fun order(orderId: String) = safeApiCall { orderApi.getById(orderId) }
    override suspend fun cancel(orderId: String, version: Int) =
        safeApiCall { orderApi.cancel(orderId, CancelOrderRequest(reasonCode = "CUSTOMER_CANCELLED", version = version)) }
    override suspend fun delivery(orderId: String) = safeApiCall { deliveryApi.getByOrder(orderId) }
    override suspend fun matching(orderId: String) = safeApiCall { deliveryApi.getMatchingStatus(orderId) }
    override suspend fun driverLocation(deliveryId: String) = safeApiCall { deliveryApi.getDriverLocation(deliveryId) }
}

/** Point on the map chosen by the customer. */
data class GeoPoint(val latitude: Double, val longitude: Double)

/** Pre-filled form used by "Đặt lại" after an order found no driver or was cancelled. */
data class OrderDraft(
    val pickupAddress: String,
    val pickup: GeoPoint?,
    val dropoffAddress: String,
    val dropoff: GeoPoint?,
    val receiverPhone: String
)

/** Hands a draft from the tracking screen to the next create-order screen (consumed once). */
class OrderDraftHolder {
    private var draft: OrderDraft? = null
    fun put(value: OrderDraft) { draft = value }
    fun take(): OrderDraft? = draft.also { draft = null }
}
