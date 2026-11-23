package com.deliveryapp.customer.data.api

import com.deliveryapp.customer.data.dto.ComplaintResponse
import com.deliveryapp.customer.data.dto.PagedResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface ComplaintApi {
    @Multipart
    @POST("api/complaint")
    suspend fun create(
        @Part("orderId") orderId: RequestBody,
        @Part("category") category: RequestBody,
        @Part("description") description: RequestBody,
        @Part files: List<MultipartBody.Part>
    ): ComplaintResponse

    @GET("api/complaint/me")
    suspend fun mine(
        @Query("orderId") orderId: String? = null,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20
    ): PagedResponse<ComplaintResponse>

    @GET("api/complaint/{id}")
    suspend fun detail(@Path("id") id: String): ComplaintResponse

    @Streaming
    @GET("api/complaint/{id}/attachments/{attachmentId}")
    suspend fun attachment(@Path("id") id: String, @Path("attachmentId") attachmentId: String): ResponseBody
}
