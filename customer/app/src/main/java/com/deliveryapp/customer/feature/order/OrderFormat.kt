package com.deliveryapp.customer.feature.order

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Short order code shown everywhere (same as the driver app and admin web): #ABCD1234. */
fun orderCode(orderId: String): String = "#" + orderId.replace("-", "").take(8).uppercase(Locale.ROOT)

private val moneyFormat = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN"))
fun vnd(amount: Long): String = moneyFormat.format(amount) + "đ"

fun km(distance: Double): String = String.format(Locale.forLanguageTag("vi-VN"), "%.1f km", distance)

/** SQL Server timestamps come back without an offset; they are UTC. */
fun parseServerInstant(value: String?): Instant? {
    if (value.isNullOrBlank()) return null
    return runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(value).toInstant(ZoneOffset.UTC) }.getOrNull()
}

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val dateTimeFormat = DateTimeFormatter.ofPattern("HH:mm dd/MM")
fun shortTime(value: String?, zone: ZoneId = ZoneId.systemDefault()): String =
    parseServerInstant(value)?.atZone(zone)?.format(timeFormat) ?: "—"
fun shortDateTime(value: String?, zone: ZoneId = ZoneId.systemDefault()): String =
    parseServerInstant(value)?.atZone(zone)?.format(dateTimeFormat) ?: "—"

object OrderStatuses {
    const val PENDING = "PENDING"
    const val ASSIGNED = "ASSIGNED"
    const val PICKED_UP = "PICKED_UP"
    const val DELIVERING = "DELIVERING"
    const val COMPLETED = "COMPLETED"
    const val CANCELLED = "CANCELLED"
    const val NO_DRIVER_FOUND = "NO_DRIVER_FOUND"

    val final = setOf(COMPLETED, CANCELLED, NO_DRIVER_FOUND)
    /** Matches the backend OrderStateMachine.CanCustomerCancel. */
    val cancellable = setOf(PENDING, ASSIGNED)
    /** The backend only serves the driver's location in these states. */
    val trackable = setOf(ASSIGNED, PICKED_UP, DELIVERING)
    val reorderable = setOf(CANCELLED, NO_DRIVER_FOUND)

    fun label(status: String): String = when (status) {
        PENDING -> "Đang tìm tài xế"
        ASSIGNED -> "Tài xế đang đến lấy hàng"
        PICKED_UP -> "Tài xế đã lấy hàng"
        DELIVERING -> "Đang giao hàng"
        COMPLETED -> "Đã giao thành công"
        CANCELLED -> "Đã hủy"
        NO_DRIVER_FOUND -> "Không tìm thấy tài xế"
        else -> status
    }

    fun description(status: String): String = when (status) {
        PENDING -> "Hệ thống đang mời tài xế gần điểm lấy hàng (bán kính 3 km, rồi 5 km)."
        ASSIGNED -> "Tài xế đã nhận đơn và đang di chuyển tới điểm lấy hàng."
        PICKED_UP -> "Tài xế đã nhận hàng và chuẩn bị giao."
        DELIVERING -> "Tài xế đang trên đường tới người nhận."
        COMPLETED -> "Đơn đã giao xong. Điểm thưởng sẽ được cộng vào tài khoản của bạn."
        CANCELLED -> "Đơn đã được hủy. Mã ưu đãi và điểm đã giữ sẽ được hoàn lại."
        NO_DRIVER_FOUND -> "Hiện chưa có tài xế phù hợp gần điểm lấy hàng. Bạn có thể đặt lại đơn."
        else -> ""
    }

    fun isWarning(status: String) = status == CANCELLED || status == NO_DRIVER_FOUND
}
