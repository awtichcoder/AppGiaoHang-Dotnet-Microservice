package com.deliveryapp.customer.data.dto

data class LoyaltyAccountResponse(
    val customerId: String,
    val points: Int,
    val availablePoints: Int = 0,
    val tier: String,
    val recentTransactions: List<LoyaltyTransactionResponse>
)
data class LoyaltyTransactionResponse(
    val transactionId: String,
    val orderId: String?,
    val type: String,
    val pointsDelta: Int,
    val balanceAfter: Int,
    val createdAt: String
)
data class PreviewPointsRequest(val points: Int, val orderAmount: Long)
data class PointsPreviewResponse(
    val requestedPoints: Int,
    val usablePoints: Int,
    val availablePoints: Int,
    val discountAmount: Long
)
