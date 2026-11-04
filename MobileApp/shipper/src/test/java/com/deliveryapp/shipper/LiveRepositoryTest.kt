package com.deliveryapp.shipper

import com.deliveryapp.shipper.core.location.DeviceFix
import com.deliveryapp.shipper.core.network.NetworkFactory
import com.deliveryapp.shipper.core.storage.TokenPair
import com.deliveryapp.shipper.core.storage.TokenStore
import com.deliveryapp.shipper.data.api.*
import com.deliveryapp.shipper.data.model.*
import com.deliveryapp.shipper.data.repository.LiveShipperRepository
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.*
import org.junit.Assert.*

/** Response bodies mirror what the real backend returned through the Gateway. */
class LiveRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var tokens: MemoryTokens
    private var fix: DeviceFix? = DeviceFix(Point(10.7755, 106.6996), 8f, 0)
    private lateinit var repo: LiveShipperRepository

    private val tokenJson = """{"accessToken":"access","refreshToken":"refresh","expiresAt":"2026-11-03T17:30:30.3988987Z"}"""
    private val meJson = """{"userId":"1b53e8f5-82dc-4235-a5f9-2af4de2c9f3d","fullName":"Tài xế phát triển","email":"driver@deliveryapp.local","phone":"0911111111","role":"DRIVER"}"""
    private val profileJson = """{"driverId":"1b53e8f5-82dc-4235-a5f9-2af4de2c9f3d","availabilityStatus":"OFFLINE","activeDeliveryId":null,"updatedAt":"2026-11-03T16:30:33.9575283Z"}"""
    // Timestamps read back from SQL Server have no offset suffix.
    private val offersJson = """{"page":1,"pageSize":1,"totalItems":1,"items":[{"offerId":"o1","deliveryId":"d1","orderId":"r1",
        "pickupAddress":"A","dropoffAddress":"B","distanceToPickupKm":1.25,"radiusKm":3,"status":"OFFERED",
        "expiresAt":"2026-11-03T16:31:04.123","sentAt":"2026-11-03T16:30:34.123","incomeEstimate":38000}]}"""
    private fun deliveryJson(status: String) = """{"deliveryId":"d1","orderId":"r1","customerId":"c1","driverId":"x",
        "pickupAddress":"A","dropoffAddress":"B","receiverPhone":"0912345678","status":"$status","currentRadiusKm":3,
        "searchStartedAt":"2026-11-03T16:30:00","version":2,"totalFee":47500,"assignedAt":"2026-11-03T16:30:40.5",
        "completedAt":${if (status == "COMPLETED") "\"2026-11-03T16:50:00Z\"" else "null"},"cancelledAt":null}"""

    @Before fun before() {
        server = MockWebServer().apply { start() }
        tokens = MemoryTokens(null)
        val retrofit = NetworkFactory.create(tokens, server.url("/").toString())
        repo = LiveShipperRepository(tokens, retrofit.create(IdentityApi::class.java),
            retrofit.create(DriverApi::class.java), retrofit.create(DeliveryApi::class.java), { fix })
    }
    @After fun after() { server.shutdown() }

    @Test fun parsesTimestampsWithAndWithoutOffset() {
        assertEquals(Instant.parse("2026-11-03T16:30:33.9575283Z"), parseServerInstant("2026-11-03T16:30:33.9575283Z"))
        assertEquals(Instant.parse("2026-11-03T16:31:04.123Z"), parseServerInstant("2026-11-03T16:31:04.123"))
    }

    @Test fun loginSavesTokensLoadsProfileAndGoesOffline() = runTest {
        server.enqueue(MockResponse().setBody(tokenJson))
        server.enqueue(MockResponse().setBody(meJson))
        server.enqueue(MockResponse().setBody(profileJson))
        val driver = repo.login("driver@deliveryapp.local", "Driver123!")
        assertEquals("DRIVER", driver.role)
        assertEquals(TokenPair("access", "refresh"), tokens.current())
        assertEquals("/api/identity/login", next().path)
        assertEquals("Bearer access", next().getHeader("Authorization"))
        val offline = next()
        assertEquals("PATCH", offline.method)
        assertTrue(offline.body.readUtf8().contains("\"OFFLINE\""))
    }

    @Test fun wrongPasswordKeepsBackendCodeAndStoresNothing() = runTest {
        server.enqueue(MockResponse().setResponseCode(401)
            .setBody("""{"code":"INVALID_CREDENTIALS","message":"Email hoặc mật khẩu không đúng.","details":null}"""))
        val error = runCatching { repo.login("driver@deliveryapp.local", "wrong") }.exceptionOrNull() as RepositoryException
        assertEquals("INVALID_CREDENTIALS", error.code)
        assertNull(tokens.current())
    }

    @Test fun goingOnlineReportsLocation() = runTest {
        tokens.save(TokenPair("access", "refresh"))
        server.enqueue(MockResponse().setBody(profileJson.replace("OFFLINE", "AVAILABLE")))
        server.enqueue(MockResponse().setBody("""{"acceptedAt":"2026-11-03T16:30:34Z"}"""))
        repo.availability(Availability.AVAILABLE)
        assertEquals("/api/driver/me/availability", next().path)
        val location = next()
        assertEquals("PUT", location.method)
        assertEquals("/api/driver/me/location", location.path)
        assertTrue(location.body.readUtf8().contains("10.7755"))
    }

    @Test fun noGpsFixIsReportedAsFalseWithoutCallingServer() = runTest {
        fix = null
        assertFalse(repo.reportLocation())
        assertEquals(0, server.requestCount)
    }

    @Test fun offerAcceptAndFullTripFlow() = runTest {
        tokens.save(TokenPair("access", "refresh"))
        server.enqueue(MockResponse().setBody(offersJson))
        val offer = repo.offer()!!
        assertEquals(OfferStatus.OFFERED, offer.status)
        assertEquals(Instant.parse("2026-11-03T16:31:04.123Z"), offer.expiresAt)
        assertEquals("/api/delivery/offers/me?page=1&pageSize=1", next().path)

        server.enqueue(MockResponse().setBody("""{"offerId":"o1","deliveryId":"d1","status":"ACCEPTED"}"""))
        server.enqueue(MockResponse().setBody(deliveryJson("ASSIGNED")))
        var trip = repo.accept(offer)
        assertEquals(TripStatus.ASSIGNED, trip.status)
        assertEquals("/api/delivery/offers/o1/accept", next().path)
        assertEquals("/api/delivery/order/r1", next().path)

        for ((target, status, path) in listOf(
            Triple(TripStatus.PICKED_UP, "PICKED_UP", "/api/delivery/d1/pickup"),
            Triple(TripStatus.DELIVERING, "DELIVERING", "/api/delivery/d1/start"),
            Triple(TripStatus.COMPLETED, "COMPLETED", "/api/delivery/d1/complete"),
        )) {
            server.enqueue(MockResponse().setBody(deliveryJson(status)))
            trip = repo.advance(trip, target)
            assertEquals(target, trip.status)
            val request = next()
            assertEquals(path, request.path)
            if (target == TripStatus.PICKED_UP) {
                assertEquals("""{"gpsLatitude":10.7755,"gpsLongitude":106.6996}""", request.body.readUtf8())
            }
        }
        assertEquals(listOf("d1"), repo.history().map { it.deliveryId })
    }

    @Test fun takenOfferMapsToNotAvailable() = runTest {
        tokens.save(TokenPair("access", "refresh"))
        server.enqueue(MockResponse().setBody(offersJson))
        val offer = repo.offer()!!
        next()
        server.enqueue(MockResponse().setResponseCode(409)
            .setBody("""{"code":"OFFER_NOT_AVAILABLE","message":"Offer đã hết hạn hoặc đã có tài xế khác nhận.","details":null}"""))
        val error = runCatching { repo.accept(offer) }.exceptionOrNull() as RepositoryException
        assertEquals("OFFER_NOT_AVAILABLE", error.code)
    }

    private suspend fun acceptedTrip(): Trip {
        tokens.save(TokenPair("access", "refresh"))
        server.enqueue(MockResponse().setBody(offersJson))
        server.enqueue(MockResponse().setBody("""{"offerId":"o1","deliveryId":"d1","status":"ACCEPTED"}"""))
        server.enqueue(MockResponse().setBody(deliveryJson("ASSIGNED")))
        val trip = repo.accept(repo.offer()!!)
        repeat(3) { next() }
        return trip
    }

    @Test fun pickupWithoutGpsFailsLocallyWithoutCallingServer() = runTest {
        val trip = acceptedTrip()
        fix = null
        val error = runCatching { repo.advance(trip, TripStatus.PICKED_UP) }.exceptionOrNull() as RepositoryException
        assertEquals("PICKUP_GPS_REQUIRED", error.code)
        assertEquals(3, server.requestCount)
    }

    @Test fun pickupSendsAFreshFixNotTheCachedOne() = runTest {
        // A cached fix can be up to 60 s old, i.e. from before the driver reached the pickup point.
        val reader = object : com.deliveryapp.shipper.core.location.LocationReader {
            override suspend fun read() = DeviceFix(Point(10.7845, 106.6996), 8f, 0)
            override suspend fun readFresh() = DeviceFix(Point(10.7755, 106.6996), 5f, 0)
        }
        val retrofit = NetworkFactory.create(tokens, server.url("/").toString())
        repo = LiveShipperRepository(tokens, retrofit.create(IdentityApi::class.java),
            retrofit.create(DriverApi::class.java), retrofit.create(DeliveryApi::class.java), reader)
        val trip = acceptedTrip()
        server.enqueue(MockResponse().setBody(deliveryJson("PICKED_UP")))
        repo.advance(trip, TripStatus.PICKED_UP)
        assertEquals("""{"gpsLatitude":10.7755,"gpsLongitude":106.6996}""", next().body.readUtf8())
    }

    @Test fun pickupTooFarMapsToReadableMessage() = runTest {
        val trip = acceptedTrip()
        server.enqueue(MockResponse().setResponseCode(409)
            .setBody("""{"code":"PICKUP_TOO_FAR","message":"Bạn cần ở trong phạm vi 200m của điểm lấy hàng.","details":null}"""))
        val error = runCatching { repo.advance(trip, TripStatus.PICKED_UP) }.exceptionOrNull() as RepositoryException
        assertEquals("PICKUP_TOO_FAR", error.code)
        assertTrue(error.message!!.contains("200 m"))
    }

    @Test fun logoutIsBlockedDuringActiveTrip() = runTest {
        tokens.save(TokenPair("access", "refresh"))
        server.enqueue(MockResponse().setBody(offersJson))
        server.enqueue(MockResponse().setBody("""{"offerId":"o1","deliveryId":"d1","status":"ACCEPTED"}"""))
        server.enqueue(MockResponse().setBody(deliveryJson("ASSIGNED")))
        repo.accept(repo.offer()!!)
        val error = runCatching { repo.logout() }.exceptionOrNull() as RepositoryException
        assertEquals("DRIVER_BUSY", error.code)
        assertNotNull(tokens.current())
    }

    private fun next(): RecordedRequest = server.takeRequest(2, TimeUnit.SECONDS)!!

    private class MemoryTokens(@Volatile var value: TokenPair?) : TokenStore {
        override suspend fun current() = value
        override suspend fun save(pair: TokenPair) { value = pair }
        override suspend fun clear() { value = null }
        override suspend fun replaceIfCurrent(expected: TokenPair, replacement: TokenPair?): Boolean = synchronized(this) {
            if (value != expected) false else { value = replacement; true }
        }
    }
}
