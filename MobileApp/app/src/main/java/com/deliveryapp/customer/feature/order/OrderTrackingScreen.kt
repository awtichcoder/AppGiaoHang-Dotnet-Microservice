package com.deliveryapp.customer.feature.order

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.deliveryapp.customer.data.dto.DeliveryResponse
import com.deliveryapp.customer.data.dto.OrderResponse
import com.deliveryapp.customer.data.repository.GeoPoint
import com.deliveryapp.customer.ui.components.AppSurface
import com.deliveryapp.customer.ui.components.DeliveryCard
import com.deliveryapp.customer.ui.components.DeliveryMap
import com.deliveryapp.customer.ui.components.DriverColor
import com.deliveryapp.customer.ui.components.DropoffColor
import com.deliveryapp.customer.ui.components.ErrorState
import com.deliveryapp.customer.ui.components.InfoRow
import com.deliveryapp.customer.ui.components.LoadingState
import com.deliveryapp.customer.ui.components.MapMarker
import com.deliveryapp.customer.ui.components.PickupColor
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
import kotlinx.coroutines.delay

@Composable
fun OrderTrackingScreen(
    viewModel: OrderTrackingViewModel,
    onOrders: () -> Unit,
    onReorder: () -> Unit,
    onSessionExpired: () -> Unit
) {
    val state = viewModel.state
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Poll only while the screen is visible, and stop once the order reaches a final status.
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                viewModel.refresh()
                if (!viewModel.shouldPoll) break
                delay(OrderTrackingViewModel.POLL_INTERVAL_MS)
            }
        }
    }
    if (state.sessionExpired) {
        SessionExpiredDialog { viewModel.consumeSessionExpired(); onSessionExpired() }
    }
    if (state.confirmCancel) {
        AlertDialog(
            onDismissRequest = viewModel::dismissCancel,
            title = { Text("Hủy đơn hàng?") },
            text = { Text("Mã ưu đãi và điểm đã dùng cho đơn sẽ được hoàn lại.") },
            confirmButton = { TextButton(onClick = viewModel::cancel) { Text("Hủy đơn", color = DeliveryDanger) } },
            dismissButton = { TextButton(onClick = viewModel::dismissCancel) { Text("Giữ đơn", color = DeliveryText) } }
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
                val order = state.order
                Text("Theo dõi đơn hàng", style = MaterialTheme.typography.headlineMedium)
                Text(
                    order?.let { "${orderCode(it.orderId)} · Đặt lúc ${shortDateTime(it.createdAt)}" } ?: orderCode(viewModel.orderId),
                    style = MaterialTheme.typography.bodyMedium, color = DeliveryMuted
                )
                state.refreshWarning?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = DeliveryDanger) }
                when {
                    order != null -> TrackingContent(state, order, viewModel, onReorder)
                    state.errorMessage != null -> ErrorState(state.errorMessage, viewModel::reload)
                    else -> LoadingState("Đang tải đơn hàng")
                }
                SecondaryAction("Về danh sách đơn", onOrders)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun TrackingContent(
    state: OrderTrackingUiState,
    order: OrderResponse,
    viewModel: OrderTrackingViewModel,
    onReorder: () -> Unit
) {
    StatusBanner(
        title = OrderStatuses.label(order.status),
        subtitle = OrderStatuses.description(order.status),
        warning = OrderStatuses.isWarning(order.status)
    )

    if (order.status == OrderStatuses.PENDING) SearchingCard(state)
    TrackingMapCard(state, order)
    ProgressCard(order, state.delivery)

    DeliveryCard {
        Text("Lộ trình", style = MaterialTheme.typography.titleLarge)
        RouteSummary(order.pickupAddress, order.dropoffAddress)
        InfoRow("SĐT người nhận", order.receiverPhone)
    }
    DeliveryCard {
        Text("Chi phí", style = MaterialTheme.typography.titleLarge)
        OrderFees(order)
        if (order.refundStatus == "PENDING") {
            Text("Ưu đãi và điểm đang được hoàn lại.", style = MaterialTheme.typography.bodyMedium, color = DeliveryMuted)
        }
    }

    state.cancelError?.let { StatusBanner("Không hủy được đơn", it, warning = true) }
    if (order.status in OrderStatuses.cancellable) {
        SecondaryAction(
            label = if (state.cancelling) "Đang hủy…" else "Hủy đơn",
            onClick = viewModel::askCancel, enabled = !state.cancelling, danger = true
        )
    }
    if (order.status in OrderStatuses.reorderable) {
        PrimaryAction("Đặt lại đơn này", onClick = { viewModel.prepareReorder(); onReorder() })
    }
}

@Composable
private fun SearchingCard(state: OrderTrackingUiState) {
    DeliveryCard {
        Text("Đang tìm tài xế", style = MaterialTheme.typography.titleLarge)
        LinearProgressIndicator(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(50)),
            color = DeliveryGreen, trackColor = DeliveryGreenSoft
        )
        val matching = state.matching
        if (matching != null) {
            InfoRow("Bán kính tìm", km(matching.currentRadiusKm))
            InfoRow("Tài xế đã mời", matching.invitedDrivers.toString())
        } else {
            Text("Đơn đang được chuyển cho hệ thống điều phối…", style = MaterialTheme.typography.bodyMedium, color = DeliveryMuted)
        }
    }
}

@Composable
private fun TrackingMapCard(state: OrderTrackingUiState, order: OrderResponse) {
    val pickup = state.delivery.point(pickup = true) ?: return
    val dropoff = state.delivery.point(pickup = false) ?: return
    val driver = state.driverLocation
    val markers = listOfNotNull(
        MapMarker(pickup, "A", PickupColor, "Điểm lấy hàng"),
        MapMarker(dropoff, "B", DropoffColor, "Điểm giao hàng"),
        driver?.let { MapMarker(GeoPoint(it.latitude, it.longitude), "TX", DriverColor, "Tài xế") }
    )
    DeliveryCard {
        Text("Bản đồ", style = MaterialTheme.typography.titleLarge)
        DeliveryMap(Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(8.dp)), markers = markers, interactive = false)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Legend(PickupColor, "Lấy hàng"); Legend(DropoffColor, "Giao hàng")
            if (order.status in OrderStatuses.trackable) Legend(DriverColor, "Tài xế")
        }
        when {
            driver != null -> Text(
                "Vị trí tài xế cập nhật lúc ${shortTime(driver.recordedAt)}",
                style = MaterialTheme.typography.bodyMedium, color = DeliveryMuted
            )
            state.locationNote != null -> Text(state.locationNote, style = MaterialTheme.typography.bodyMedium, color = DeliveryMuted)
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = DeliveryMuted)
    }
}

private data class Step(val label: String, val time: String?, val warning: Boolean = false)

@Composable
private fun ProgressCard(order: OrderResponse, delivery: DeliveryResponse?) {
    val steps = buildList {
        add(Step("Đặt đơn", order.createdAt))
        add(Step("Tài xế nhận đơn", delivery?.assignedAt))
        add(Step("Đã lấy hàng", delivery?.pickedUpAt))
        add(Step("Đang giao hàng", delivery?.deliveringAt))
        add(Step("Giao thành công", delivery?.completedAt))
    }.let { base ->
        when (order.status) {
            // The flow stopped: show only what happened, then the reason.
            OrderStatuses.CANCELLED -> base.filter { it.time != null } +
                Step("Đã hủy", delivery?.cancelledAt ?: order.updatedAt, warning = true)
            OrderStatuses.NO_DRIVER_FOUND -> base.take(1) +
                Step("Không tìm thấy tài xế", delivery?.searchEndedAt ?: order.updatedAt, warning = true)
            else -> base
        }
    }
    val current = steps.indexOfFirst { it.time == null }
    DeliveryCard {
        Text("Tiến trình", style = MaterialTheme.typography.titleLarge)
        steps.forEachIndexed { index, step ->
            StepRow(step, done = step.time != null, active = index == current, last = index == steps.lastIndex)
        }
    }
}

@Composable
private fun StepRow(step: Step, done: Boolean, active: Boolean, last: Boolean) {
    val color = when {
        step.warning -> DeliveryDanger
        done -> DeliveryGreen
        else -> DeliveryBorder
    }
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(18.dp)
                    .background(if (done) color else Color.White, CircleShape)
                    .border(2.dp, if (active) DeliveryGreen else color, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (done) Text(if (step.warning) "!" else "✓", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            if (!last) Box(Modifier.size(width = 2.dp, height = 18.dp).background(if (done) color else DeliveryBorder))
        }
        Text(
            step.label, Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = if (done || active) DeliveryText else DeliveryMuted,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
        Text(if (done) shortTime(step.time) else if (active) "Đang chờ" else "", style = MaterialTheme.typography.bodyMedium, color = DeliveryMuted)
    }
}
