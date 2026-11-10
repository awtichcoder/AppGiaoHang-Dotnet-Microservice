package com.deliveryapp.shipper

import com.deliveryapp.shipper.core.location.DeviceFix
import com.deliveryapp.shipper.core.location.LocationReader
import com.deliveryapp.shipper.core.storage.SettingsPreferences
import com.deliveryapp.shipper.data.model.Point
import com.deliveryapp.shipper.data.repository.MockShipperRepository
import com.deliveryapp.shipper.feature.delivery.LocationViewModel
import com.deliveryapp.shipper.feature.profile.SettingsViewModel
import com.deliveryapp.shipper.feature.work.WorkViewModel
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class StateRecoveryTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }

    @Test fun cancelledSessionCannotUnlockNewAction() = runTest(dispatcher) {
        val vm = WorkViewModel(MockShipperRepository(latencyMs = 500))
        vm.toggleAvailability()
        runCurrent()
        vm.reset()
        vm.toggleAvailability()
        runCurrent()
        assertTrue(vm.state.value.loading)
        assertNull(vm.state.value.offer)
        vm.reset()
        runCurrent()
        assertFalse(vm.state.value.loading)
    }

    @Test fun logoutCancelsPendingLocationRead() = runTest(dispatcher) {
        var cancelled = false
        val vm = LocationViewModel(LocationReader {
            try { awaitCancellation() } finally { cancelled = true }
        })
        vm.refresh()
        runCurrent()
        assertTrue(vm.state.value.loading)
        vm.reset()
        runCurrent()
        assertTrue(cancelled)
        assertFalse(vm.state.value.loading)
        assertNull(vm.state.value.fix)
    }

    @Test fun permissionDenialAndLogoutRemovePreviousCoordinates() = runTest(dispatcher) {
        val fix = DeviceFix(Point(10.7755, 106.6996), 5f, 1L)
        val vm = LocationViewModel(LocationReader { fix })
        vm.refresh(); runCurrent()
        assertEquals(fix, vm.state.value.fix)
        vm.denied()
        assertTrue(vm.state.value.denied)
        assertNull(vm.state.value.fix)
        vm.refresh(); runCurrent()
        vm.reset()
        assertNull(vm.state.value.fix)
        assertFalse(vm.state.value.denied)
    }

    @Test fun settingsWriteFailureIsVisibleAndCanBeRetried() = runTest(dispatcher) {
        val preferences = object : SettingsPreferences {
            override val largeText = MutableStateFlow(false)
            var fail = true
            override suspend fun largeText(value: Boolean) {
                if (fail) throw IOException("Disk full")
                largeText.value = value
            }
        }
        val vm = SettingsViewModel(preferences)
        vm.largeText(true); runCurrent()
        assertFalse(preferences.largeText.value)
        assertNotNull(vm.error.value)
        preferences.fail = false
        vm.largeText(true); runCurrent()
        assertTrue(preferences.largeText.value)
        assertNull(vm.error.value)
    }
}
