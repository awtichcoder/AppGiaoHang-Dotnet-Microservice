package com.deliveryapp.shipper

import com.deliveryapp.shipper.core.network.NetworkFactory
import com.deliveryapp.shipper.core.storage.TokenPair
import com.deliveryapp.shipper.core.storage.TokenStore
import com.deliveryapp.shipper.data.api.ComplaintApi
import com.deliveryapp.shipper.data.model.*
import com.deliveryapp.shipper.data.repository.ComplaintRepository
import com.deliveryapp.shipper.data.repository.LiveComplaintRepository
import com.deliveryapp.shipper.data.repository.MockComplaintRepository
import com.deliveryapp.shipper.feature.complaint.ComplaintViewModel
import com.deliveryapp.shipper.feature.complaint.PickedImage
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ComplaintViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val completedAt = Instant.parse("2026-11-20T08:00:00Z")
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }

    private fun clockAt(instant: Instant) = Clock.fixed(instant, ZoneOffset.UTC)
    private fun image(name: String = "a.jpg") = ComplaintImage(name, "image/jpeg", byteArrayOf(1, 2, 3))
    private fun ready(name: String = "a.jpg") = PickedImage.Ready(image(name))
    private fun complaint(id: String, status: String = "OPEN", note: String? = null) = Complaint(
        id, "r1", "OTHER", "Mô tả", status, note, completedAt, completedAt, emptyList())

    @Test fun windowIsInclusiveAtExactly24Hours() {
        assertTrue(isWithinComplaintWindow(completedAt, completedAt.plusSeconds(86_400)))
        assertFalse(isWithinComplaintWindow(completedAt, completedAt.plusSeconds(86_400).plusMillis(1)))
        assertFalse(isWithinComplaintWindow(null, completedAt))
    }

    @Test fun reportButtonNeedsLoadedStateOpenWindowAndNoExistingReport() = runTest(dispatcher) {
        val vm = ComplaintViewModel(FakeRepository(), clockAt(completedAt.plusSeconds(3_600)))
        assertFalse(vm.canReport("r1", completedAt))
        vm.loadEntry("r1")
        assertFalse("hidden while loading", vm.canReport("r1", completedAt))
        runCurrent()
        assertTrue(vm.canReport("r1", completedAt))
        assertFalse("other trip", vm.canReport("r2", completedAt))

        val late = ComplaintViewModel(FakeRepository(), clockAt(completedAt.plusSeconds(86_401)))
        late.loadEntry("r1"); runCurrent()
        assertFalse(late.canReport("r1", completedAt))
    }

    @Test fun existingReportReplacesTheButton() = runTest(dispatcher) {
        val fake = FakeRepository().apply { items = listOf(complaint("c1", "IN_REVIEW")) }
        val vm = ComplaintViewModel(fake, clockAt(completedAt))
        vm.loadEntry("r1"); runCurrent()
        assertEquals("IN_REVIEW", vm.entry.value.existing?.status)
        assertFalse(vm.canReport("r1", completedAt))
        assertEquals("r1", fake.lastOrderFilter)
    }

    @Test fun formValidatesWithoutCallingServer() = runTest(dispatcher) {
        val fake = FakeRepository()
        val vm = ComplaintViewModel(fake, clockAt(completedAt))
        vm.startForm("r1"); vm.updateDescription("   ")
        vm.submit(); runCurrent()
        assertEquals("Vui lòng chọn loại vấn đề.", vm.form.value.categoryError)
        assertEquals("Vui lòng mô tả sự cố.", vm.form.value.descriptionError)
        assertEquals(0, fake.created.size)
    }

    @Test fun formKeepsAtMostThreeImagesAndReportsBadOnes() {
        val vm = ComplaintViewModel(FakeRepository(), clockAt(completedAt))
        vm.startForm("r1")
        vm.addPicked(listOf(ready("1.jpg"), PickedImage.Unsupported))
        assertEquals("Chỉ nhận ảnh JPEG hoặc PNG.", vm.form.value.imageError)
        vm.addPicked(listOf(ready("2.jpg"), ready("3.jpg"), ready("4.jpg")))
        assertEquals(listOf("1.jpg", "2.jpg", "3.jpg"), vm.form.value.images.map { it.fileName })
        assertEquals("Chỉ được đính kèm tối đa 3 ảnh.", vm.form.value.imageError)
        vm.removeImage(1)
        assertEquals(listOf("1.jpg", "3.jpg"), vm.form.value.images.map { it.fileName })
    }

    @Test fun submitSendsTrimmedTextOnceAndUpdatesEntry() = runTest(dispatcher) {
        val fake = FakeRepository()
        val vm = ComplaintViewModel(fake, clockAt(completedAt))
        vm.startForm("r1"); vm.selectCategory("WRONG_ITEM"); vm.updateDescription("  Khách từ chối nhận  ")
        vm.submit(); vm.submit(); runCurrent()
        assertEquals(1, fake.created.size)
        assertEquals("Khách từ chối nhận", fake.created.single().second)
        assertEquals("created", vm.form.value.createdId)
        assertEquals("created", vm.entry.value.existing?.id)
        vm.consumeCreated()
        assertNull(vm.form.value.orderId)
    }

    @Test fun serverRuleErrorsBlockResendButNetworkErrorAllowsRetry() = runTest(dispatcher) {
        val fake = FakeRepository().apply { createError = RepositoryException("COMPLAINT_WINDOW_CLOSED", "Đã quá 24 giờ.") }
        val vm = ComplaintViewModel(fake, clockAt(completedAt))
        vm.startForm("r1"); vm.selectCategory("OTHER"); vm.updateDescription("Trễ")
        vm.submit(); runCurrent()
        assertEquals("Đã quá 24 giờ.", vm.form.value.submitError)
        assertTrue(vm.form.value.blocked)
        vm.submit(); runCurrent()
        assertEquals(1, fake.attempts)

        val retry = FakeRepository().apply { createError = RepositoryException("NETWORK", "Mất mạng") }
        val other = ComplaintViewModel(retry, clockAt(completedAt))
        other.startForm("r1"); other.selectCategory("OTHER"); other.updateDescription("Trễ")
        other.submit(); runCurrent()
        assertFalse(other.form.value.blocked)
        assertEquals("Trễ", other.form.value.description)
        retry.createError = null
        other.submit(); runCurrent()
        assertEquals("created", other.form.value.createdId)
    }

    @Test fun draftSurvivesReopeningSameTripButNotAnotherTrip() {
        val vm = ComplaintViewModel(FakeRepository(), clockAt(completedAt))
        vm.startForm("r1"); vm.updateDescription("Nháp")
        vm.startForm("r1")
        assertEquals("Nháp", vm.form.value.description)
        vm.startForm("r2")
        assertEquals("", vm.form.value.description)
    }

    @Test fun listPagesWithoutDuplicates() = runTest(dispatcher) {
        val fake = FakeRepository().apply { items = (1..25).map { complaint("c$it") } }
        val vm = ComplaintViewModel(fake, clockAt(completedAt))
        vm.loadList(); runCurrent()
        assertEquals(20, vm.list.value.items.size)
        assertTrue(vm.list.value.hasMore)
        vm.loadMore(); runCurrent()
        assertEquals(25, vm.list.value.items.distinctBy { it.id }.size)
        assertFalse(vm.list.value.hasMore)
    }

    @Test fun detailShowsAdminNoteAndResetClearsEverything() = runTest(dispatcher) {
        val fake = FakeRepository().apply { detailResult = complaint("c1", "REJECTED", "Không đủ bằng chứng") }
        val vm = ComplaintViewModel(fake, clockAt(completedAt))
        vm.loadDetail("c1"); runCurrent()
        assertEquals("Không đủ bằng chứng", vm.detail.value.complaint?.adminNote)
        vm.startForm("r1"); vm.updateDescription("Nháp")
        vm.reset()
        assertNull(vm.detail.value.complaint)
        assertEquals("", vm.form.value.description)
    }

    @Test fun mockRepositoryEnforcesOneReportPerTrip() = runTest {
        val repository = MockComplaintRepository(clockAt(completedAt), 0)
        val created = repository.create("r1", "OTHER", "Mô tả", listOf(image()))
        assertArrayEquals(byteArrayOf(1, 2, 3), repository.attachment(created.id, created.attachments.single().id))
        val error = runCatching { repository.create("r1", "OTHER", "Lần hai", emptyList()) }.exceptionOrNull() as RepositoryException
        assertEquals("COMPLAINT_ALREADY_EXISTS", error.code)
    }

    @Test fun liveRepositorySendsMultipartAndKeepsBackendErrorCode() = runBlockingWithServer { server, repository ->
        server.enqueue(MockResponse().setResponseCode(201).setBody(
            """{"id":"c1","orderId":"r1","reporterRole":"DRIVER","category":"OTHER","description":"Mô tả","status":"OPEN",
                "adminNote":null,"createdAt":"2026-11-20T09:00:00.1234567Z","updatedAt":"2026-11-20T09:00:00.1234567Z",
                "attachments":[{"id":"a1","fileName":"a.jpg","contentType":"image/jpeg","sizeBytes":3,"createdAt":"2026-11-20T09:00:00Z"}]}"""))
        val created = repository.create("r1", "OTHER", "Mô tả", listOf(image()))
        assertEquals("a1", created.attachments.single().id)
        val request = server.takeRequest()
        assertEquals("/api/complaint", request.path)
        assertEquals("Bearer access", request.getHeader("Authorization"))
        assertTrue(request.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
        val body = request.body.readUtf8()
        listOf("name=\"orderId\"", "name=\"category\"", "name=\"description\"", "name=\"files\"; filename=\"a.jpg\"", "image/jpeg")
            .forEach { assertTrue(it, body.contains(it)) }

        server.enqueue(MockResponse().setResponseCode(409)
            .setBody("""{"code":"COMPLAINT_ALREADY_EXISTS","message":"Bạn đã gửi khiếu nại cho đơn này.","details":null}"""))
        val error = runCatching { repository.create("r1", "OTHER", "Lần hai", emptyList()) }.exceptionOrNull() as RepositoryException
        assertEquals("COMPLAINT_ALREADY_EXISTS", error.code)
    }

    private fun runBlockingWithServer(block: suspend (MockWebServer, ComplaintRepository) -> Unit) = runTest {
        val server = MockWebServer().apply { start() }
        try {
            val retrofit = NetworkFactory.create(StaticTokens, server.url("/").toString())
            block(server, LiveComplaintRepository(retrofit.create(ComplaintApi::class.java)))
        } finally { server.shutdown() }
    }

    private object StaticTokens : TokenStore {
        private val pair = TokenPair("access", "refresh")
        override suspend fun current() = pair
        override suspend fun save(pair: TokenPair) = Unit
        override suspend fun replaceIfCurrent(expected: TokenPair, replacement: TokenPair?) = false
        override suspend fun clear() = Unit
    }

    private inner class FakeRepository : ComplaintRepository {
        var items: List<Complaint> = emptyList()
        var createError: RepositoryException? = null
        var detailResult: Complaint = complaint("c1")
        var lastOrderFilter: String? = null
        var attempts = 0
        val created = mutableListOf<Pair<String, String>>()

        override suspend fun create(orderId: String, category: String, description: String, images: List<ComplaintImage>): Complaint {
            attempts++
            createError?.let { throw it }
            created += orderId to description
            return complaint("created")
        }
        override suspend fun mine(orderId: String?, page: Int, pageSize: Int): ComplaintPage {
            lastOrderFilter = orderId
            return ComplaintPage(page, items.size, items.drop((page - 1) * pageSize).take(pageSize))
        }
        override suspend fun detail(id: String) = detailResult
        override suspend fun attachment(id: String, attachmentId: String) = byteArrayOf(9)
    }
}
