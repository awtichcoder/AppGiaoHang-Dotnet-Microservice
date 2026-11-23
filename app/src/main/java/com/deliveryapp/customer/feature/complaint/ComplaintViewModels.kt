package com.deliveryapp.customer.feature.complaint

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.deliveryapp.customer.AppContainer
import com.deliveryapp.customer.core.network.ApiResult
import com.deliveryapp.customer.core.network.isSessionExpired
import com.deliveryapp.customer.data.dto.ComplaintResponse
import com.deliveryapp.customer.data.repository.ComplaintGateway
import com.deliveryapp.customer.data.repository.ComplaintImage
import java.time.Clock
import kotlinx.coroutines.launch

private inline fun <reified VM : ViewModel> factoryOf(crossinline create: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    }

/** Decides what the completed-order screen shows: the send button, the existing status, or nothing. */
class ComplaintEntryViewModel(
    private val orderId: String,
    private val gateway: ComplaintGateway,
    private val clock: Clock = Clock.systemUTC()
) : ViewModel() {
    var state by mutableStateOf(ComplaintEntryUiState())
        private set

    suspend fun refresh() {
        state = when (val result = gateway.mine(orderId = orderId, page = 1, pageSize = 1)) {
            is ApiResult.Success -> ComplaintEntryUiState(isLoading = false, existing = result.data.items.firstOrNull())
            is ApiResult.Error -> state.copy(
                isLoading = false,
                errorMessage = result.message,
                sessionExpired = result.isSessionExpired()
            )
            ApiResult.Loading -> state
        }
    }

    fun reload() {
        state = state.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch { refresh() }
    }

    fun canCreate(completedAt: String?): Boolean =
        !state.isLoading && state.errorMessage == null && state.existing == null &&
            isWithinComplaintWindow(completedAt, clock.instant())

    fun consumeSessionExpired() { state = state.copy(sessionExpired = false) }

    companion object {
        fun factory(container: AppContainer, orderId: String) =
            factoryOf { ComplaintEntryViewModel(orderId, container.complaintRepository) }
    }
}

data class ComplaintEntryUiState(
    val isLoading: Boolean = true,
    val existing: ComplaintResponse? = null,
    val errorMessage: String? = null,
    val sessionExpired: Boolean = false
)

class ComplaintFormViewModel(
    val orderId: String,
    private val gateway: ComplaintGateway
) : ViewModel() {
    var state by mutableStateOf(ComplaintFormUiState())
        private set

    fun selectCategory(code: String) { state = state.copy(category = code, categoryError = null) }

    fun updateDescription(value: String) {
        state = state.copy(description = value.take(ComplaintLimits.MAX_DESCRIPTION), descriptionError = null)
    }

    fun addPicked(picked: List<PickedImage>) {
        if (picked.isEmpty()) return
        val ready = picked.filterIsInstance<PickedImage.Ready>().map { it.image }
        val room = ComplaintLimits.MAX_IMAGES - state.images.size
        val error = when {
            picked.any { it is PickedImage.Unsupported } -> "Chỉ nhận ảnh JPEG hoặc PNG."
            picked.any { it is PickedImage.TooLarge } -> "Mỗi ảnh tối đa 5 MB."
            picked.any { it is PickedImage.Unreadable } -> "Không đọc được một số ảnh. Vui lòng chọn lại."
            ready.size > room -> "Chỉ được đính kèm tối đa ${ComplaintLimits.MAX_IMAGES} ảnh."
            else -> null
        }
        state = state.copy(images = state.images + ready.take(room.coerceAtLeast(0)), imageError = error)
    }

    fun removeImage(index: Int) {
        state = state.copy(images = state.images.filterIndexed { i, _ -> i != index }, imageError = null)
    }

    fun submit() {
        if (state.isSubmitting || state.isBlocked) return
        val category = state.category
        val description = state.description.trim()
        val categoryError = if (category == null) "Vui lòng chọn loại vấn đề." else null
        val descriptionError = if (description.isEmpty()) "Vui lòng mô tả vấn đề." else null
        if (category == null || descriptionError != null) {
            state = state.copy(categoryError = categoryError, descriptionError = descriptionError)
            return
        }
        state = state.copy(isSubmitting = true, submitError = null, submitErrorCode = null)
        viewModelScope.launch {
            state = when (val result = gateway.create(orderId, category, description, state.images)) {
                is ApiResult.Success -> state.copy(isSubmitting = false, createdId = result.data.id)
                is ApiResult.Error -> state.copy(
                    isSubmitting = false,
                    submitError = result.message,
                    submitErrorCode = result.code,
                    sessionExpired = result.isSessionExpired()
                )
                ApiResult.Loading -> state
            }
        }
    }

    fun consumeCreated() { state = state.copy(createdId = null) }
    fun consumeSessionExpired() { state = state.copy(sessionExpired = false) }

    companion object {
        fun factory(container: AppContainer, orderId: String) =
            factoryOf { ComplaintFormViewModel(orderId, container.complaintRepository) }
    }
}

data class ComplaintFormUiState(
    val category: String? = null,
    val description: String = "",
    val images: List<ComplaintImage> = emptyList(),
    val categoryError: String? = null,
    val descriptionError: String? = null,
    val imageError: String? = null,
    val submitError: String? = null,
    val submitErrorCode: String? = null,
    val isSubmitting: Boolean = false,
    val createdId: String? = null,
    val sessionExpired: Boolean = false
) {
    val isBlocked: Boolean get() = submitErrorCode in blockingComplaintErrors
}

class ComplaintListViewModel(private val gateway: ComplaintGateway) : ViewModel() {
    var state by mutableStateOf(ComplaintListUiState())
        private set

    suspend fun refresh() = loadPage(1)

    fun reload() {
        state = state.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch { refresh() }
    }

    fun loadMore() {
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        state = state.copy(isLoadingMore = true, errorMessage = null)
        viewModelScope.launch { loadPage(state.page + 1) }
    }

    private suspend fun loadPage(page: Int) {
        state = when (val result = gateway.mine(page = page, pageSize = PAGE_SIZE)) {
            is ApiResult.Success -> {
                val items = if (page == 1) result.data.items
                else (state.items + result.data.items).distinctBy { it.id }
                ComplaintListUiState(isLoading = false, items = items, page = page, totalItems = result.data.totalItems)
            }
            is ApiResult.Error -> state.copy(
                isLoading = false,
                isLoadingMore = false,
                errorMessage = result.message,
                sessionExpired = result.isSessionExpired()
            )
            ApiResult.Loading -> state
        }
    }

    fun consumeSessionExpired() { state = state.copy(sessionExpired = false) }

    companion object {
        const val PAGE_SIZE = 20
        fun factory(container: AppContainer) = factoryOf { ComplaintListViewModel(container.complaintRepository) }
    }
}

data class ComplaintListUiState(
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val items: List<ComplaintResponse> = emptyList(),
    val page: Int = 0,
    val totalItems: Int = 0,
    val errorMessage: String? = null,
    val sessionExpired: Boolean = false
) {
    val hasMore: Boolean get() = items.size < totalItems
}

class ComplaintDetailViewModel(
    val complaintId: String,
    private val gateway: ComplaintGateway
) : ViewModel() {
    var state by mutableStateOf(ComplaintDetailUiState())
        private set

    suspend fun refresh() {
        state = when (val result = gateway.detail(complaintId)) {
            is ApiResult.Success -> ComplaintDetailUiState(isLoading = false, complaint = result.data)
            is ApiResult.Error -> state.copy(
                isLoading = false,
                errorMessage = if (state.complaint == null) result.message else null,
                sessionExpired = result.isSessionExpired()
            )
            ApiResult.Loading -> state
        }
    }

    fun reload() {
        state = state.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch { refresh() }
    }

    suspend fun attachmentBytes(attachmentId: String): ByteArray? =
        (gateway.attachment(complaintId, attachmentId) as? ApiResult.Success)?.data

    fun consumeSessionExpired() { state = state.copy(sessionExpired = false) }

    companion object {
        fun factory(container: AppContainer, complaintId: String) =
            factoryOf { ComplaintDetailViewModel(complaintId, container.complaintRepository) }
    }
}

data class ComplaintDetailUiState(
    val isLoading: Boolean = true,
    val complaint: ComplaintResponse? = null,
    val errorMessage: String? = null,
    val sessionExpired: Boolean = false
)
