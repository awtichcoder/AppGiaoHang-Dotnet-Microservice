package com.deliveryapp.customer.feature.complaint

import com.deliveryapp.customer.feature.order.parseServerInstant
import java.time.Duration
import java.time.Instant

object ComplaintLimits {
    const val MAX_IMAGES = 3
    const val MAX_IMAGE_BYTES = 5 * 1024 * 1024
    const val MAX_DESCRIPTION = 1000
    val WINDOW: Duration = Duration.ofHours(24)
}

/** Only decides whether to show the button. The server makes the real 24-hour check. */
fun isWithinComplaintWindow(completedAt: String?, now: Instant): Boolean {
    val completed = parseServerInstant(completedAt) ?: return false
    return Duration.between(completed, now) <= ComplaintLimits.WINDOW
}

object ComplaintCategories {
    val all = listOf(
        "LATE_DELIVERY" to "Giao trễ",
        "WRONG_ITEM" to "Sai hàng",
        "DAMAGED_ITEM" to "Hàng hư hỏng",
        "DRIVER_BEHAVIOR" to "Thái độ tài xế",
        "OTHER" to "Khác"
    )

    fun label(code: String): String = all.firstOrNull { it.first == code }?.second ?: code
}

object ComplaintStatuses {
    const val RESOLVED = "RESOLVED"
    const val REJECTED = "REJECTED"

    fun label(status: String): String = when (status) {
        "OPEN" -> "Đã gửi, chờ xử lý"
        "IN_REVIEW" -> "Đang xem xét"
        RESOLVED -> "Đã giải quyết"
        REJECTED -> "Đã từ chối"
        else -> status
    }

    fun isWarning(status: String) = status == REJECTED
}

/** These errors cannot be fixed by editing the form, so the send button is disabled. */
val blockingComplaintErrors = setOf(
    "COMPLAINT_WINDOW_CLOSED",
    "COMPLAINT_ALREADY_EXISTS",
    "COMPLAINT_ORDER_NOT_COMPLETED",
    "COMPLAINT_FORBIDDEN",
    "ORDER_NOT_FOUND"
)
