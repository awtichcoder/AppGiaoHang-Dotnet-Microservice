package com.deliveryapp.shipper.data.model

import java.time.Instant
import kotlin.math.ceil

enum class Availability(val label: String) {
    OFFLINE("Đang nghỉ"), AVAILABLE("Sẵn sàng nhận đơn"), BUSY("Đang thực hiện chuyến")
}
enum class TripStatus(val label: String) {
    ASSIGNED("Đến điểm lấy hàng"), PICKED_UP("Đã lấy hàng"),
    DELIVERING("Đang giao hàng"), COMPLETED("Đã hoàn thành"), CANCELLED("Đã hủy"), UNKNOWN("Chưa xác định");
    val next: TripStatus? get() = when (this) {
        ASSIGNED -> PICKED_UP; PICKED_UP -> DELIVERING; DELIVERING -> COMPLETED; else -> null
    }
    val action: String? get() = when (this) {
        ASSIGNED -> "Xác nhận lấy hàng"; PICKED_UP -> "Bắt đầu giao hàng"
        DELIVERING -> "Hoàn thành giao hàng"; else -> null
    }
}
enum class OfferStatus { OFFERED, ACCEPTED, REJECTED, EXPIRED, CANCELLED, PROCESSED }
data class Driver(val userId: String, val fullName: String, val email: String, val phone: String?, val role: String)
data class Point(val latitude: Double, val longitude: Double)
data class Offer(
    val offerId: String, val deliveryId: String, val orderId: String,
    val pickupAddress: String, val dropoffAddress: String,
    val distanceToPickupKm: Double?, val incomeEstimate: Long?,
    val sentAt: Instant, val expiresAt: Instant, val status: OfferStatus = OfferStatus.OFFERED,
) {
    fun validAt(now: Instant) = status == OfferStatus.OFFERED && now < expiresAt
    fun remainingSeconds(now: Instant) = ceil((expiresAt.toEpochMilli() - now.toEpochMilli()) / 1000.0).toInt().coerceIn(0, 30)
}
data class Trip(
    val deliveryId: String, val orderId: String,
    val pickupAddress: String, val dropoffAddress: String, val status: TripStatus,
    val pickup: Point?, val dropoff: Point?, val receiverPhone: String? = null,
    val totalFee: Long? = null, val assignedAt: Instant? = null,
    val completedAt: Instant? = null, val cancelledAt: Instant? = null,
    /** Copied from the accepted offer; the delivery endpoints do not return them. */
    val distanceToPickupKm: Double? = null, val incomeEstimate: Long? = null,
) {
    val active get() = status.next != null
    val shortId get() = if (orderId.length > 12) orderId.take(8).uppercase() else orderId
    val displayTime get() = completedAt ?: cancelledAt ?: assignedAt
}
class RepositoryException(val code: String, override val message: String) : Exception(message)
