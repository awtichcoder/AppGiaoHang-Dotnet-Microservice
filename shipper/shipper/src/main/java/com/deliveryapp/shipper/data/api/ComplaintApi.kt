package com.deliveryapp.shipper.data.api

import com.deliveryapp.shipper.data.model.Complaint
import com.deliveryapp.shipper.data.model.ComplaintAttachment
import com.deliveryapp.shipper.data.model.ComplaintPage
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.*

data class ComplaintAttachmentResponse(val id: String, val fileName: String, val contentType: String, val sizeBytes: Long)
data class ComplaintResponse(
    val id: String, val orderId: String, val category: String, val description: String, val status: String,
    val adminNote: String?, val createdAt: String, val updatedAt: String,
    val attachments: List<ComplaintAttachmentResponse>?,
) {
    fun toDomain() = Complaint(id, orderId, category, description, status, adminNote,
        parseServerInstant(createdAt), parseServerInstant(updatedAt),
        attachments.orEmpty().map { ComplaintAttachment(it.id, it.fileName, it.contentType, it.sizeBytes) })
}

fun PageResponse<ComplaintResponse>.toComplaintPage() = ComplaintPage(page, totalItems, items.map { it.toDomain() })

interface ComplaintApi {
    @Multipart
    @POST("api/complaint")
    suspend fun create(
        @Part("orderId") orderId: RequestBody,
        @Part("category") category: RequestBody,
        @Part("description") description: RequestBody,
        @Part files: List<MultipartBody.Part>,
    ): ComplaintResponse

    @GET("api/complaint/me")
    suspend fun mine(
        @Query("orderId") orderId: String? = null,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
    ): PageResponse<ComplaintResponse>

    @GET("api/complaint/{id}") suspend fun detail(@Path("id") id: String): ComplaintResponse

    @Streaming
    @GET("api/complaint/{id}/attachments/{attachmentId}")
    suspend fun attachment(@Path("id") id: String, @Path("attachmentId") attachmentId: String): ResponseBody
}
