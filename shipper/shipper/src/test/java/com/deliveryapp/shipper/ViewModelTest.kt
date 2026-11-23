package com.deliveryapp.shipper

import com.deliveryapp.shipper.data.model.*
import com.deliveryapp.shipper.data.repository.*
import com.deliveryapp.shipper.feature.auth.AuthViewModel
import com.deliveryapp.shipper.feature.work.WorkViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }
    @Test fun validationAndDriverRoleEnforced() = runTest(dispatcher) {
        val fake = object : AuthRepository {
            var loggedOut = false
            override suspend fun login(email: String, password: String) = Driver("x", "Customer", email, null, "CUSTOMER")
            override suspend fun logout() { loggedOut = true }
        }
        val vm = AuthViewModel(fake)
        vm.login()
        assertNotNull(vm.state.value.emailError)
        vm.email("customer@example.com"); vm.password("password")
        vm.login(); runCurrent()
        assertNull(vm.state.value.user)
        assertTrue(fake.loggedOut)
        assertTrue(vm.state.value.error!!.contains("quyền tài xế"))
    }
    @Test fun workLoadingGuardCountdownAndTransitions() = runTest(dispatcher) {
        val clock = MutableClock()
        val repository = MockShipperRepository(clock, 0)
        val vm = WorkViewModel(repository, clock)
        vm.toggleAvailability(); vm.toggleAvailability()
        runCurrent()
        assertEquals(Availability.AVAILABLE, vm.state.value.availability)
        assertEquals(30, vm.state.value.seconds)
        vm.accept(); vm.accept(); runCurrent()
        assertEquals(Availability.BUSY, vm.state.value.availability)
        vm.advance(TripStatus.DELIVERING); runCurrent()
        assertEquals(TripStatus.ASSIGNED, vm.state.value.active!!.status)
        assertNotNull(vm.state.value.error)
        vm.advance(TripStatus.ASSIGNED); runCurrent()
        vm.advance(TripStatus.PICKED_UP); runCurrent()
        vm.advance(TripStatus.DELIVERING); runCurrent()
        assertNull(vm.state.value.active)
        assertEquals(TripStatus.COMPLETED, vm.state.value.completed!!.status)
        vm.reset()
    }
    @Test fun countdownUsesAbsoluteDeadlineAndStopsAfterExpiry() = runTest(dispatcher) {
        val clock = MutableClock()
        val vm = WorkViewModel(MockShipperRepository(clock, 0), clock)
        vm.toggleAvailability(); runCurrent()
        clock.current = clock.current.plusSeconds(31)
        vm.tick()
        assertFalse(vm.state.value.canRespond)
        assertEquals(OfferStatus.EXPIRED, vm.state.value.offer!!.status)
        vm.accept(); runCurrent()
        assertNull(vm.state.value.active)
        vm.reset()
    }
}
