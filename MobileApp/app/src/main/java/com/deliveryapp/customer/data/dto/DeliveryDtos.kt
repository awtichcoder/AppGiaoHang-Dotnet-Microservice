package com.deliveryapp.customer.data.dto

data class MatchingStatusResponse(
    val orderId: String,
    val status: String,
    val searchStatus: String,
    val currentRadiusKm: Double,
    val invitedDrivers: Int,
    val attemptCount: Int,
    val candidateCount: Int,
    val remainingCandidateCount: Int,
    val searchStartedAt: String,
    val searchEndedAt: String?
)
data class DeliveryResponse(
    val deliveryId: String,
    val orderId: String,
    val customerId: String,
    val driverId: String?,
    val status: String,
    val totalFee: Long,
    val version: Int,
    val pickupAddress: String? = null,
    val dropoffAddress: String? = null,
    val receiverPhone: String? = null,
    val pickupLatitude: Double? = null,
    val pickupLongitude: Double? = null,
    val dropoffLatitude: Double? = null,
    val dropoffLongitude: Double? = null,
    val currentRadiusKm: Double? = null,
    val searchStartedAt: String? = null,
    val searchEndedAt: String? = null,
    val assignedAt: String? = null,
    val pickedUpAt: String? = null,
    val deliveringAt: String? = null,
    val completedAt: String? = null,
    val cancelledAt: String? = null
)
data class DriverLocationResponse(
    val deliveryId: String,
    val driverId: String,
    val latitude: Double,
    val longitude: Double,
    val recordedAt: String,
    val accuracyM: Double?
)
