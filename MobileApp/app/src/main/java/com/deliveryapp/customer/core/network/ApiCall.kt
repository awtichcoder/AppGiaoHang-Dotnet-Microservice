package com.deliveryapp.customer.core.network

import com.google.gson.Gson
import java.io.IOException
import retrofit2.HttpException

private val errorGson = Gson()

suspend fun <T> safeApiCall(block: suspend () -> T): ApiResult<T> = try {
    ApiResult.Success(block())
} catch (error: HttpException) {
    val status = error.code()
    val body = error.response()?.errorBody()?.string()
    val parsed = body?.let { parseErrorBody(it) }
    ApiResult.Error(
        code = parsed?.code ?: if (status == 401) "SESSION_EXPIRED" else "HTTP_$status",
        message = parsed?.message ?: defaultHttpMessage(status),
        httpStatus = status
    )
} catch (error: IOException) {
    ApiResult.Error(
        code = "NETWORK",
        message = "Không thể kết nối máy chủ. Kiểm tra mạng hoặc backend Docker rồi thử lại."
    )
} catch (error: Exception) {
    ApiResult.Error(
        code = "UNKNOWN",
        message = error.message ?: "Đã có lỗi xảy ra. Vui lòng thử lại."
    )
}

fun ApiResult.Error.isSessionExpired(): Boolean = httpStatus == 401 || code == "SESSION_EXPIRED"

private fun parseErrorBody(body: String): ApiErrorResponse? = runCatching {
    errorGson.fromJson(body, ApiErrorResponse::class.java)
}.getOrNull()

private fun defaultHttpMessage(status: Int): String = when (status) {
    400 -> "Dữ liệu chưa hợp lệ. Vui lòng kiểm tra lại."
    401 -> "Phiên đăng nhập đã hết hạn."
    403 -> "Bạn không có quyền thực hiện thao tác này."
    404 -> "Không tìm thấy dữ liệu."
    in 500..599 -> "Máy chủ đang gặp lỗi. Vui lòng thử lại sau."
    else -> "Yêu cầu thất bại. Vui lòng thử lại."
}
