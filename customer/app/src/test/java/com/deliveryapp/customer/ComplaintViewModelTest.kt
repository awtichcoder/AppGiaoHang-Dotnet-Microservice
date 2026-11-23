package com.deliveryapp.customer

import com.deliveryapp.customer.core.network.ApiResult
import com.deliveryapp.customer.data.api.ComplaintApi
import com.deliveryapp.customer.data.dto.ComplaintResponse
import com.deliveryapp.customer.data.dto.PagedResponse
import com.deliveryapp.customer.data.repository.ComplaintGateway
import com.deliveryapp.customer.data.repository.ComplaintImage
import com.deliveryapp.customer.data.repository.ComplaintRepository
import com.deliveryapp.customer.feature.complaint.ComplaintDetailViewModel
import com.deliveryapp.customer.feature.complaint.ComplaintEntryViewModel
import com.deliveryapp.customer.feature.complaint.ComplaintFormViewModel
import com.deliveryapp.customer.feature.complaint.ComplaintLimits
import com.deliveryapp.customer.feature.complaint.ComplaintListViewModel
import com.deliveryapp.customer.feature.complaint.PickedImage
import com.deliveryapp.customer.feature.complaint.isWithinComplaintWindow
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ComplaintViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val completedAt = "2026-11-20T08:00:00.123"
    private val completedInstant = Instant.parse("2026-11-20T08:00:00.123Z")

    private fun image(name: String = "a.jpg") = ComplaintImage(name, "image/jpeg", byteArrayOf(1, 2, 3))
    private fun ready(name: String = "a.jpg") = PickedImage.Ready(image(name))
    private fun clockAt(instant: Instant) = Clock.fixed(instant, ZoneOffset.UTC)

    @Test
    fun window_isInclusiveAtExactly24Hours_andTreatsServerTimeAsUtc() {
        assertTrue(isWithinComplaintWindow(completedAt, completedInstant))
        assertTrue(isWithinComplaintWindow(completedAt, completedInstant.plusSeconds(86_400)))
        assertFalse(isWithinComplaintWindow(completedAt, completedInstant.plusSeconds(86_400).plusMillis(1)))
        assertFalse(isWithinComplaintWindow(null, completedInstant))
    }

    @Test
    fun entry_showsButtonOnlyWithinWindow_andWithoutExistingComplaint() = runTest {
        val inside = ComplaintEntryViewModel("o1", FakeComplaintGateway(), clockAt(completedInstant.plusSeconds(3_600)))
        assertFalse("hidden until loaded", inside.canCreate(completedAt))
        inside.refresh()
        assertTrue(inside.canCreate(completedAt))

        val late = ComplaintEntryViewModel("o1", FakeComplaintGateway(), clockAt(completedInstant.plusSeconds(86_401)))
        late.refresh()
        assertFalse(late.canCreate(completedAt))
    }

    @Test
    fun entry_showsExistingStatusInsteadOfButton_andQueriesThisOrder() = runTest {
        val gateway = FakeComplaintGateway().apply { items = listOf(sample("c1", status = "IN_REVIEW")) }
        val vm = ComplaintEntryViewModel("o1", gateway, clockAt(completedInstant))

        vm.refresh()

        assertEquals("IN_REVIEW", vm.state.existing?.status)
        assertFalse(vm.canCreate(completedAt))
        assertEquals("o1", gateway.lastOrderFilter)
    }

    @Test
    fun entry_error_hidesButton_andFlagsExpiredSession() = runTest {
        val gateway = FakeComplaintGateway().apply { mineError = ApiResult.Error("SESSION_EXPIRED", "Hết phiên", 401) }
        val vm = ComplaintEntryViewModel("o1", gateway, clockAt(completedInstant))

        vm.refresh()

        assertFalse(vm.canCreate(completedAt))
        assertTrue(vm.state.sessionExpired)
    }

    @Test
    fun form_requiresCategoryAndDescription_withoutCallingServer() {
        val gateway = FakeComplaintGateway()
        val vm = ComplaintFormViewModel("o1", gateway)
        vm.updateDescription("   ")

        vm.submit()

        assertEquals("Vui lòng chọn loại vấn đề.", vm.state.categoryError)
        assertEquals("Vui lòng mô tả vấn đề.", vm.state.descriptionError)
        assertEquals(0, gateway.createCalls.size)
    }

    @Test
    fun form_capsDescriptionAt1000Characters() {
        val vm = ComplaintFormViewModel("o1", FakeComplaintGateway())
        vm.updateDescription("a".repeat(ComplaintLimits.MAX_DESCRIPTION + 5))
        assertEquals(ComplaintLimits.MAX_DESCRIPTION, vm.state.description.length)
    }

    @Test
    fun form_keepsAtMostThreeImages_andExplainsWhy() {
        val vm = ComplaintFormViewModel("o1", FakeComplaintGateway())
        vm.addPicked(listOf(ready("1.jpg"), ready("2.jpg")))
        assertNull(vm.state.imageError)

        vm.addPicked(listOf(ready("3.jpg"), ready("4.jpg")))

        assertEquals(listOf("1.jpg", "2.jpg", "3.jpg"), vm.state.images.map { it.fileName })
        assertEquals("Chỉ được đính kèm tối đa 3 ảnh.", vm.state.imageError)
        vm.removeImage(0)
        assertEquals(listOf("2.jpg", "3.jpg"), vm.state.images.map { it.fileName })
        assertNull(vm.state.imageError)
    }

    @Test
    fun form_reportsRejectedImages_butKeepsValidOnes() {
        val vm = ComplaintFormViewModel("o1", FakeComplaintGateway())
        vm.addPicked(listOf(ready(), PickedImage.Unsupported))
        assertEquals(1, vm.state.images.size)
        assertEquals("Chỉ nhận ảnh JPEG hoặc PNG.", vm.state.imageError)
        vm.addPicked(listOf(PickedImage.TooLarge))
        assertEquals("Mỗi ảnh tối đa 5 MB.", vm.state.imageError)
    }

    @Test
    fun form_submitsTrimmedText_andReportsCreatedId() {
        val gateway = FakeComplaintGateway()
        val vm = ComplaintFormViewModel("o1", gateway)
        vm.selectCategory("DAMAGED_ITEM")
        vm.updateDescription("  Hàng bị móp  ")
        vm.addPicked(listOf(ready()))

        vm.submit()

        val call = gateway.createCalls.single()
        assertEquals("o1", call.orderId)
        assertEquals("DAMAGED_ITEM", call.category)
        assertEquals("Hàng bị móp", call.description)
        assertEquals(1, call.images.size)
        assertEquals("created", vm.state.createdId)
        vm.consumeCreated()
        assertNull(vm.state.createdId)
    }

    @Test
    fun form_windowClosed_showsServerMessage_andBlocksResend() {
        val gateway = FakeComplaintGateway().apply {
            createResult = ApiResult.Error("COMPLAINT_WINDOW_CLOSED", "Đã quá 24 giờ kể từ khi đơn hoàn thành.", 409)
        }
        val vm = ComplaintFormViewModel("o1", gateway)
        vm.selectCategory("OTHER")
        vm.updateDescription("Trễ")

        vm.submit()
        vm.submit()

        assertEquals("Đã quá 24 giờ kể từ khi đơn hoàn thành.", vm.state.submitError)
        assertTrue(vm.state.isBlocked)
        assertEquals(1, gateway.createCalls.size)
    }

    @Test
    fun form_alreadyExists_blocksResend() {
        val gateway = FakeComplaintGateway().apply {
            createResult = ApiResult.Error("COMPLAINT_ALREADY_EXISTS", "Bạn đã gửi khiếu nại cho đơn này.", 409)
        }
        val vm = ComplaintFormViewModel("o1", gateway)
        vm.selectCategory("OTHER")
        vm.updateDescription("Lần hai")

        vm.submit()

        assertTrue(vm.state.isBlocked)
    }

    @Test
    fun form_networkError_allowsRetry_andKeepsInput() {
        val gateway = FakeComplaintGateway().apply { createResult = ApiResult.Error("NETWORK", "Mất mạng") }
        val vm = ComplaintFormViewModel("o1", gateway)
        vm.selectCategory("OTHER")
        vm.updateDescription("Thử lại")
        vm.addPicked(listOf(ready()))

        vm.submit()
        assertFalse(vm.state.isBlocked)
        assertEquals("Thử lại", vm.state.description)
        assertEquals(1, vm.state.images.size)

        gateway.createResult = ApiResult.Success(sample("created"))
        vm.submit()
        assertEquals("created", vm.state.createdId)
        assertNull(vm.state.submitError)
    }

    @Test
    fun list_loadsPagesWithoutDuplicates() = runTest {
        val gateway = FakeComplaintGateway().apply {
            items = (1..25).map { sample("c$it") }
        }
        val vm = ComplaintListViewModel(gateway)

        vm.refresh()
        assertEquals(20, vm.state.items.size)
        assertTrue(vm.state.hasMore)

        vm.loadMore()
        assertEquals(25, vm.state.items.size)
        assertEquals(25, vm.state.items.distinctBy { it.id }.size)
        assertFalse(vm.state.hasMore)
    }

    @Test
    fun list_failedLoadMore_keepsLoadedItems() = runTest {
        val gateway = FakeComplaintGateway().apply { items = (1..25).map { sample("c$it") } }
        val vm = ComplaintListViewModel(gateway)
        vm.refresh()
        gateway.mineError = ApiResult.Error("NETWORK", "Mất mạng")

        vm.loadMore()

        assertEquals(20, vm.state.items.size)
        assertEquals("Mất mạng", vm.state.errorMessage)
        assertFalse(vm.state.isLoadingMore)
    }

    @Test
    fun detail_showsAdminNote_andLoadsAttachmentBytes() = runTest {
        val gateway = FakeComplaintGateway().apply {
            detailResult = ApiResult.Success(sample("c1", status = "REJECTED", note = "Không đủ bằng chứng"))
        }
        val vm = ComplaintDetailViewModel("c1", gateway)

        vm.refresh()

        assertEquals("Không đủ bằng chứng", vm.state.complaint?.adminNote)
        assertArrayEquals(byteArrayOf(9, 8), vm.attachmentBytes("a1"))
        gateway.attachmentResult = ApiResult.Error("COMPLAINT_ATTACHMENT_NOT_FOUND", "Không tìm thấy", 404)
        assertNull(vm.attachmentBytes("a1"))
    }

    @Test
    fun repository_sendsMultipartFieldsExpectedByBackend() = runTest {
        val api = RecordingComplaintApi()
        val repository = ComplaintRepository(api)

        val result = repository.create("o1", "OTHER", "Mô tả", listOf(image("ảnh 1.jpg")))

        assertTrue(result is ApiResult.Success)
        assertEquals("o1", api.orderId)
        assertEquals("OTHER", api.category)
        assertEquals("Mô tả", api.description)
        val disposition = api.files.single().headers!!["Content-Disposition"]!!
        assertTrue(disposition, disposition.contains("name=\"files\""))
        assertTrue(disposition, disposition.contains("filename=\"ảnh 1.jpg\""))
        assertEquals("image/jpeg", api.files.single().body.contentType().toString())
    }

    private fun sample(id: String, status: String = "OPEN", note: String? = null) = ComplaintResponse(
        id = id, orderId = "o1", reporterRole = "CUSTOMER", category = "OTHER", description = "Mô tả",
        status = status, adminNote = note, createdAt = "2026-11-20T09:00:00Z", updatedAt = "2026-11-20T09:00:00Z"
    )

    private data class CreateCall(val orderId: String, val category: String, val description: String, val images: List<ComplaintImage>)

    private inner class FakeComplaintGateway : ComplaintGateway {
        var items: List<ComplaintResponse> = emptyList()
        var mineError: ApiResult.Error? = null
        var lastOrderFilter: String? = null
        var createResult: ApiResult<ComplaintResponse> = ApiResult.Success(sample("created"))
        var detailResult: ApiResult<ComplaintResponse> = ApiResult.Success(sample("c1"))
        var attachmentResult: ApiResult<ByteArray> = ApiResult.Success(byteArrayOf(9, 8))
        val createCalls = mutableListOf<CreateCall>()

        override suspend fun create(orderId: String, category: String, description: String, images: List<ComplaintImage>) =
            createResult.also { createCalls += CreateCall(orderId, category, description, images) }

        override suspend fun mine(orderId: String?, page: Int, pageSize: Int): ApiResult<PagedResponse<ComplaintResponse>> {
            lastOrderFilter = orderId
            mineError?.let { return it }
            val slice = items.drop((page - 1) * pageSize).take(pageSize)
            return ApiResult.Success(PagedResponse(page, pageSize, items.size, slice))
        }

        override suspend fun detail(id: String) = detailResult
        override suspend fun attachment(id: String, attachmentId: String) = attachmentResult
    }

    private inner class RecordingComplaintApi : ComplaintApi {
        var orderId = ""
        var category = ""
        var description = ""
        var files: List<MultipartBody.Part> = emptyList()

        private fun RequestBody.text() = Buffer().also { writeTo(it) }.readUtf8()

        override suspend fun create(
            orderId: RequestBody,
            category: RequestBody,
            description: RequestBody,
            files: List<MultipartBody.Part>
        ): ComplaintResponse {
            this.orderId = orderId.text()
            this.category = category.text()
            this.description = description.text()
            this.files = files
            return sample("created")
        }

        override suspend fun mine(orderId: String?, page: Int, pageSize: Int) = PagedResponse(1, 20, 0, emptyList<ComplaintResponse>())
        override suspend fun detail(id: String) = sample(id)
        override suspend fun attachment(id: String, attachmentId: String): ResponseBody = throw UnsupportedOperationException()
    }
}
