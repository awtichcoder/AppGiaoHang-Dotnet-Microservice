package com.deliveryapp.shipper.data.repository

import com.deliveryapp.shipper.data.model.*

interface AuthRepository {
    suspend fun login(email: String, password: String): Driver
    suspend fun logout()
}
interface WorkRepository {
    suspend fun availability(value: Availability)
    suspend fun offer(): Offer?
    suspend fun accept(offer: Offer): Trip
    suspend fun reject(offer: Offer)
    suspend fun advance(trip: Trip, target: TripStatus): Trip
    suspend fun history(): List<Trip>
    /**
     * Sends the device position so the backend can match this driver (it ignores fixes older than 60 s).
     * Returns false when no position could be read. The mock has no server, so it is a no-op.
     */
    suspend fun reportLocation(): Boolean = true
}
enum class DemoScenario(val label: String) {
    NORMAL("Kết nối bình thường / lời mời mới"),
    EMPTY("Chưa có lời mời / lịch sử trống"),
    NETWORK_ERROR("Mất kết nối mạng"),
    CANCELLED("Lời mời bị hủy khi phản hồi"),
    PROCESSED("Lời mời đã được xử lý khi phản hồi")
}
interface DemoControls {
    fun scenario(value: DemoScenario)
}
