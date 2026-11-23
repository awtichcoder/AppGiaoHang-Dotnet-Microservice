package com.deliveryapp.customer

import com.deliveryapp.customer.core.network.ApiResult
import com.deliveryapp.customer.data.repository.GeoPoint
import com.deliveryapp.customer.data.repository.OrderDraftHolder
import com.deliveryapp.customer.feature.order.OrderTrackingViewModel
import com.deliveryapp.customer.feature.order.orderCode
import com.deliveryapp.customer.feature.order.parseServerInstant
import com.deliveryapp.customer.feature.order.vnd
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrderTrackingViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val drafts = OrderDraftHolder()
    private fun viewModel(gateway: FakeOrderGateway) = OrderTrackingViewModel("o1", gateway, drafts)

    @Test
    fun searching_showsMatchingProgress_withoutDriverLocation() = runTest {
        val gateway = FakeOrderGateway()
        val vm = viewModel(gateway)

        vm.refresh()

        assertEquals("PENDING", vm.state.order?.status)
        assertEquals(3.0, vm.state.matching!!.currentRadiusKm, 0.0)
        assertNull(vm.state.delivery)
        assertEquals(0, gateway.locationCalls)
        assertTrue(vm.shouldPoll)
    }

    @Test
    fun assigned_loadsDriverLocation_andCoordinates() = runTest {
        val gateway = FakeOrderGateway().apply {
            orderResult = ApiResult.Success(FakeOrderGateway.sampleOrder("ASSIGNED"))
            deliveryResult = ApiResult.Success(FakeOrderGateway.sampleDelivery("ASSIGNED"))
        }
        val vm = viewModel(gateway)

        vm.refresh()

        assertEquals(10.7790, vm.state.driverLocation!!.latitude, 0.0)
        assertNull(vm.state.matching)
        assertNull(vm.state.locationNote)
    }

    @Test
    fun staleDriverLocation_showsNote_insteadOfOldPosition() = runTest {
        val gateway = FakeOrderGateway().apply {
            orderResult = ApiResult.Success(FakeOrderGateway.sampleOrder("DELIVERING"))
            deliveryResult = ApiResult.Success(FakeOrderGateway.sampleDelivery("DELIVERING"))
            locationResult = ApiResult.Error("DRIVER_LOCATION_STALE", "Vị trí tài xế đã quá cũ.", 404)
        }
        val vm = viewModel(gateway)

        vm.refresh()

        assertNull(vm.state.driverLocation)
        assertEquals("Vị trí tài xế tạm thời chưa cập nhật (mất tín hiệu GPS).", vm.state.locationNote)
    }

    @Test
    fun finalStatus_stopsPolling_andSkipsLocation() = runTest {
        val gateway = FakeOrderGateway().apply {
            orderResult = ApiResult.Success(FakeOrderGateway.sampleOrder("COMPLETED"))
            deliveryResult = ApiResult.Success(FakeOrderGateway.sampleDelivery("COMPLETED"))
        }
        val vm = viewModel(gateway)

        vm.refresh()

        assertFalse(vm.shouldPoll)
        assertEquals(0, gateway.locationCalls)
    }

    @Test
    fun transientError_keepsLastData_andShowsWarning() = runTest {
        val gateway = FakeOrderGateway()
        val vm = viewModel(gateway)
        vm.refresh()
        gateway.orderResult = ApiResult.Error("NETWORK", "Không thể kết nối máy chủ.")

        vm.refresh()

        assertNotNull(vm.state.order)
        assertNull(vm.state.errorMessage)
        assertEquals("Mất kết nối, đang thử lại…", vm.state.refreshWarning)
    }

    @Test
    fun firstLoadError_isShownAsScreenError() = runTest {
        val gateway = FakeOrderGateway().apply { orderResult = ApiResult.Error("HTTP_404", "Không tìm thấy đơn hàng.", 404) }
        val vm = viewModel(gateway)

        vm.refresh()

        assertEquals("Không tìm thấy đơn hàng.", vm.state.errorMessage)
    }

    @Test
    fun cancel_sendsCurrentVersion_andShowsCancelledOrder() = runTest {
        val gateway = FakeOrderGateway().apply { orderResult = ApiResult.Success(FakeOrderGateway.sampleOrder("ASSIGNED", version = 3)) }
        val vm = viewModel(gateway)
        vm.refresh()
        gateway.orderResult = ApiResult.Success(FakeOrderGateway.sampleOrder("CANCELLED", version = 4))

        vm.askCancel()
        vm.cancel()

        assertEquals(listOf("o1" to 3), gateway.cancelled)
        assertEquals("CANCELLED", vm.state.order?.status)
        assertFalse(vm.state.confirmCancel)
    }

    @Test
    fun cancelAfterPickup_explainsWhy() = runTest {
        val gateway = FakeOrderGateway().apply {
            orderResult = ApiResult.Success(FakeOrderGateway.sampleOrder("ASSIGNED"))
            cancelResult = ApiResult.Error("ORDER_CANNOT_CANCEL", "Không thể hủy đơn ở trạng thái PICKED_UP.", 409)
        }
        val vm = viewModel(gateway)
        vm.refresh()

        vm.cancel()

        assertEquals("Không thể hủy: trạng thái đơn vừa thay đổi (tài xế có thể đã lấy hàng).", vm.state.cancelError)
    }

    @Test
    fun reorder_putsRouteWithCoordinatesInTheDraft() = runTest {
        val gateway = FakeOrderGateway().apply {
            orderResult = ApiResult.Success(FakeOrderGateway.sampleOrder("NO_DRIVER_FOUND"))
            deliveryResult = ApiResult.Success(FakeOrderGateway.sampleDelivery("NO_DRIVER_FOUND", driver = null))
        }
        val vm = viewModel(gateway)
        vm.refresh()

        vm.prepareReorder()

        val draft = drafts.take()!!
        assertEquals(GeoPoint(10.7755, 106.6996), draft.pickup)
        assertEquals("Chợ Bến Thành", draft.dropoffAddress)
        assertEquals("0912345678", draft.receiverPhone)
        assertNull("draft is consumed once", drafts.take())
    }

    @Test
    fun formatting_matchesTheOtherApps() {
        assertEquals("#6D317915", orderCode("6d317915-008c-4e2c-8efa-5b2e5dbaa777"))
        assertEquals("59.520đ", vnd(59520))
        assertEquals(Instant.parse("2026-11-04T03:00:00.123Z"), parseServerInstant("2026-11-04T03:00:00.123"))
        assertEquals(Instant.parse("2026-11-04T03:00:00Z"), parseServerInstant("2026-11-04T03:00:00Z"))
        assertNull(parseServerInstant(null))
    }
}
