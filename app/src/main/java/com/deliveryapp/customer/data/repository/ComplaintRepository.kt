package com.deliveryapp.customer.data.repository

import com.deliveryapp.customer.core.network.ApiResult
import com.deliveryapp.customer.core.network.safeApiCall
import com.deliveryapp.customer.data.api.ComplaintApi
import com.deliveryapp.customer.data.dto.ComplaintResponse
import com.deliveryapp.customer.data.dto.PagedResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/** Image already read into memory, so OkHttp can resend it after a token refresh. */
class ComplaintImage(val fileName: String, val contentType: String, val bytes: ByteArray)

/** Everything the complaint screens need from the backend (fakeable in tests). */
interface ComplaintGateway {
    suspend fun create(
        orderId: String,
        category: String,
        description: String,
        images: List<ComplaintImage>
    ): ApiResult<ComplaintResponse>
    suspend fun mine(orderId: String? = null, page: Int = 1, pageSize: Int = 20): ApiResult<PagedResponse<ComplaintResponse>>
    suspend fun detail(id: String): ApiResult<ComplaintResponse>
    suspend fun attachment(id: String, attachmentId: String): ApiResult<ByteArray>
}

class ComplaintRepository(private val api: ComplaintApi) : ComplaintGateway {
    override suspend fun create(
        orderId: String,
        category: String,
        description: String,
        images: List<ComplaintImage>
    ) = safeApiCall {
        val text = "text/plain; charset=utf-8".toMediaType()
        api.create(
            orderId = orderId.toRequestBody(text),
            category = category.toRequestBody(text),
            description = description.toRequestBody(text),
            files = images.map { image ->
                MultipartBody.Part.createFormData(
                    "files",
                    image.fileName,
                    image.bytes.toRequestBody(image.contentType.toMediaType())
                )
            }
        )
    }

    override suspend fun mine(orderId: String?, page: Int, pageSize: Int) =
        safeApiCall { api.mine(orderId, page, pageSize) }

    override suspend fun detail(id: String) = safeApiCall { api.detail(id) }

    override suspend fun attachment(id: String, attachmentId: String) = safeApiCall {
        val body = api.attachment(id, attachmentId)
        withContext(Dispatchers.IO) { body.use { it.bytes() } }
    }
}
