package com.deliveryapp.shipper.data.model

import java.time.Duration
import java.time.Instant

object ComplaintLimits {
    const val MAX_IMAGES = 3
    const val MAX_IMAGE_BYTES = 5 * 1024 * 1024
    const val MAX_DESCRIPTION = 1000
    val WINDOW: Duration = Duration.ofHours(24)
}

enum class ComplaintCategory(val label: String) {
    LATE_DELIVERY("Giao trễ"), WRONG_ITEM("Sai hàng"), DAMAGED_ITEM("Hàng hư hỏng"),
    DRIVER_BEHAVIOR("Thái độ tài xế"), OTHER("Khác");

    companion object { fun labelOf(code: String) = entries.firstOrNull { it.name == code }?.label ?: code }
}

object ComplaintStatusLabels {
    fun label(status: String) = when (status) {
        "OPEN" -> "Đã gửi, chờ xử lý"
        "IN_REVIEW" -> "Đang xem xét"
        "RESOLVED" -> "Đã giải quyết"
        "REJECTED" -> "Đã từ chối"
        else -> status
    }
    fun isWarning(status: String) = status == "REJECTED"
}

data class ComplaintAttachment(val id: String, val fileName: String, val contentType: String, val sizeBytes: Long)

data class Complaint(
    val id: String, val orderId: String, val category: String, val description: String, val status: String,
    val adminNote: String?, val createdAt: Instant, val updatedAt: Instant, val attachments: List<ComplaintAttachment>,
)

/** Image already read into memory, so OkHttp can resend it after a token refresh. */
class ComplaintImage(val fileName: String, val contentType: String, val bytes: ByteArray)

data class ComplaintPage(val page: Int, val totalItems: Int, val items: List<Complaint>)

/** Only decides whether to show the button. The server makes the real 24-hour check. */
fun isWithinComplaintWindow(completedAt: Instant?, now: Instant): Boolean =
    completedAt != null && Duration.between(completedAt, now) <= ComplaintLimits.WINDOW

/** These errors cannot be fixed by editing the form, so the send button is disabled. */
val blockingComplaintErrors = setOf(
    "COMPLAINT_WINDOW_CLOSED", "COMPLAINT_ALREADY_EXISTS", "COMPLAINT_ORDER_NOT_COMPLETED",
    "COMPLAINT_FORBIDDEN", "ORDER_NOT_FOUND",
)
