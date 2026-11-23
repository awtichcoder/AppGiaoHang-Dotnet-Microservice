package com.deliveryapp.customer.feature.order

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.deliveryapp.customer.core.location.ReverseGeocoder
import com.deliveryapp.customer.core.location.currentLocation
import com.deliveryapp.customer.core.location.hasLocationPermission
import com.deliveryapp.customer.data.dto.PromotionListItem
import com.deliveryapp.customer.data.repository.GeoPoint
import com.deliveryapp.customer.ui.components.AppSurface
import com.deliveryapp.customer.ui.components.AppTextField
import com.deliveryapp.customer.ui.components.DeliveryCard
import com.deliveryapp.customer.ui.components.DeliveryMap
import com.deliveryapp.customer.ui.components.DropoffColor
import com.deliveryapp.customer.ui.components.LoadingState
import com.deliveryapp.customer.ui.components.MapMarker
import com.deliveryapp.customer.ui.components.PickupColor
import com.deliveryapp.customer.ui.components.PrimaryAction
import com.deliveryapp.customer.ui.components.SecondaryAction
import com.deliveryapp.customer.ui.components.SessionExpiredDialog
import com.deliveryapp.customer.ui.components.StatusBanner
import com.deliveryapp.customer.ui.theme.DeliveryGreen
import com.deliveryapp.customer.ui.theme.DeliveryGreenSoft
import com.deliveryapp.customer.ui.theme.DeliveryMuted
import com.deliveryapp.customer.ui.theme.DeliveryText
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun CreateOrderScreen(
    viewModel: CreateOrderViewModel,
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    onSessionExpired: () -> Unit
) {
    val state = viewModel.state
    var picking by remember { mutableStateOf<PointKind?>(null) }

    LaunchedEffect(state.createdOrderId) {
        state.createdOrderId?.let { viewModel.consumeCreated(); onCreated(it) }
    }
    if (state.sessionExpired) {
        SessionExpiredDialog { viewModel.consumeSessionExpired(); onSessionExpired() }
    }
    picking?.let { kind ->
        MapPickerDialog(
            kind = kind,
            initial = if (kind == PointKind.PICKUP) state.pickup else state.dropoff,
            other = if (kind == PointKind.PICKUP) state.dropoff else state.pickup,
            onDismiss = { picking = null },
            onConfirm = { point, address -> viewModel.onPointPicked(kind, point, address); picking = null }
        )
    }

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
                Text("Tạo đơn giao hàng", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Chọn điểm lấy, điểm giao trên bản đồ và xem phí trước khi đặt.",
                    style = MaterialTheme.typography.bodyMedium, color = DeliveryMuted
                )

                RouteCard(state, viewModel, onPick = { picking = it })
                ReceiverCard(state, viewModel)
                PromotionCard(state, viewModel)
                PointsCard(state, viewModel)
                PriceCard(state)

                state.submitError?.let { StatusBanner("Chưa đặt được đơn", it, warning = true) }
                PrimaryAction(
                    label = state.total?.let { "Đặt đơn · ${vnd(it)}" } ?: "Đặt đơn",
                    onClick = viewModel::submit,
                    loading = state.submitting
                )
                SecondaryAction("Quay lại", onBack)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun RouteCard(state: CreateOrderUiState, viewModel: CreateOrderViewModel, onPick: (PointKind) -> Unit) {
    DeliveryCard {
        Text("Lộ trình", style = MaterialTheme.typography.titleLarge)
        PointInput(
            label = "A", title = "Điểm lấy hàng", color = PickupColor,
            address = state.pickupAddress, point = state.pickup, error = state.pickupError,
            onAddress = { viewModel.onAddressChange(PointKind.PICKUP, it) },
            onPick = { onPick(PointKind.PICKUP) }
        )
        PointInput(
            label = "B", title = "Điểm giao hàng", color = DropoffColor,
            address = state.dropoffAddress, point = state.dropoff, error = state.dropoffError,
            onAddress = { viewModel.onAddressChange(PointKind.DROPOFF, it) },
            onPick = { onPick(PointKind.DROPOFF) }
        )
        val markers = listOfNotNull(
            state.pickup?.let { MapMarker(it, "A", PickupColor, "Điểm lấy hàng") },
            state.dropoff?.let { MapMarker(it, "B", DropoffColor, "Điểm giao hàng") }
        )
        if (markers.size == 2) {
            DeliveryMap(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(8.dp)), markers = markers, interactive = false)
        }
    }
}

@Composable
private fun PointInput(
    label: String, title: String, color: Color,
    address: String, point: GeoPoint?, error: String?,
    onAddress: (String) -> Unit, onPick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PointBadge(label, color)
            Text(title, style = MaterialTheme.typography.titleMedium, color = DeliveryText, modifier = Modifier.weight(1f))
            TextButton(onClick = onPick) {
                Text(if (point == null) "Chọn trên bản đồ" else "Đổi vị trí", color = DeliveryGreen, fontWeight = FontWeight.SemiBold)
            }
        }
        AppTextField(value = address, onValueChange = onAddress, label = "Địa chỉ, số nhà, ghi chú", singleLine = false)
        Text(
            point?.let { "Đã ghim: ${coordinates(it)}" } ?: "Chưa chọn vị trí trên bản đồ",
            style = MaterialTheme.typography.bodyMedium,
            color = if (point == null) DeliveryMuted else DeliveryGreen
        )
        FieldError(error)
    }
}

@Composable
private fun ReceiverCard(state: CreateOrderUiState, viewModel: CreateOrderViewModel) {
    DeliveryCard {
        Text("Người nhận", style = MaterialTheme.typography.titleLarge)
        AppTextField(
            value = state.receiverPhone, onValueChange = viewModel::onPhoneChange,
            label = "Số điện thoại người nhận", error = state.phoneError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
        )
    }
}

@Composable
private fun PromotionCard(state: CreateOrderUiState, viewModel: CreateOrderViewModel) {
    DeliveryCard {
        Text("Ưu đãi", style = MaterialTheme.typography.titleLarge)
        if (state.promotions.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.promotions.forEach { promotion ->
                    FilterChip(
                        selected = state.promotionCode == promotion.code,
                        onClick = { viewModel.onPromotionToggle(promotion.code) },
                        label = {
                            Column(Modifier.padding(vertical = 6.dp)) {
                                Text(promotion.code, fontWeight = FontWeight.Bold)
                                Text(promotionSummary(promotion), style = MaterialTheme.typography.bodyMedium)
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DeliveryGreenSoft, selectedLabelColor = DeliveryGreen
                        )
                    )
                }
            }
        }
        AppTextField(value = state.promotionCode, onValueChange = viewModel::onPromotionChange, label = "Nhập mã ưu đãi")
        FieldError(state.promotionError)
        val discount = state.preview?.discountPromotion ?: 0
        if (state.promotionCode.isNotBlank() && state.promotionError == null && discount > 0) {
            Text("Đã áp dụng: giảm ${vnd(discount)}", color = DeliveryGreen, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PointsCard(state: CreateOrderUiState, viewModel: CreateOrderViewModel) {
    val available = state.availablePoints ?: return
    DeliveryCard {
        Text("Điểm thưởng", style = MaterialTheme.typography.titleLarge)
        Text(
            "Bạn có $available điểm khả dụng · 1 điểm = 1.000đ",
            style = MaterialTheme.typography.bodyMedium, color = DeliveryMuted
        )
        if (available > 0) {
            AppTextField(
                value = state.pointsText, onValueChange = viewModel::onPointsChange,
                label = "Số điểm muốn dùng", error = state.pointsError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            if (state.pointsDiscount > 0 && state.pointsError == null) {
                Text("Giảm ${vnd(state.pointsDiscount)} bằng điểm", color = DeliveryGreen, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun PriceCard(state: CreateOrderUiState) {
    DeliveryCard {
        Text("Chi phí", style = MaterialTheme.typography.titleLarge)
        val preview = state.preview
        val total = state.total
        when {
            state.pickup == null || state.dropoff == null -> Text(
                "Chọn điểm lấy và điểm giao để xem phí giao hàng.",
                style = MaterialTheme.typography.bodyMedium, color = DeliveryMuted
            )
            preview == null && state.previewLoading -> LoadingState("Đang tính phí")
            preview == null -> Text(state.previewError ?: "Chưa tính được phí.", style = MaterialTheme.typography.bodyMedium, color = DeliveryMuted)
            total != null -> {
                PriceBreakdown(preview, state.pointsDiscount, total)
                if (state.previewLoading) Text("Đang cập nhật…", style = MaterialTheme.typography.bodyMedium, color = DeliveryMuted)
            }
        }
    }
}

/** Full-screen map: the customer drags the map under a fixed centre pin, then confirms. */
@Composable
private fun MapPickerDialog(
    kind: PointKind,
    initial: GeoPoint?,
    other: GeoPoint?,
    onDismiss: () -> Unit,
    onConfirm: (GeoPoint, String?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pickup = kind == PointKind.PICKUP
    val color = if (pickup) PickupColor else DropoffColor
    var center by remember { mutableStateOf(initial ?: other ?: com.deliveryapp.customer.ui.components.HoChiMinhCity) }
    var focus by remember { mutableStateOf(initial ?: other) }
    var locating by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun locate() {
        scope.launch {
            locating = true; message = null
            val point = currentLocation(context)
            locating = false
            if (point == null) message = "Không lấy được vị trí hiện tại. Hãy bật GPS rồi thử lại."
            else { focus = point; center = point }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) locate() else message = "Cần quyền vị trí để dùng vị trí hiện tại."
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (pickup) "Chọn điểm lấy hàng" else "Chọn điểm giao hàng", style = MaterialTheme.typography.headlineMedium)
                    Text("Kéo bản đồ để đặt ghim đúng vị trí.", style = MaterialTheme.typography.bodyMedium, color = DeliveryMuted)
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    DeliveryMap(
                        Modifier.fillMaxSize(),
                        markers = listOfNotNull(other?.let {
                            MapMarker(it, if (pickup) "B" else "A", if (pickup) DropoffColor else PickupColor, "Điểm còn lại")
                        }),
                        focus = focus,
                        onCenterChange = { center = it }
                    )
                    CenterPin(if (pickup) "A" else "B", color, Modifier.align(Alignment.Center))
                }
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Vị trí: ${coordinates(center)}", style = MaterialTheme.typography.bodyMedium, color = DeliveryMuted)
                    message?.let { FieldError(it) }
                    SecondaryAction(
                        label = if (locating) "Đang lấy vị trí…" else "Dùng vị trí hiện tại",
                        onClick = {
                            if (hasLocationPermission(context)) locate()
                            else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        },
                        enabled = !locating
                    )
                    PrimaryAction(
                        label = "Xác nhận vị trí này",
                        loading = confirming,
                        onClick = {
                            scope.launch {
                                confirming = true
                                val point = center
                                onConfirm(point, ReverseGeocoder.address(point))
                                confirming = false
                            }
                        }
                    )
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("Hủy", color = DeliveryMuted)
                    }
                }
            }
        }
    }
}

/** The pin's tip sits exactly on the map centre. */
@Composable
private fun CenterPin(label: String, color: Color, modifier: Modifier) {
    Column(modifier.offset(y = (-22).dp), horizontalAlignment = Alignment.CenterHorizontally) {
        PointBadge(label, color, Modifier.size(32.dp))
        Box(Modifier.width(3.dp).height(12.dp).background(color))
    }
}

private fun coordinates(point: GeoPoint) =
    String.format(Locale.ROOT, "%.5f, %.5f", point.latitude, point.longitude)

private fun promotionSummary(promotion: PromotionListItem): String {
    val value = promotion.discountAmount?.let { "Giảm ${vnd(it.toLong())}" }
        ?: promotion.discountPercent?.let { percent ->
            "Giảm ${percent.toInt()}%" + (promotion.maxDiscount?.let { " (tối đa ${vnd(it.toLong())})" } ?: "")
        } ?: promotion.description
    return if (promotion.minOrderAmount > 0) "$value · đơn từ ${vnd(promotion.minOrderAmount.toLong())}" else value
}
