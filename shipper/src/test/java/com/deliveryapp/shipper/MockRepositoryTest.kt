package com.deliveryapp.shipper

import com.deliveryapp.shipper.data.model.*
import com.deliveryapp.shipper.data.repository.*
import java.time.*
import kotlinx.coroutines.async
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class MutableClock(var current: Instant = Instant.parse("2026-10-30T08:00:00Z")) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = current
}
class MockRepositoryTest {
    @Test fun fullLifecycleAndBusyGuard() = runTest {
        val repository = MockShipperRepository(latencyMs = 0)
        repository.availability(Availability.AVAILABLE)
        var trip = repository.accept(repository.offer()!!)
        assertEquals(TripStatus.ASSIGNED, trip.status)
        assertFails("DRIVER_BUSY") { repository.availability(Availability.OFFLINE) }
        assertFails("INVALID_DELIVERY_TRANSITION") { repository.advance(trip, TripStatus.COMPLETED) }
        trip = repository.advance(trip, TripStatus.PICKED_UP)
        trip = repository.advance(trip, TripStatus.DELIVERING)
        trip = repository.advance(trip, TripStatus.COMPLETED)
        assertNotNull(trip.completedAt)
        assertFails("INVALID_DELIVERY_TRANSITION") { repository.advance(trip, TripStatus.COMPLETED) }
        assertEquals(1, repository.history().count { it.deliveryId == trip.deliveryId })
        repository.availability(Availability.OFFLINE)
    }
    @Test fun expiredOfferCannotBeRevivedByReload() = runTest {
        val clock = MutableClock()
        val repository = MockShipperRepository(clock, 0)
        repository.availability(Availability.AVAILABLE)
        val offer = repository.offer()!!
        assertEquals(30, offer.remainingSeconds(clock.instant()))
        clock.current = clock.current.plusSeconds(31)
        assertEquals(offer.offerId, repository.offer()!!.offerId)
        assertFails("OFFER_EXPIRED") { repository.accept(offer) }
    }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun expiresDuringAcceptAndDoubleTapAreSafe() = runTest {
        val clock = MutableClock()
        val repository = MockShipperRepository(clock, 100)
        repository.availability(Availability.AVAILABLE)
        val offer = repository.offer()!!
        val first = async { runCatching { repository.accept(offer) } }
        runCurrent()
        clock.current = clock.current.plusSeconds(31)
        advanceUntilIdle()
        assertEquals("OFFER_EXPIRED", (first.await().exceptionOrNull() as RepositoryException).code)
        repository.scenario(DemoScenario.NORMAL)
        val fresh = repository.offer()!!
        val a = async { runCatching { repository.accept(fresh) } }
        val b = async { runCatching { repository.accept(fresh) } }
        advanceUntilIdle()
        assertEquals(1, listOf(a.await(), b.await()).count { it.isSuccess })
    }
    @Test fun rejectionAndServerConflicts() = runTest {
        val repository = MockShipperRepository(latencyMs = 0)
        repository.availability(Availability.AVAILABLE)
        val offer = repository.offer()!!
        repository.reject(offer)
        assertFails("OFFER_EXPIRED") { repository.accept(offer) }
        for ((scenario, error) in listOf(DemoScenario.CANCELLED to "OFFER_CANCELLED", DemoScenario.PROCESSED to "OFFER_NOT_AVAILABLE")) {
            repository.scenario(scenario)
            assertFails(error) { repository.accept(repository.offer()!!) }
        }
    }
    @Test fun networkRetryAndEmptyHistory() = runTest {
        val repository = MockShipperRepository(latencyMs = 0)
        repository.scenario(DemoScenario.NETWORK_ERROR)
        assertFails("NETWORK") { repository.history() }
        repository.scenario(DemoScenario.NORMAL)
        assertEquals(3, repository.history().size)
        repository.scenario(DemoScenario.EMPTY)
        assertTrue(repository.history().isEmpty())
    }
    @Test fun credentialsAndReceiverPrivacy() = runTest {
        val repository = MockShipperRepository(latencyMs = 0)
        assertFails("INVALID_CREDENTIALS") { repository.login("x", "wrong") }
        assertEquals("DRIVER", repository.login(MockShipperRepository.DEMO_EMAIL, MockShipperRepository.DEMO_PASSWORD).role)
        repository.availability(Availability.AVAILABLE)
        val offer = repository.offer()!!
        assertFalse(Offer::class.java.declaredFields.any { it.name == "receiverPhone" })
        val trip = repository.accept(offer)
        assertNotNull(trip.receiverPhone)
        assertFails("DRIVER_BUSY") { repository.logout() }
    }
    private suspend fun assertFails(code: String, block: suspend () -> Unit) {
        try { block(); fail("Expected $code") }
        catch (e: RepositoryException) { assertEquals(code, e.code) }
    }
}
