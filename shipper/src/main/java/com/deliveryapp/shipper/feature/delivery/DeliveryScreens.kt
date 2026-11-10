package com.deliveryapp.shipper.feature.delivery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.deliveryapp.shipper.data.model.*
import com.deliveryapp.shipper.feature.work.WorkUiState
import com.deliveryapp.shipper.ui.components.*
import com.deliveryapp.shipper.ui.theme.*

@Composable
fun TripAction(trip: Trip, loading: Boolean, onAdvance: (TripStatus) -> Unit) {
    var confirmation by rememberSaveable { mutableStateOf(false) }
    val action = trip.status.action ?: return
    if (confirmation) ConfirmDialog(action,
        when (trip.status) {
            TripStatus.ASSIGNED -> "Bạn đã nhận đúng kiện hàng tại điểm lấy?"
            TripStatus.PICKED_UP -> "Bạn đã kiểm tra hàng và sẵn sàng đi đến người nhận?"
            else -> "Bạn đã bàn giao hàng thành công cho người nhận?"
        }, onDismiss = { confirmation = false }, onConfirm = { confirmation = false; onAdvance(trip.status) })
    PrimaryAction(action, { confirmation = true }, Modifier.testTag("advance_trip"), loading = loading, icon = Icons.Outlined.CheckCircle)
}

@Composable
fun DetailScreen(trip: Trip?, state: WorkUiState, readOnly: Boolean, onBack: () -> Unit, onMap: () -> Unit, onAdvance: (TripStatus) -> Unit) {
    AppScreen("Chi tiết chuyến", onBack = onBack) {
        DemoBadge()
        if (trip == null) EmptyState("Không có chuyến đang giao", "Về trang chủ để xem lời mời mới.")
        else {
            Text(trip.shortId, style = MaterialTheme.typography.headlineMedium)
            TripBadge(trip.status)
            Text(dateLabel(trip.displayTime), style = MaterialTheme.typography.bodySmall, color = DeliveryMuted)
            SectionTitle("Lộ trình giao hàng")
            DeliveryCard { RouteStops(trip.pickupAddress, trip.dropoffAddress) }
            SectionTitle("Thông tin chuyến")
            DeliveryCard {
                InfoRow("Mã đơn", trip.orderId)
                trip.receiverPhone?.let { InfoRow("Điện thoại người nhận", it) }
                trip.totalFee?.let { InfoRow("Phí giao hàng", money(it)) }
                Text("Phí của đơn hàng, không phải thu nhập thực nhận của tài xế.", style = MaterialTheme.typography.bodySmall, color = DeliveryMuted)
            }
            if (!readOnly && trip.active) {
                SecondaryAction("Xem bản đồ & chỉ đường", onMap, Modifier.testTag("open_map"), icon = Icons.Outlined.Map)
                state.error?.let { ErrorState(it) }
                TripAction(trip, state.loading, onAdvance)
            }
        }
    }
}
