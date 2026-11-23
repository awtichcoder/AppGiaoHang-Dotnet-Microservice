package com.deliveryapp.shipper.data.repository

import com.deliveryapp.shipper.data.model.*
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One simulated driver session. No network requests, no fake JWT, no private APIs. */
class MockShipperRepository(
    private val clock: Clock = Clock.systemUTC(),
    private val latencyMs: Long = 450,
) : AuthRepository, WorkRepository, DemoControls {
    private val mutex = Mutex()
    private var driverAvailability = Availability.OFFLINE
    private var pending: Offer? = null
    private var active: Trip? = null
    private val completed = mutableListOf<Trip>()
    private var sequence = 0
    private var issued = false
    private var scenario = DemoScenario.NORMAL

    private suspend fun simulateNetwork() {
        delay(latencyMs)
        if (scenario == DemoScenario.NETWORK_ERROR) throw RepositoryException("NETWORK", "Không có kết nối. Kiểm tra mạng rồi thử lại.")
    }
    override fun scenario(value: DemoScenario) {
        scenario = value
        pending = null
        issued = false
    }
    override suspend fun login(email: String, password: String): Driver {
        simulateNetwork()
        if (email != DEMO_EMAIL || password != DEMO_PASSWORD) {
            throw RepositoryException("INVALID_CREDENTIALS", "Email hoặc mật khẩu chưa đúng. Vui lòng thử lại.")
        }
        return Driver("demo-driver", "Trần Minh Khoa", DEMO_EMAIL, "0901234567", "DRIVER")
    }
    override suspend fun logout() {
        mutex.withLock {
            if (active != null) throw RepositoryException("DRIVER_BUSY", "Hãy hoàn thành chuyến trước khi đăng xuất.")
            driverAvailability = Availability.OFFLINE
            pending = null
            issued = false
            completed.clear()
            scenario = DemoScenario.NORMAL
        }
    }
    override suspend fun availability(value: Availability) {
        simulateNetwork()
        mutex.withLock {
            if (active != null || value == Availability.BUSY) throw RepositoryException("DRIVER_BUSY", "Hoàn thành chuyến đang giao trước khi nghỉ.")
            driverAvailability = value
            if (value == Availability.OFFLINE) { pending = null; issued = false }
        }
    }
    override suspend fun offer(): Offer? {
        simulateNetwork()
        return mutex.withLock {
            if (driverAvailability != Availability.AVAILABLE || scenario == DemoScenario.EMPTY) return@withLock null
            if (!issued) {
                issued = true
                sequence++
                val now = clock.instant()
                pending = Offer(
                    offerId = "offer-$sequence", deliveryId = "delivery-$sequence", orderId = "DH-26" + sequence.toString().padStart(4, '0'),
                    pickupAddress = "72 Lê Thánh Tôn, Bến Nghé, Quận 1, TP.HCM",
                    dropoffAddress = "18 Phan Xích Long, Phường 2, Phú Nhuận, TP.HCM",
                    distanceToPickupKm = 1.2, incomeEstimate = 38000,
                    sentAt = now, expiresAt = now.plusSeconds(30),
                )
            }
            pending
        }
    }
    private fun validate(offer: Offer) {
        when (scenario) {
            DemoScenario.CANCELLED -> {
                pending = offer.copy(status = OfferStatus.CANCELLED)
                throw RepositoryException("OFFER_CANCELLED", "Khách hàng đã hủy lời mời này.")
            }
            DemoScenario.PROCESSED -> {
                pending = offer.copy(status = OfferStatus.PROCESSED)
                throw RepositoryException("OFFER_NOT_AVAILABLE", "Lời mời đã được xử lý và không còn hiệu lực.")
            }
            else -> Unit
        }
        if (pending?.offerId != offer.offerId || pending?.validAt(clock.instant()) != true) {
            throw RepositoryException("OFFER_EXPIRED", "Lời mời đã hết hạn. Hãy chờ lời mời tiếp theo.")
        }
    }
    override suspend fun accept(offer: Offer): Trip {
        simulateNetwork()
        return mutex.withLock {
            validate(offer) // Server authority simulated after delay, including expiry races.
            if (active != null || driverAvailability != Availability.AVAILABLE) throw RepositoryException("DRIVER_BUSY", "Bạn chưa sẵn sàng nhận chuyến mới.")
            pending = offer.copy(status = OfferStatus.ACCEPTED)
            driverAvailability = Availability.BUSY
            Trip(offer.deliveryId, offer.orderId, offer.pickupAddress, offer.dropoffAddress,
                TripStatus.ASSIGNED, Point(10.7794, 106.7021), Point(10.7987, 106.6896),
                receiverPhone = "0912345678", totalFee = 47500, assignedAt = clock.instant()).also { active = it }
        }
    }
    override suspend fun reject(offer: Offer) {
        simulateNetwork()
        mutex.withLock { validate(offer); pending = offer.copy(status = OfferStatus.REJECTED) }
    }
    override suspend fun advance(trip: Trip, target: TripStatus): Trip {
        simulateNetwork()
        return mutex.withLock {
            if (active?.deliveryId != trip.deliveryId || active?.status != trip.status || trip.status.next != target) {
                throw RepositoryException("INVALID_DELIVERY_TRANSITION", "Trạng thái chuyến đã thay đổi. Vui lòng kiểm tra lại.")
            }
            val updated = trip.copy(status = target, completedAt = if (target == TripStatus.COMPLETED) clock.instant() else null)
            active = updated
            if (target == TripStatus.COMPLETED) {
                completed.add(0, updated)
                active = null
                driverAvailability = Availability.AVAILABLE
            }
            updated
        }
    }
    override suspend fun history(): List<Trip> {
        simulateNetwork()
        return mutex.withLock {
            if (scenario == DemoScenario.EMPTY) emptyList() else completed.toList() + samples(clock.instant())
        }
    }
    private fun samples(now: Instant) = listOf(
        Trip("sample-1", "DH-260128", "24 Nguyễn Huệ, Quận 1, TP.HCM", "86 Võ Thị Sáu, Quận 3, TP.HCM",
            TripStatus.COMPLETED, null, null, totalFee = 42000, assignedAt = now.minusSeconds(7200), completedAt = now.minusSeconds(5400)),
        Trip("sample-2", "DH-260127", "112 Hai Bà Trưng, Quận 1, TP.HCM", "35 Nguyễn Văn Trỗi, Phú Nhuận, TP.HCM",
            TripStatus.COMPLETED, null, null, totalFee = 51000, assignedAt = now.minusSeconds(90000), completedAt = now.minusSeconds(88000)),
        Trip("sample-3", "DH-260126", "10 Pasteur, Quận 1, TP.HCM", "46 Cao Thắng, Quận 3, TP.HCM",
            TripStatus.CANCELLED, null, null, assignedAt = now.minusSeconds(97000), cancelledAt = now.minusSeconds(96000)),
    )
    companion object {
        const val DEMO_EMAIL = "driver@deliveryapp.local"
        const val DEMO_PASSWORD = "Driver123!"
    }
}
