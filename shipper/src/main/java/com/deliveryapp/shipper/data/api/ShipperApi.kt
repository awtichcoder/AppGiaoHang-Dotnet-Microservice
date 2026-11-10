package com.deliveryapp.shipper.data.api

import com.deliveryapp.shipper.data.model.*
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import retrofit2.Call
import retrofit2.http.*

/**
 * Backend timestamps are UTC. Values written in the same request carry a `Z` suffix, but values
 * read back from SQL Server (datetime2) are serialized without an offset, e.g. `2026-11-03T16:30:33.95`.
 */
fun parseServerInstant(value: String): Instant =
    runCatching { Instant.parse(value) }.getOrElse { LocalDateTime.parse(value).toInstant(ZoneOffset.UTC) }

data class LoginRequest(val email: String, val password: String)
data class RefreshRequest(val refreshToken: String)
data class TokenResponse(val accessToken: String, val refreshToken: String, val expiresAt: String)
data class LogoutResponse(val success: Boolean, val revokedAt: String)
data class AvailabilityRequest(val availabilityStatus: String)
data class DriverProfileResponse(val driverId: String, val availabilityStatus: String, val activeDeliveryId: String?, val updatedAt: String)
data class LocationRequest(val latitude: Double, val longitude: Double, val accuracyM: Double, val recordedAt: String)
data class LocationResponse(val acceptedAt: String)
data class RejectRequest(val reasonCode: String = "DRIVER_DECLINED")
data class RejectResponse(val offerId: String, val offerStatus: String, val reasonCode: String)
data class PickupRequest(val gpsLatitude: Double? = null, val gpsLongitude: Double? = null)
data class AcceptResponse(val offerId: String, val deliveryId: String, val status: String)
data class PageResponse<T>(val page: Int, val pageSize: Int, val totalItems: Int, val items: List<T>)
data class OfferResponse(
    val offerId: String, val deliveryId: String, val orderId: String,
    val pickupAddress: String, val dropoffAddress: String, val distanceToPickupKm: Double?,
    val incomeEstimate: Long?, val status: String, val sentAt: String, val expiresAt: String,
) {
    fun toDomain() = Offer(offerId, deliveryId, orderId, pickupAddress, dropoffAddress,
        distanceToPickupKm, incomeEstimate, parseServerInstant(sentAt), parseServerInstant(expiresAt),
        runCatching { OfferStatus.valueOf(status) }.getOrDefault(OfferStatus.PROCESSED))
}
data class DeliveryResponse(
    val deliveryId: String, val orderId: String, val pickupAddress: String, val dropoffAddress: String,
    val status: String, val receiverPhone: String?, val totalFee: Long?,
    val pickupLatitude: Double?, val pickupLongitude: Double?,
    val dropoffLatitude: Double?, val dropoffLongitude: Double?,
    val assignedAt: String?, val completedAt: String?, val cancelledAt: String?,
) {
    fun toDomain() = Trip(deliveryId, orderId, pickupAddress, dropoffAddress,
        runCatching { TripStatus.valueOf(status) }.getOrDefault(TripStatus.UNKNOWN),
        if (pickupLatitude != null && pickupLongitude != null) Point(pickupLatitude, pickupLongitude) else null,
        if (dropoffLatitude != null && dropoffLongitude != null) Point(dropoffLatitude, dropoffLongitude) else null,
        receiverPhone, totalFee, assignedAt?.let(::parseServerInstant),
        completedAt?.let(::parseServerInstant), cancelledAt?.let(::parseServerInstant))
}
interface IdentityApi {
    @POST("api/identity/login") suspend fun login(@Body request: LoginRequest): TokenResponse
    @GET("api/identity/me") suspend fun me(): Driver
    @POST("api/identity/logout") suspend fun logout(@Body request: RefreshRequest): LogoutResponse
}
interface RefreshApi {
    @POST("api/identity/refresh") fun refresh(@Body request: RefreshRequest): Call<TokenResponse>
}
interface DriverApi {
    @PATCH("api/driver/me/availability") suspend fun availability(@Body request: AvailabilityRequest): DriverProfileResponse
    @PUT("api/driver/me/location") suspend fun location(@Body request: LocationRequest): LocationResponse
}
interface DeliveryApi {
    @GET("api/delivery/offers/me") suspend fun offers(@Query("page") page: Int = 1, @Query("pageSize") pageSize: Int = 20): PageResponse<OfferResponse>
    @POST("api/delivery/offers/{offerId}/accept") suspend fun accept(@Path("offerId") id: String): AcceptResponse
    @POST("api/delivery/offers/{offerId}/reject") suspend fun reject(@Path("offerId") id: String, @Body request: RejectRequest): RejectResponse
    @GET("api/delivery/order/{orderId}") suspend fun detail(@Path("orderId") id: String): DeliveryResponse
    @POST("api/delivery/{id}/pickup") suspend fun pickup(@Path("id") id: String, @Body request: PickupRequest): DeliveryResponse
    @POST("api/delivery/{id}/start") suspend fun start(@Path("id") id: String): DeliveryResponse
    @POST("api/delivery/{id}/complete") suspend fun complete(@Path("id") id: String): DeliveryResponse
}
// No DRIVER history/current-trip endpoint has been verified. Do not invent one here.
