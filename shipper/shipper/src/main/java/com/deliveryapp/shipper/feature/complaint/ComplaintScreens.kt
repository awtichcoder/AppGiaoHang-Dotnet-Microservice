package com.deliveryapp.shipper.feature.complaint

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deliveryapp.shipper.data.model.*
import com.deliveryapp.shipper.ui.components.*
import com.deliveryapp.shipper.ui.theme.*
import kotlinx.coroutines.launch

private fun shortOrder(orderId: String) = if (orderId.length > 12) orderId.take(8).uppercase() else orderId

@Composable
private fun ComplaintBadge(status: String) {
    val warning = ComplaintStatusLabels.isWarning(status)
    StatusBadge(ComplaintStatusLabels.label(status),
        color = if (warning) DeliveryDanger else DeliveryGreenDark,
        background = if (warning) Color(0xFFFFEEEC) else DeliveryGreenSoft,
        icon = if (status == "RESOLVED") Icons.Outlined.CheckCircle else Icons.Outlined.ReportProblem)
}

/** Shown on a completed trip: report button within 24 hours, or the status of the existing report. */
@Composable
fun ComplaintEntry(viewModel: ComplaintViewModel, trip: Trip, onReport: () -> Unit, onOpen: (String) -> Unit) {
    if (trip.status != TripStatus.COMPLETED) return
    LaunchedEffect(trip.orderId) { viewModel.loadEntry(trip.orderId) }
    val state by viewModel.entry.collectAsStateWithLifecycle()
    if (state.orderId != trip.orderId) return
    val existing = state.existing
    when {
        existing != null -> DeliveryCard {
            Text("Báo cáo sự cố của chuyến này", style = MaterialTheme.typography.titleMedium)
            ComplaintBadge(existing.status)
            SecondaryAction("Xem báo cáo", { onOpen(existing.id) }, Modifier.testTag("complaint_open"))
        }
        state.error != null -> ErrorState(state.error!!) { viewModel.loadEntry(trip.orderId) }
        viewModel.canReport(trip.orderId, trip.completedAt) ->
            SecondaryAction("Báo cáo sự cố", onReport, Modifier.testTag("complaint_report"), icon = Icons.Outlined.ReportProblem)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComplaintFormScreen(viewModel: ComplaintViewModel, orderId: String, onBack: () -> Unit, onCreated: (String) -> Unit) {
    LaunchedEffect(orderId) { viewModel.startForm(orderId) }
    val state by viewModel.form.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reading by remember { mutableStateOf(false) }
    // The contract needs maxItems > 1, so it stays at 3 and the ViewModel enforces the remaining room.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(ComplaintLimits.MAX_IMAGES)) { uris ->
        if (uris.isNotEmpty()) {
            reading = true
            scope.launch {
                viewModel.addPicked(uris.map { readComplaintImage(context, it) })
                reading = false
            }
        }
    }
    LaunchedEffect(state.createdId) { state.createdId?.let { viewModel.consumeCreated(); onCreated(it) } }

    AppScreen("Báo cáo sự cố", "Chuyến ${shortOrder(orderId)}", onBack = onBack) {
        DemoBadge()
        DeliveryCard {
            Text("Loại vấn đề", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ComplaintCategory.entries.forEach { category ->
                    FilterChip(state.category == category.name, { viewModel.selectCategory(category.name) }, label = { Text(category.label) })
                }
            }
            state.categoryError?.let { Text(it, color = DeliveryDanger, style = MaterialTheme.typography.bodySmall) }
        }
        DeliveryCard {
            Text("Mô tả", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(state.description, viewModel::updateDescription, Modifier.fillMaxWidth().testTag("complaint_description"),
                label = { Text("Mô tả sự cố bạn gặp phải") }, isError = state.descriptionError != null, minLines = 3)
            state.descriptionError?.let { Text(it, color = DeliveryDanger, style = MaterialTheme.typography.bodySmall) }
            Text("${state.description.length}/${ComplaintLimits.MAX_DESCRIPTION}", style = MaterialTheme.typography.bodySmall, color = DeliveryMuted)
        }
        DeliveryCard {
            Text("Ảnh đính kèm", style = MaterialTheme.typography.titleMedium)
            Text("Tối đa ${ComplaintLimits.MAX_IMAGES} ảnh JPEG hoặc PNG, mỗi ảnh tối đa 5 MB.", style = MaterialTheme.typography.bodySmall, color = DeliveryMuted)
            state.images.forEachIndexed { index, image ->
                PickedImageRow(image, !state.submitting) { viewModel.removeImage(index) }
            }
            state.imageError?.let { Text(it, color = DeliveryDanger, style = MaterialTheme.typography.bodySmall) }
            if (state.images.size < ComplaintLimits.MAX_IMAGES) {
                SecondaryAction(if (reading) "Đang đọc ảnh…" else "Chọn ảnh",
                    { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !reading && !state.submitting, icon = Icons.Outlined.AddPhotoAlternate)
            }
        }
        state.submitError?.let { ErrorState(it) }
        PrimaryAction("Gửi báo cáo", viewModel::submit, Modifier.testTag("complaint_submit"),
            enabled = !state.blocked && !reading, loading = state.submitting)
    }
}

@Composable
private fun PickedImageRow(image: ComplaintImage, enabled: Boolean, onRemove: () -> Unit) {
    val bitmap by produceState<ImageBitmap?>(null, image) { value = decodeThumbnail(image.bytes, 240) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(DeliveryBorder), contentAlignment = Alignment.Center) {
            bitmap?.let { Image(it, "Ảnh đã chọn", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        Text(image.fileName, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
        TextButton(onRemove, enabled = enabled) { Text("Xóa", color = DeliveryDanger) }
    }
}

@Composable
fun ComplaintListScreen(viewModel: ComplaintViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    LaunchedEffect(Unit) { viewModel.loadList() }
    val state by viewModel.list.collectAsStateWithLifecycle()
    AppScreen("Báo cáo của tôi", "Theo dõi trạng thái các báo cáo sự cố", onBack = onBack) {
        DemoBadge()
        when {
            state.loading && state.items.isEmpty() -> LoadingState("Đang tải báo cáo…")
            state.error != null && state.items.isEmpty() -> ErrorState(state.error!!, viewModel::loadList)
            state.items.isEmpty() -> EmptyState("Chưa có báo cáo", "Bạn có thể báo cáo sự cố từ chi tiết chuyến đã hoàn thành.", Icons.Outlined.ReportProblem)
            else -> {
                state.items.forEach { complaint ->
                    DeliveryCard(Modifier.clickable { onOpen(complaint.id) }) {
                        Text("Chuyến ${shortOrder(complaint.orderId)}", style = MaterialTheme.typography.titleMedium)
                        ComplaintBadge(complaint.status)
                        Text(ComplaintCategory.labelOf(complaint.category), style = MaterialTheme.typography.bodyMedium)
                        Text(dateLabel(complaint.createdAt), style = MaterialTheme.typography.bodySmall, color = DeliveryMuted)
                    }
                }
                state.error?.let { ErrorState(it) }
                if (state.hasMore) SecondaryAction(if (state.loadingMore) "Đang tải…" else "Xem thêm", viewModel::loadMore, enabled = !state.loadingMore)
            }
        }
    }
}

@Composable
fun ComplaintDetailScreen(viewModel: ComplaintViewModel, complaintId: String, onBack: () -> Unit) {
    LaunchedEffect(complaintId) { viewModel.loadDetail(complaintId) }
    val state by viewModel.detail.collectAsStateWithLifecycle()
    val complaint = state.complaint?.takeIf { it.id == complaintId }
    AppScreen("Chi tiết báo cáo", complaint?.let { "Chuyến ${shortOrder(it.orderId)}" }, onBack = onBack) {
        DemoBadge()
        when {
            complaint != null -> {
                ComplaintBadge(complaint.status)
                DeliveryCard {
                    InfoRow("Loại vấn đề", ComplaintCategory.labelOf(complaint.category))
                    InfoRow("Gửi lúc", dateLabel(complaint.createdAt))
                    InfoRow("Cập nhật", dateLabel(complaint.updatedAt))
                    HorizontalDivider(color = DeliveryBorder)
                    Text(complaint.description, style = MaterialTheme.typography.bodyMedium)
                }
                complaint.adminNote?.let { note ->
                    SectionTitle("Ghi chú của quản trị viên")
                    DeliveryCard { Text(note, style = MaterialTheme.typography.bodyMedium) }
                }
                SectionTitle("Ảnh đính kèm")
                DeliveryCard {
                    if (complaint.attachments.isEmpty()) {
                        Text("Không có ảnh. Ảnh được tự động xóa sau 30 ngày kể từ khi báo cáo kết thúc.",
                            style = MaterialTheme.typography.bodySmall, color = DeliveryMuted)
                    }
                    complaint.attachments.forEach { AttachmentImage(viewModel, complaint.id, it) }
                }
            }
            state.error != null -> ErrorState(state.error!!) { viewModel.loadDetail(complaintId) }
            else -> LoadingState("Đang tải báo cáo…")
        }
    }
}

private sealed interface AttachmentLoad {
    data object Loading : AttachmentLoad
    data object Failed : AttachmentLoad
    data class Ready(val bitmap: ImageBitmap) : AttachmentLoad
}

@Composable
private fun AttachmentImage(viewModel: ComplaintViewModel, complaintId: String, attachment: ComplaintAttachment) {
    val load by produceState<AttachmentLoad>(AttachmentLoad.Loading, attachment.id) {
        val bitmap = viewModel.attachmentBytes(complaintId, attachment.id)?.let { decodeThumbnail(it) }
        value = bitmap?.let { AttachmentLoad.Ready(it) } ?: AttachmentLoad.Failed
    }
    Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(12.dp)).background(DeliveryBorder), contentAlignment = Alignment.Center) {
        when (val current = load) {
            AttachmentLoad.Loading -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
            AttachmentLoad.Failed -> Text("Không tải được ảnh", style = MaterialTheme.typography.bodySmall, color = DeliveryMuted)
            is AttachmentLoad.Ready -> Image(current.bitmap, "Ảnh đính kèm ${attachment.fileName}", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
    }
}
