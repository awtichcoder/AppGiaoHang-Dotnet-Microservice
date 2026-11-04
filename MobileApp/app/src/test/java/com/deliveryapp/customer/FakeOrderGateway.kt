package com.deliveryapp.customer

import com.deliveryapp.customer.core.network.ApiResult
import com.deliveryapp.customer.data.dto.CreateOrderRequest
import com.deliveryapp.customer.data.dto.DeliveryResponse
import com.deliveryapp.customer.data.dto.DriverLocationResponse
import com.deliveryapp.customer.data.dto.LoyaltyAccountResponse
import com.deliveryapp.customer.data.dto.MatchingStatusResponse
import com.deliveryapp.customer.data.dto.OrderPricePreview
import com.deliveryapp.customer.data.dto.OrderResponse
import com.deliveryapp.customer.data.dto.PagedResponse
import com.deliveryapp.customer.data.dto.PointsPreviewResponse
import com.deliveryapp.customer.data.dto.PromotionListItem
import com.deliveryapp.customer.data.repository.OrderGateway

/** Values mirror real backend responses (see the Kha BE integration run). */
class FakeOrderGateway : OrderGateway {
    var promotionsResult: ApiResult<List<PromotionListItem>> = ApiResult.Success(
        listOf(PromotionListItem("GIAM10", "Giảm 10%", null, 10.0, 20000, 0, "2026-12-31T23:59:59"))
    )
    var loyaltyResult: ApiResult<LoyaltyAccountResponse> =
        ApiResult.Success(LoyaltyAccountResponse("c1", 120, 115, "SILVER", emptyList()))
    val previewRequests = mutableListOf<CreateOrderRequest>()
    var previewResult: (CreateOrderRequest) -> ApiResult<OrderPricePreview> = { request ->
        ApiResult.Success(samplePrice(discount = if (request.promotionCode != null) 5952 else 0))
    }
    var pointsResult: (Int, Long) -> ApiResult<PointsPreviewResponse> = { points, _ ->
        ApiResult.Success(PointsPreviewResponse(points, minOf(points, 115), 115, minOf(points, 115) * 1000L))
    }
    val createRequests = mutableListOf<CreateOrderRequest>()
    var createResult: ApiResult<OrderResponse> = ApiResult.Success(sampleOrder("PENDING"))

    var orderResult: ApiResult<OrderResponse> = ApiResult.Success(sampleOrder("PENDING"))
    var deliveryResult: ApiResult<DeliveryResponse> = ApiResult.Error("HTTP_404", "Không tìm thấy", 404)
    var matchingResult: ApiResult<MatchingStatusResponse> = ApiResult.Success(
        MatchingStatusResponse("o1", "PENDING", "SEARCHING", 3.0, 1, 1, 1, 0, "2026-11-04T03:00:00", null)
    )
    var locationResult: ApiResult<DriverLocationResponse> =
        ApiResult.Success(DriverLocationResponse("d1", "x", 10.7790, 106.6990, "2026-11-04T03:05:00", 5.0))
    var locationCalls = 0
    val cancelled = mutableListOf<Pair<String, Int>>()
    var cancelResult: ApiResult<OrderResponse> = ApiResult.Success(sampleOrder("CANCELLED"))

    override suspend fun promotions() = promotionsResult
    override suspend fun loyalty() = loyaltyResult
    override suspend fun preview(request: CreateOrderRequest) = previewResult(request).also { previewRequests += request }
    override suspend fun previewPoints(points: Int, orderAmount: Long) = pointsResult(points, orderAmount)
    override suspend fun create(request: CreateOrderRequest) = createResult.also { createRequests += request }
    override suspend fun orders(page: Int, pageSize: Int): ApiResult<PagedResponse<OrderResponse>> =
        ApiResult.Success(PagedResponse(1, 20, 1, listOf(sampleOrder("PENDING"))))
    override suspend fun order(orderId: String) = orderResult
    override suspend fun cancel(orderId: String, version: Int) = cancelResult.also { cancelled += orderId to version }
    override suspend fun delivery(orderId: String) = deliveryResult
    override suspend fun matching(orderId: String) = matchingResult
    override suspend fun driverLocation(deliveryId: String) = locationResult.also { locationCalls++ }

    companion object {
        fun samplePrice(discount: Long = 0) = OrderPricePreview(4.71, 47100, 9420, 3000, 59520, discount, 59520 - discount)

        fun sampleOrder(status: String, version: Int = 1) = OrderResponse(
            orderId = "6d317915-008c-4e2c-8efa-5b2e5dbaa777", customerId = "c1",
            pickupAddress = "Nhà Hát Lớn, Quận 1", dropoffAddress = "Chợ Bến Thành", receiverPhone = "0912345678",
            distanceKm = 4.71, baseFee = 47100, surcharge = 9420, platformFee = 3000,
            discountPromotion = 0, discountPoints = 0, totalFee = 59520,
            status = status, matchingStatus = "SEARCHING", version = version,
            createdAt = "2026-11-04T03:00:00.123", updatedAt = null, refundStatus = "NOT_APPLICABLE"
        )

        fun sampleDelivery(status: String, driver: String? = "x") = DeliveryResponse(
            deliveryId = "d1", orderId = "6d317915-008c-4e2c-8efa-5b2e5dbaa777", customerId = "c1", driverId = driver,
            status = status, totalFee = 59520, version = 2,
            pickupAddress = "Nhà Hát Lớn, Quận 1", dropoffAddress = "Chợ Bến Thành",
            pickupLatitude = 10.7755, pickupLongitude = 106.6996, dropoffLatitude = 10.7725, dropoffLongitude = 106.6980,
            searchStartedAt = "2026-11-04T03:00:01", assignedAt = "2026-11-04T03:00:20"
        )
    }
}
