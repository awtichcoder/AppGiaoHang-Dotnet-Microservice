package com.deliveryapp.shipper.data.repository

import com.deliveryapp.shipper.core.network.apiCall
import com.deliveryapp.shipper.data.api.ComplaintApi
import com.deliveryapp.shipper.data.api.toComplaintPage
import com.deliveryapp.shipper.data.model.*
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

interface ComplaintRepository {
    suspend fun create(orderId: String, category: String, description: String, images: List<ComplaintImage>): Complaint
    suspend fun mine(orderId: String? = null, page: Int = 1, pageSize: Int = 20): ComplaintPage
    suspend fun detail(id: String): Complaint
    suspend fun attachment(id: String, attachmentId: String): ByteArray
}

class LiveComplaintRepository(private val api: ComplaintApi) : ComplaintRepository {
    override suspend fun create(orderId: String, category: String, description: String, images: List<ComplaintImage>): Complaint {
        val text = "text/plain; charset=utf-8".toMediaType()
        return call {
            api.create(
                orderId.toRequestBody(text), category.toRequestBody(text), description.toRequestBody(text),
                images.map { MultipartBody.Part.createFormData("files", it.fileName, it.bytes.toRequestBody(it.contentType.toMediaType())) },
            )
        }.toDomain()
    }

    override suspend fun mine(orderId: String?, page: Int, pageSize: Int) =
        call { api.mine(orderId, page, pageSize) }.toComplaintPage()

    override suspend fun detail(id: String) = call { api.detail(id) }.toDomain()

    override suspend fun attachment(id: String, attachmentId: String): ByteArray = call {
        val body = api.attachment(id, attachmentId)
        withContext(Dispatchers.IO) { body.use { it.bytes() } }
    }

    private suspend fun <T> call(block: suspend () -> T): T = try {
        apiCall(block)
    } catch (e: RepositoryException) {
        throw if (e.code == "HTTP_401" || e.code == "UNAUTHORIZED") {
            RepositoryException("SESSION_EXPIRED", "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.")
        } else e
    }
}

/** Offline demo (-PuseMock=true). Applies the same one-per-trip rule as the server. */
class MockComplaintRepository(
    private val clock: Clock = Clock.systemUTC(),
    private val latencyMs: Long = 300,
) : ComplaintRepository {
    private val mutex = Mutex()
    private val complaints = mutableListOf<Complaint>()
    private val images = mutableMapOf<String, ByteArray>()

    override suspend fun create(orderId: String, category: String, description: String, images: List<ComplaintImage>): Complaint {
        delay(latencyMs)
        return mutex.withLock {
            if (complaints.any { it.orderId == orderId }) {
                throw RepositoryException("COMPLAINT_ALREADY_EXISTS", "Bạn đã gửi khiếu nại cho chuyến này.")
            }
            val now = clock.instant()
            val attachments = images.map { image ->
                val id = UUID.randomUUID().toString()
                this.images[id] = image.bytes
                ComplaintAttachment(id, image.fileName, image.contentType, image.bytes.size.toLong())
            }
            Complaint(UUID.randomUUID().toString(), orderId, category, description, "OPEN", null, now, now, attachments)
                .also { complaints.add(0, it) }
        }
    }

    override suspend fun mine(orderId: String?, page: Int, pageSize: Int): ComplaintPage {
        delay(latencyMs)
        return mutex.withLock {
            val matching = complaints.filter { orderId == null || it.orderId == orderId }
            ComplaintPage(page, matching.size, matching.drop((page - 1) * pageSize).take(pageSize))
        }
    }

    override suspend fun detail(id: String): Complaint {
        delay(latencyMs)
        return mutex.withLock {
            complaints.firstOrNull { it.id == id } ?: throw RepositoryException("COMPLAINT_NOT_FOUND", "Không tìm thấy khiếu nại.")
        }
    }

    override suspend fun attachment(id: String, attachmentId: String): ByteArray {
        delay(latencyMs)
        return mutex.withLock {
            images[attachmentId] ?: throw RepositoryException("COMPLAINT_ATTACHMENT_NOT_FOUND", "Không tìm thấy ảnh đính kèm.")
        }
    }
}
