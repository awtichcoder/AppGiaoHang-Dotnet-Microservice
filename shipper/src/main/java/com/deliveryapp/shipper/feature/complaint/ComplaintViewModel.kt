package com.deliveryapp.shipper.feature.complaint

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deliveryapp.shipper.data.model.*
import com.deliveryapp.shipper.data.repository.ComplaintRepository
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ComplaintFormState(
    val orderId: String? = null,
    val category: String? = null, val description: String = "", val images: List<ComplaintImage> = emptyList(),
    val categoryError: String? = null, val descriptionError: String? = null, val imageError: String? = null,
    val submitError: String? = null, val submitErrorCode: String? = null,
    val submitting: Boolean = false, val createdId: String? = null,
) {
    val blocked get() = submitErrorCode in blockingComplaintErrors
}
data class ComplaintEntryState(
    val orderId: String? = null, val loading: Boolean = false,
    val existing: Complaint? = null, val error: String? = null,
)
data class ComplaintListState(
    val loading: Boolean = false, val loadingMore: Boolean = false, val items: List<Complaint> = emptyList(),
    val page: Int = 0, val totalItems: Int = 0, val error: String? = null,
) {
    val hasMore get() = items.size < totalItems
}
data class ComplaintDetailState(val loading: Boolean = false, val complaint: Complaint? = null, val error: String? = null)

class ComplaintViewModel(
    private val repository: ComplaintRepository,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {
    private val entryMutable = MutableStateFlow(ComplaintEntryState())
    private val formMutable = MutableStateFlow(ComplaintFormState())
    private val listMutable = MutableStateFlow(ComplaintListState())
    private val detailMutable = MutableStateFlow(ComplaintDetailState())
    val entry = entryMutable.asStateFlow()
    val form = formMutable.asStateFlow()
    val list = listMutable.asStateFlow()
    val detail = detailMutable.asStateFlow()
    private var entryJob: Job? = null
    private var listJob: Job? = null
    private var detailJob: Job? = null
    private var submitJob: Job? = null

    private fun messageOf(error: Exception) =
        (error as? RepositoryException)?.message ?: "Không thể tải dữ liệu. Vui lòng thử lại."

    fun loadEntry(orderId: String) {
        entryJob?.cancel()
        entryMutable.update {
            if (it.orderId == orderId) it.copy(loading = true, error = null)
            else ComplaintEntryState(orderId = orderId, loading = true)
        }
        entryJob = viewModelScope.launch {
            try {
                val existing = repository.mine(orderId = orderId, page = 1, pageSize = 1).items.firstOrNull()
                entryMutable.value = ComplaintEntryState(orderId = orderId, existing = existing)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { entryMutable.update { it.copy(loading = false, error = messageOf(e)) } }
        }
    }

    fun canReport(orderId: String, completedAt: Instant?): Boolean = entry.value.let {
        it.orderId == orderId && !it.loading && it.error == null && it.existing == null &&
            isWithinComplaintWindow(completedAt, clock.instant())
    }

    fun startForm(orderId: String) {
        // Keep the draft when the form is reopened for the same trip (for example after rotation).
        if (form.value.orderId != orderId) formMutable.value = ComplaintFormState(orderId = orderId)
    }
    fun selectCategory(code: String) = formMutable.update { it.copy(category = code, categoryError = null) }
    fun updateDescription(value: String) = formMutable.update {
        it.copy(description = value.take(ComplaintLimits.MAX_DESCRIPTION), descriptionError = null)
    }
    fun addPicked(picked: List<PickedImage>) {
        if (picked.isEmpty()) return
        formMutable.update { state ->
            val ready = picked.filterIsInstance<PickedImage.Ready>().map { it.image }
            val room = ComplaintLimits.MAX_IMAGES - state.images.size
            val error = when {
                picked.any { it is PickedImage.Unsupported } -> "Chỉ nhận ảnh JPEG hoặc PNG."
                picked.any { it is PickedImage.TooLarge } -> "Mỗi ảnh tối đa 5 MB."
                picked.any { it is PickedImage.Unreadable } -> "Không đọc được một số ảnh. Vui lòng chọn lại."
                ready.size > room -> "Chỉ được đính kèm tối đa ${ComplaintLimits.MAX_IMAGES} ảnh."
                else -> null
            }
            state.copy(images = state.images + ready.take(room.coerceAtLeast(0)), imageError = error)
        }
    }
    fun removeImage(index: Int) = formMutable.update { state ->
        state.copy(images = state.images.filterIndexed { i, _ -> i != index }, imageError = null)
    }

    fun submit() {
        val state = form.value
        val orderId = state.orderId ?: return
        if (state.submitting || state.blocked) return
        val category = state.category
        val description = state.description.trim()
        if (category == null || description.isEmpty()) {
            formMutable.update {
                it.copy(categoryError = if (category == null) "Vui lòng chọn loại vấn đề." else null,
                    descriptionError = if (description.isEmpty()) "Vui lòng mô tả sự cố." else null)
            }
            return
        }
        formMutable.update { it.copy(submitting = true, submitError = null, submitErrorCode = null) }
        submitJob = viewModelScope.launch {
            try {
                val created = repository.create(orderId, category, description, state.images)
                formMutable.value = ComplaintFormState(orderId = orderId, createdId = created.id)
                entryMutable.value = ComplaintEntryState(orderId = orderId, existing = created)
            } catch (e: CancellationException) {
                formMutable.update { it.copy(submitting = false) }
                throw e
            } catch (e: RepositoryException) {
                formMutable.update { it.copy(submitting = false, submitError = e.message, submitErrorCode = e.code) }
            } catch (e: Exception) {
                formMutable.update { it.copy(submitting = false, submitError = messageOf(e)) }
            }
        }
    }
    /** After success the draft is gone, so another trip starts with an empty form. */
    fun consumeCreated() { formMutable.value = ComplaintFormState() }

    fun loadList() {
        listJob?.cancel()
        listMutable.update { it.copy(loading = true, loadingMore = false, error = null) }
        listJob = viewModelScope.launch { fetchPage(1) }
    }
    fun loadMore() {
        val state = list.value
        if (state.loading || state.loadingMore || !state.hasMore) return
        listMutable.update { it.copy(loadingMore = true, error = null) }
        listJob = viewModelScope.launch { fetchPage(state.page + 1) }
    }
    private suspend fun fetchPage(page: Int) {
        try {
            val result = repository.mine(page = page, pageSize = PAGE_SIZE)
            listMutable.update {
                val items = if (page == 1) result.items else (it.items + result.items).distinctBy { c -> c.id }
                ComplaintListState(items = items, page = page, totalItems = result.totalItems)
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { listMutable.update { it.copy(loading = false, loadingMore = false, error = messageOf(e)) } }
    }

    fun loadDetail(id: String) {
        detailJob?.cancel()
        detailMutable.update { ComplaintDetailState(loading = true, complaint = it.complaint?.takeIf { c -> c.id == id }) }
        detailJob = viewModelScope.launch {
            try { detailMutable.value = ComplaintDetailState(complaint = repository.detail(id)) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { detailMutable.update { it.copy(loading = false, error = messageOf(e)) } }
        }
    }
    suspend fun attachmentBytes(id: String, attachmentId: String): ByteArray? = try {
        repository.attachment(id, attachmentId)
    } catch (e: CancellationException) { throw e }
    catch (_: Exception) { null }

    /** Called on logout so the next driver on this device never sees these complaints. */
    fun reset() {
        entryJob?.cancel(); listJob?.cancel(); detailJob?.cancel(); submitJob?.cancel()
        entryMutable.value = ComplaintEntryState()
        formMutable.value = ComplaintFormState()
        listMutable.value = ComplaintListState()
        detailMutable.value = ComplaintDetailState()
    }

    companion object { const val PAGE_SIZE = 20 }
}
