package com.deliveryapp.shipper

import com.deliveryapp.shipper.data.model.*
import com.deliveryapp.shipper.data.repository.WorkRepository
import com.deliveryapp.shipper.feature.work.WorkViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class WorkPollingTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }

    private class FakeLive(private val clock: MutableClock) : WorkRepository {
        var serverOffer: Offer? = null
        var gps = true
        var offerCalls = 0
        var locationCalls = 0
        override suspend fun availability(value: Availability) {}
        override suspend fun offer(): Offer? { offerCalls++; return serverOffer }
        override suspend fun accept(offer: Offer) = Trip(offer.deliveryId, offer.orderId, "A", "B", TripStatus.ASSIGNED, null, null)
        override suspend fun reject(offer: Offer) {}
        override suspend fun advance(trip: Trip, target: TripStatus) = trip.copy(status = target)
        override suspend fun history() = emptyList<Trip>()
        override suspend fun reportLocation(): Boolean { locationCalls++; return gps }
        fun newOffer(id: String) = Offer(id, "d-$id", "r-$id", "A", "B", 1.0, 1000, clock.instant(), clock.instant().plusSeconds(30))
    }

    @Test fun offerArrivingLaterIsPickedUpByPolling() = runTest(dispatcher) {
        val clock = MutableClock()
        val repo = FakeLive(clock)
        val vm = WorkViewModel(repo, clock, pollIntervalMs = 3_000, locationEveryPolls = 5)
        vm.toggleAvailability(); runCurrent()
        assertNull(vm.state.value.offer)
        assertEquals(1, repo.locationCalls)

        repo.serverOffer = repo.newOffer("o1")
        advanceTimeBy(3_100)
        assertEquals("o1", vm.state.value.offer?.offerId)
        assertTrue(vm.state.value.canRespond)

        // While an offer is open the server is not asked again.
        val calls = repo.offerCalls
        advanceTimeBy(6_000)
        assertEquals(calls, repo.offerCalls)
        vm.reset()
    }

    @Test fun missingGpsShowsWarningAndOfflineStopsPolling() = runTest(dispatcher) {
        val clock = MutableClock()
        val repo = FakeLive(clock).apply { gps = false }
        val vm = WorkViewModel(repo, clock, pollIntervalMs = 3_000, locationEveryPolls = 5)
        vm.toggleAvailability(); runCurrent()
        assertNotNull(vm.state.value.locationWarning)

        vm.toggleAvailability(); runCurrent()
        assertEquals(Availability.OFFLINE, vm.state.value.availability)
        assertNull(vm.state.value.locationWarning)
        val calls = repo.offerCalls
        advanceTimeBy(30_000)
        assertEquals(calls, repo.offerCalls)
    }

    @Test fun demoModeDoesNotPoll() = runTest(dispatcher) {
        val clock = MutableClock()
        val repo = FakeLive(clock)
        val vm = WorkViewModel(repo, clock)
        vm.toggleAvailability(); runCurrent()
        val calls = repo.offerCalls
        advanceTimeBy(30_000)
        assertEquals(calls, repo.offerCalls)
        assertEquals(0, repo.locationCalls)
        vm.reset()
    }
}
