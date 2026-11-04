package com.deliveryapp.shipper.data.repository

import com.deliveryapp.shipper.core.location.LocationReader
import com.deliveryapp.shipper.core.network.apiCall
import com.deliveryapp.shipper.core.storage.TokenPair
import com.deliveryapp.shipper.core.storage.TokenStore
import com.deliveryapp.shipper.data.api.*
import com.deliveryapp.shipper.data.model.*
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Talks to the real backend through the API Gateway.
 *
 * The backend has no driver "trip history" or "current trip" endpoint, so history only contains
 * trips finished in this app session and an active trip is not restored after the process dies.
 */
class LiveShipperRepository(
    private val tokens: TokenStore,
    private val identity: IdentityApi,
    private val driver: DriverApi,
    private val delivery: DeliveryApi,
    private val location: LocationReader,
    private val clock: Clock = Clock.systemUTC(),
) : AuthRepository, WorkRepository {
    private val mutex = Mutex()
    private var active: Trip? = null
    private val finished = mutableListOf<Trip>()

    override suspend fun login(email: String, password: String): Driver {
        val pair = call { identity.login(LoginRequest(email, password)) }
        tokens.save(TokenPair(pair.accessToken, pair.refreshToken))
        val me = try { call { identity.me() } } catch (e: Exception) { tokens.clear(); throw e }
        if (me.role == "DRIVER") {
            // The UI starts every session as OFFLINE; align the server with it (fails only while BUSY).
            runCatching { call { driver.availability(AvailabilityRequest(Availability.OFFLINE.name)) } }
                .onFailure { if (it is CancellationException) throw it }
        }
        mutex.withLock { active = null; finished.clear() }
        return me
    }

    override suspend fun logout() {
        mutex.withLock {
            if (active != null) throw RepositoryException("DRIVER_BUSY", "Hãy hoàn thành chuyến trước khi đăng xuất.")
        }
        val pair = tokens.current()
        if (pair != null) {
            // Best effort: a failed network call must not keep the user signed in on this device.
            runCatching { call { driver.availability(AvailabilityRequest(Availability.OFFLINE.name)) } }
            runCatching { call { identity.logout(RefreshRequest(pair.refreshToken)) } }
        }
        tokens.clear()
        mutex.withLock { finished.clear() }
    }

    override suspend fun availability(value: Availability) {
        if (value == Availability.BUSY) throw RepositoryException("DRIVER_BUSY", "Trạng thái bận do hệ thống tự cập nhật.")
        call { driver.availability(AvailabilityRequest(value.name)) }
        if (value == Availability.AVAILABLE) reportLocation()
    }

    override suspend fun offer(): Offer? =
        call { delivery.offers(page = 1, pageSize = 1) }.items.firstOrNull()?.toDomain()

    override suspend fun accept(offer: Offer): Trip {
        call { delivery.accept(offer.offerId) }
        val trip = call { delivery.detail(offer.orderId) }.toDomain()
        mutex.withLock { active = trip }
        return trip
    }

    override suspend fun reject(offer: Offer) {
        call { delivery.reject(offer.offerId, RejectRequest()) }
    }

    override suspend fun advance(trip: Trip, target: TripStatus): Trip {
        if (trip.status.next != target) {
            throw RepositoryException("INVALID_DELIVERY_TRANSITION", "Trạng thái chuyến đã thay đổi. Vui lòng kiểm tra lại.")
        }
        val response = when (target) {
            TripStatus.PICKED_UP -> {
                // The backend only accepts pickup with the current GPS within 200 m of the pickup point.
                val fix = location.readFresh() ?: throw RepositoryException("PICKUP_GPS_REQUIRED",
                    "Không lấy được vị trí hiện tại. Hãy bật GPS, cấp quyền vị trí rồi thử lại.")
                call { delivery.pickup(trip.deliveryId, PickupRequest(fix.point.latitude, fix.point.longitude)) }
            }
            TripStatus.DELIVERING -> call { delivery.start(trip.deliveryId) }
            TripStatus.COMPLETED -> call { delivery.complete(trip.deliveryId) }
            else -> throw RepositoryException("INVALID_DELIVERY_TRANSITION", "Thao tác không hợp lệ.")
        }
        // Older backends omit coordinates in the stage response; keep the ones we already have.
        val updated = response.toDomain().let { it.copy(pickup = it.pickup ?: trip.pickup, dropoff = it.dropoff ?: trip.dropoff) }
        mutex.withLock {
            if (updated.status == TripStatus.COMPLETED || updated.status == TripStatus.CANCELLED) {
                active = null
                finished.removeAll { it.deliveryId == updated.deliveryId }
                finished.add(0, updated)
            } else active = updated
        }
        return updated
    }

    override suspend fun history(): List<Trip> = mutex.withLock { finished.toList() }

    override suspend fun reportLocation(): Boolean {
        val fix = location.read() ?: return false
        call {
            driver.location(LocationRequest(
                latitude = fix.point.latitude,
                longitude = fix.point.longitude,
                accuracyM = fix.accuracyM.toDouble().coerceIn(0.0, 10_000.0),
                // The reader only returns fixes at most 60 s old; stamp with the send time so the
                // server's freshness window is measured from now rather than from a cached fix.
                recordedAt = clock.instant().toString(),
            ))
        }
        return true
    }

    private suspend fun <T> call(block: suspend () -> T): T = try {
        apiCall(block)
    } catch (e: RepositoryException) {
        throw when (e.code) {
            "HTTP_401" -> RepositoryException("SESSION_EXPIRED", "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.")
            "INVALID_CREDENTIALS" -> RepositoryException(e.code, "Email hoặc mật khẩu chưa đúng. Vui lòng thử lại.")
            "DRIVER_IS_BUSY" -> RepositoryException("DRIVER_BUSY", e.message)
            "PICKUP_TOO_FAR" -> RepositoryException(e.code, "Bạn đang ở xa điểm lấy hàng. Hãy đến trong phạm vi 200 m rồi xác nhận lại.")
            "PICKUP_GPS_REQUIRED" -> RepositoryException(e.code, "Không lấy được vị trí hiện tại. Hãy bật GPS, cấp quyền vị trí rồi thử lại.")
            else -> e
        }
    }
}
