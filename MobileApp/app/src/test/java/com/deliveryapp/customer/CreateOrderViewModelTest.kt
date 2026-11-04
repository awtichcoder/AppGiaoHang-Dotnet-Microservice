package com.deliveryapp.customer

import com.deliveryapp.customer.core.network.ApiResult
import com.deliveryapp.customer.data.repository.GeoPoint
import com.deliveryapp.customer.data.repository.OrderDraft
import com.deliveryapp.customer.feature.order.CreateOrderViewModel
import com.deliveryapp.customer.feature.order.PointKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreateOrderViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val pickup = GeoPoint(10.7755, 106.6996)
    private val dropoff = GeoPoint(10.7725, 106.6980)
    private var ids = 0
    private fun viewModel(gateway: FakeOrderGateway, draft: OrderDraft? = null) =
        CreateOrderViewModel(gateway, draft, previewDebounceMs = 0, newRequestId = { "id-${++ids}" })

    private fun CreateOrderViewModel.fillValid() {
        onPointPicked(PointKind.PICKUP, pickup, "Nhà Hát Lớn, Quận 1")
        onPointPicked(PointKind.DROPOFF, dropoff, null)
        onAddressChange(PointKind.DROPOFF, "Chợ Bến Thành")
        onPhoneChange("0912 345 678")
    }

    @Test
    fun submit_withEmptyForm_showsFieldErrorsAndDoesNotCreate() = runTest {
        val gateway = FakeOrderGateway()
        val vm = viewModel(gateway)

        vm.submit()

        assertEquals("Chọn điểm lấy hàng trên bản đồ", vm.state.pickupError)
        assertEquals("Chọn điểm giao hàng trên bản đồ", vm.state.dropoffError)
        assertEquals("Nhập số điện thoại người nhận", vm.state.phoneError)
        assertTrue(gateway.createRequests.isEmpty())
    }

    @Test
    fun invalidPhone_andSamePoints_areRejected() = runTest {
        val gateway = FakeOrderGateway()
        val vm = viewModel(gateway)
        vm.onPointPicked(PointKind.PICKUP, pickup, "A")
        vm.onPointPicked(PointKind.DROPOFF, pickup, "B")
        vm.onPhoneChange("12345")

        vm.submit()

        assertEquals("Điểm giao phải khác điểm lấy hàng", vm.state.dropoffError)
        assertEquals("Số điện thoại gồm 10 số, bắt đầu bằng 0", vm.state.phoneError)
        assertTrue(gateway.createRequests.isEmpty())
    }

    @Test
    fun pickedPoint_fillsEmptyAddressOnly_andLoadsOptions() = runTest {
        val vm = viewModel(FakeOrderGateway())
        vm.onAddressChange(PointKind.PICKUP, "Số 7 Công Trường Lam Sơn")
        vm.onPointPicked(PointKind.PICKUP, pickup, "Geocoded")
        vm.onPointPicked(PointKind.DROPOFF, dropoff, "Chợ Bến Thành, Quận 1")

        assertEquals("Số 7 Công Trường Lam Sơn", vm.state.pickupAddress)
        assertEquals("Chợ Bến Thành, Quận 1", vm.state.dropoffAddress)
        assertEquals(115, vm.state.availablePoints)
        assertEquals(listOf("GIAM10"), vm.state.promotions.map { it.code })
    }

    @Test
    fun preview_combinesPromotionAndPoints_withPlaceholderTextFields() = runTest {
        val gateway = FakeOrderGateway()
        val vm = viewModel(gateway)
        vm.onPointPicked(PointKind.PICKUP, pickup, null)
        vm.onPointPicked(PointKind.DROPOFF, dropoff, null)
        vm.onPromotionToggle("GIAM10")
        vm.onPointsChange("5")

        assertEquals(5952L, vm.state.preview?.discountPromotion)
        assertEquals(5000L, vm.state.pointsDiscount)
        assertEquals(59520L - 5952 - 5000, vm.state.total)
        // Preview must not fail on half-typed fields: it always sends valid placeholders.
        val sent = gateway.previewRequests.last()
        assertEquals("0900000000", sent.receiverPhone)
        assertEquals("Điểm lấy hàng", sent.pickupAddress)
        assertEquals(0, sent.pointsToUse)
    }

    @Test
    fun invalidPromotion_showsReason_andStillShowsFeeWithoutIt() = runTest {
        val gateway = FakeOrderGateway().apply {
            previewResult = { request ->
                if (request.promotionCode != null) ApiResult.Error("PROMOTION_INVALID", "Mã khuyến mãi đang được giữ cho một đơn khác chưa hoàn tất của bạn.", 409)
                else ApiResult.Success(FakeOrderGateway.samplePrice())
            }
        }
        val vm = viewModel(gateway)
        vm.fillValid()
        vm.onPromotionChange("giam10")

        assertEquals("GIAM10", vm.state.promotionCode)
        assertEquals("Mã khuyến mãi đang được giữ cho một đơn khác chưa hoàn tất của bạn.", vm.state.promotionError)
        assertEquals(59520L, vm.state.total)
        vm.submit()
        assertTrue("an invalid code blocks the order", gateway.createRequests.isEmpty())
    }

    @Test
    fun morePointsThanUsable_isReported() = runTest {
        val vm = viewModel(FakeOrderGateway())
        vm.fillValid()
        vm.onPointsChange("500")

        assertEquals("Bạn chỉ có 115 điểm khả dụng", CreateOrderViewModel.validate(vm.state).pointsError)
        assertEquals("Chỉ dùng được tối đa 115 điểm cho đơn này.", vm.state.pointsError)
    }

    @Test
    fun submit_success_sendsNormalizedRequest_andReportsCreatedOrder() = runTest {
        val gateway = FakeOrderGateway()
        val vm = viewModel(gateway)
        vm.fillValid()
        vm.onPointsChange("5")

        vm.submit()

        val request = gateway.createRequests.single()
        assertEquals("0912345678", request.receiverPhone)
        assertEquals("Nhà Hát Lớn, Quận 1", request.pickupAddress)
        assertEquals(10.7755, request.pickupLocation.latitude, 0.0)
        assertEquals(5, request.pointsToUse)
        assertEquals("id-1", request.clientRequestId)
        assertEquals("6d317915-008c-4e2c-8efa-5b2e5dbaa777", vm.state.createdOrderId)
    }

    @Test
    fun retryAfterNetworkError_reusesRequestId_butEditingStartsANewOne() = runTest {
        val gateway = FakeOrderGateway().apply { createResult = ApiResult.Error("NETWORK", "Không thể kết nối máy chủ.") }
        val vm = viewModel(gateway)
        vm.fillValid()

        vm.submit()
        assertEquals("Không thể kết nối máy chủ.", vm.state.submitError)
        vm.submit()
        vm.onPhoneChange("0987654321")
        vm.submit()

        assertEquals(listOf("id-1", "id-1", "id-2"), gateway.createRequests.map { it.clientRequestId })
    }

    @Test
    fun draftFromReorder_prefillsTheForm() = runTest {
        val vm = viewModel(FakeOrderGateway(), OrderDraft("Nhà Hát Lớn", pickup, "Chợ Bến Thành", dropoff, "0912345678"))

        assertEquals(pickup, vm.state.pickup)
        assertEquals("Chợ Bến Thành", vm.state.dropoffAddress)
        assertNotNull(vm.state.preview)
        vm.submit()
        assertNull(vm.state.submitError)
    }

    @Test
    fun sessionExpiredOnCreate_isSurfaced() = runTest {
        val gateway = FakeOrderGateway().apply { createResult = ApiResult.Error("SESSION_EXPIRED", "Phiên đăng nhập đã hết hạn.", 401) }
        val vm = viewModel(gateway)
        vm.fillValid()
        vm.submit()
        assertTrue(vm.state.sessionExpired)
    }
}
