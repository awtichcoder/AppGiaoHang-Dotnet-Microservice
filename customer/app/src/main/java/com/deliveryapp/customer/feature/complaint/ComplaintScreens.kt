package com.deliveryapp.customer.feature.complaint

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.deliveryapp.customer.data.dto.ComplaintAttachmentResponse
import com.deliveryapp.customer.data.dto.ComplaintResponse
import com.deliveryapp.customer.data.repository.ComplaintImage
import com.deliveryapp.customer.feature.order.FieldError
import com.deliveryapp.customer.feature.order.orderCode
import com.deliveryapp.customer.feature.order.shortDateTime
import com.deliveryapp.customer.ui.components.AppSurface
import com.deliveryapp.customer.ui.components.AppTextField
import com.deliveryapp.customer.ui.components.DeliveryCard
import com.deliveryapp.customer.ui.components.EmptyState
import com.deliveryapp.customer.ui.components.ErrorState
import com.deliveryapp.customer.ui.components.InfoRow
import com.deliveryapp.customer.ui.components.LoadingState
import com.deliveryapp.customer.ui.components.PrimaryAction
import com.deliveryapp.customer.ui.components.SecondaryAction
import com.deliveryapp.customer.ui.components.SessionExpiredDialog
import com.deliveryapp.customer.ui.components.StatusBanner
import com.deliveryapp.customer.ui.theme.DeliveryBorder
import com.deliveryapp.customer.ui.theme.DeliveryDanger
import com.deliveryapp.customer.ui.theme.DeliveryGreen
import com.deliveryapp.customer.ui.theme.DeliveryGreenSoft
import com.deliveryapp.customer.ui.theme.DeliveryMuted
import com.deliveryapp.customer.ui.theme.DeliveryText
import com.deliveryapp.customer.ui.theme.DeliveryWarning
import kotlinx.coroutines.launch

@Composable
private fun ComplaintFrame(title: String, subtitle: String, content: @Composable () -> Unit) {
    AppSurface {
        Scaffold { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(title, style = MaterialTheme.typography.headlineMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = DeliveryMuted)
                content()
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ComplaintStatusChip(status: String) {
    val warning = ComplaintStatuses.isWarning(status)
    Text(
        ComplaintStatuses.label(status),
        Modifier
            .background(if (warning) DeliveryWarning else DeliveryGreenSoft, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        color = if (warning) DeliveryDanger else DeliveryGreen,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold
    )
}

/** Shown on a completed order: send button within 24 hours, or the status of the existing complaint. */
@Composable
fun ComplaintEntryCard(
    viewModel: ComplaintEntryViewModel,
    completedAt: String?,
    onCreate: () -> Unit,
    onOpen: (String) -> Unit,
    onSessionExpired: () -> Unit
) {
    val state = viewModel.state
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Runs again when coming back from the form, so the button is replaced by the status.
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.refresh() }
    }
    if (state.sessionExpired) {
        SessionExpiredDialog { viewModel.consumeSessionExpired(); onSessionExpired() }
    }
    val existing = state.existing
    when {
        existing != null -> DeliveryCard {
            Text("Khiếu nại của đơn này", style = MaterialTheme.typography.titleLarge)
            ComplaintStatusChip(existing.status)
            SecondaryAction("Xem khiếu nại", onClick = { onOpen(existing.id) })
        }
        state.errorMessage != null -> DeliveryCard {
            Text("Chưa kiểm tra được khiếu nại", style = MaterialTheme.typography.titleMedium)
            Text(state.errorMessage, style = MaterialTheme.typography.bodyMedium, color = DeliveryMuted)
            SecondaryAction("Thử lại", viewModel::reload)
        }
        viewModel.canCreate(completedAt) -> DeliveryCard {
            Text("Đơn hàng có vấn đề?", style = MaterialTheme.typography.titleLarge)
            Text(
                "Bạn có thể gửi khiếu nại trong vòng 24 giờ kể từ khi đơn hoàn thành.",
                style = MaterialTheme.typography.bodyMedium, color = DeliveryMuted
            )
            SecondaryAction("Khiếu nại", onCreate)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComplaintFormScreen(
    viewModel: ComplaintFormViewModel,
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    onSessionExpired: () -> Unit
) {
    val state = viewModel.state
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var readingImages by remember { mutableStateOf(false) }
    // The contract needs maxItems > 1, so it stays at 3 and the ViewModel enforces the remaining room.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(ComplaintLimits.MAX_IMAGES)
    ) { uris ->
        if (uris.isNotEmpty()) {
            readingImages = true
            scope.launch {
                viewModel.addPicked(uris.map { readComplaintImage(context, it) })
                readingImages = false
            }
        }
    }
    LaunchedEffect(state.createdId) {
        state.createdId?.let { viewModel.consumeCreated(); onCreated(it) }
    }
    if (state.sessionExpired) {
        SessionExpiredDialog { viewModel.consumeSessionExpired(); onSessionExpired() }
    }

    ComplaintFrame("Khiếu nại", "Đơn ${orderCode(viewModel.orderId)}") {
        DeliveryCard {
            Text("Loại vấn đề", style = MaterialTheme.typography.titleLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ComplaintCategories.all.forEach { (code, label) ->
                    FilterChip(
                        selected = state.category == code,
                        onClick = { viewModel.selectCategory(code) },
                        label = { Text(label) }
                    )
                }
            }
            FieldError(state.categoryError)
        }
        DeliveryCard {
            Text("Mô tả", style = MaterialTheme.typography.titleLarge)
            AppTextField(
                value = state.description,
                onValueChange = viewModel::updateDescription,
                label = "Mô tả vấn đề bạn gặp phải",
                error = state.descriptionError,
                singleLine = false
            )
            Text(
                "${state.description.length}/${ComplaintLimits.MAX_DESCRIPTION}",
                style = MaterialTheme.typography.bodyMedium, color = DeliveryMuted
            )
        }
        DeliveryCard {
            Text("Ảnh đính kèm", style = MaterialTheme.typography.titleLarge)
            Text(
                "Tối đa ${ComplaintLimits.MAX_IMAGES} ảnh JPEG hoặc PNG, mỗi ảnh tối đa 5 MB.",
                style = MaterialTheme.typography.bodyMedium, color = DeliveryMuted
            )
            state.images.forEachIndexed { index, image ->
                PickedImageRow(image, onRemove = { viewModel.removeImage(index) }, enabled = !state.isSubmitting)
            }
            FieldError(state.imageError)
            if (state.images.size < ComplaintLimits.MAX_IMAGES) {
                SecondaryAction(
                    label = if (readingImages) "Đang đọc ảnh…" else "Chọn ảnh",
                    onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    enabled = !readingImages && !state.isSubmitting
                )
            }
        }
        state.submitError?.let { StatusBanner("Chưa gửi được khiếu nại", it, warning = true) }
        PrimaryAction(
            label = "Gửi khiếu nại",
            onClick = viewModel::submit,
            enabled = !state.isBlocked && !readingImages,
            loading = state.isSubmitting
        )
        SecondaryAction("Quay lại", onBack, enabled = !state.isSubmitting)
    }
}

@Composable
private fun PickedImageRow(image: ComplaintImage, onRemove: () -> Unit, enabled: Boolean) {
    val bitmap by produceState<ImageBitmap?>(null, image) { value = decodeThumbnail(image.bytes, 240) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(DeliveryBorder),
            contentAlignment = Alignment.Center
        ) {
            bitmap?.let {
                Image(it, contentDescription = "Ảnh đã chọn", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        Text(image.fileName, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = DeliveryText, maxLines = 1)
        TextButton(onClick = onRemove, enabled = enabled) { Text("Xóa", color = DeliveryDanger) }
    }
}

@Composable
fun ComplaintListScreen(
    viewModel: ComplaintListViewModel,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
    onSessionExpired: () -> Unit
) {
    val state = viewModel.state
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.refresh() }
    }
    if (state.sessionExpired) {
        SessionExpiredDialog { viewModel.consumeSessionExpired(); onSessionExpired() }
    }
    ComplaintFrame("Khiếu nại của tôi", "Theo dõi trạng thái các khiếu nại đã gửi.") {
        when {
            state.isLoading && state.items.isEmpty() -> LoadingState("Đang tải khiếu nại")
            state.errorMessage != null && state.items.isEmpty() -> ErrorState(state.errorMessage, viewModel::reload)
            state.items.isEmpty() -> EmptyState(
                "Chưa có khiếu nại",
                "Bạn có thể gửi khiếu nại từ màn hình chi tiết của đơn đã hoàn thành."
            )
            else -> {
                state.items.forEach { ComplaintRow(it, onOpen) }
                state.errorMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = DeliveryDanger) }
                if (state.hasMore) {
                    SecondaryAction(
                        label = if (state.isLoadingMore) "Đang tải…" else "Xem thêm",
                        onClick = viewModel::loadMore,
                        enabled = !state.isLoadingMore
                    )
                }
            }
        }
        SecondaryAction("Quay lại", onBack)
    }
}

@Composable
private fun ComplaintRow(complaint: ComplaintResponse, onOpen: (String) -> Unit) {
    DeliveryCard(Modifier.clickable { onOpen(complaint.id) }) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(orderCode(complaint.orderId), style = MaterialTheme.typography.titleMedium)
            ComplaintStatusChip(complaint.status)
        }
        Text(ComplaintCategories.label(complaint.category), style = MaterialTheme.typography.bodyMedium, color = DeliveryText)
        Text("Gửi lúc ${shortDateTime(complaint.createdAt)}", style = MaterialTheme.typography.bodyMedium, color = DeliveryMuted)
    }
}

@Composable
fun ComplaintDetailScreen(
    viewModel: ComplaintDetailViewModel,
    onBack: () -> Unit,
    onSessionExpired: () -> Unit
) {
    val state = viewModel.state
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.refresh() }
    }
    if (state.sessionExpired) {
        SessionExpiredDialog { viewModel.consumeSessionExpired(); onSessionExpired() }
    }
    val complaint = state.complaint
    ComplaintFrame("Chi tiết khiếu nại", complaint?.let { "Đơn ${orderCode(it.orderId)}" } ?: "") {
        when {
            complaint != null -> {
                StatusBanner(
                    title = ComplaintStatuses.label(complaint.status),
                    subtitle = "Cập nhật lúc ${shortDateTime(complaint.updatedAt)}",
                    warning = ComplaintStatuses.isWarning(complaint.status)
                )
                DeliveryCard {
                    Text("Nội dung", style = MaterialTheme.typography.titleLarge)
                    InfoRow("Loại vấn đề", ComplaintCategories.label(complaint.category))
                    InfoRow("Gửi lúc", shortDateTime(complaint.createdAt))
                    Text(complaint.description, style = MaterialTheme.typography.bodyLarge, color = DeliveryText)
                }
                complaint.adminNote?.let { note ->
                    DeliveryCard {
                        Text("Ghi chú của quản trị viên", style = MaterialTheme.typography.titleLarge)
                        Text(note, style = MaterialTheme.typography.bodyLarge, color = DeliveryText)
                    }
                }
                DeliveryCard {
                    Text("Ảnh đính kèm", style = MaterialTheme.typography.titleLarge)
                    if (complaint.attachments.isEmpty()) {
                        Text(
                            "Không có ảnh. Ảnh được tự động xóa sau 30 ngày kể từ khi khiếu nại kết thúc.",
                            style = MaterialTheme.typography.bodyMedium, color = DeliveryMuted
                        )
                    }
                    complaint.attachments.forEach { AttachmentImage(viewModel, it) }
                }
            }
            state.errorMessage != null -> ErrorState(state.errorMessage, viewModel::reload)
            else -> LoadingState("Đang tải khiếu nại")
        }
        SecondaryAction("Quay lại", onBack)
    }
}

private sealed interface AttachmentLoad {
    data object Loading : AttachmentLoad
    data object Failed : AttachmentLoad
    data class Ready(val bitmap: ImageBitmap) : AttachmentLoad
}

@Composable
private fun AttachmentImage(viewModel: ComplaintDetailViewModel, attachment: ComplaintAttachmentResponse) {
    val load by produceState<AttachmentLoad>(AttachmentLoad.Loading, attachment.id) {
        val bitmap = viewModel.attachmentBytes(attachment.id)?.let { decodeThumbnail(it) }
        value = bitmap?.let { AttachmentLoad.Ready(it) } ?: AttachmentLoad.Failed
    }
    Box(
        Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(8.dp)).background(DeliveryBorder),
        contentAlignment = Alignment.Center
    ) {
        when (val current = load) {
            AttachmentLoad.Loading -> CircularProgressIndicator(color = DeliveryGreen)
            AttachmentLoad.Failed -> Text("Không tải được ảnh", style = MaterialTheme.typography.bodyMedium, color = DeliveryMuted)
            is AttachmentLoad.Ready -> Image(
                current.bitmap,
                contentDescription = "Ảnh đính kèm ${attachment.fileName}",
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}
