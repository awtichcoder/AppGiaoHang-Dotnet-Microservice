package com.deliveryapp.customer.data.dto

data class ComplaintAttachmentResponse(
    val id: String,
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long,
    val createdAt: String
)

data class ComplaintResponse(
    val id: String,
    val orderId: String,
    val reporterRole: String,
    val category: String,
    val description: String,
    val status: String,
    val adminNote: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val resolvedAt: String? = null,
    val attachments: List<ComplaintAttachmentResponse> = emptyList()
)
