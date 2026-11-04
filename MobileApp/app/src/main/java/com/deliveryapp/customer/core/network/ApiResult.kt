package com.deliveryapp.customer.core.network

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Error(val code: String?, val message: String, val httpStatus: Int? = null) : ApiResult<Nothing>
    data object Loading : ApiResult<Nothing>
}

data class ApiErrorResponse(
    val code: String?,
    val message: String?,
    val details: Any?
)
