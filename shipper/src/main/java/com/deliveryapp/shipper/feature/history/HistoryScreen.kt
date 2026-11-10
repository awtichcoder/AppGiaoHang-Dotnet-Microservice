package com.deliveryapp.shipper.feature.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deliveryapp.shipper.data.model.*
import com.deliveryapp.shipper.feature.work.WorkUiState
import com.deliveryapp.shipper.ui.components.*
import com.deliveryapp.shipper.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(state: WorkUiState, bottomBar: @Composable () -> Unit, onRefresh: () -> Unit, onDetail: (String) -> Unit) {
    var selected by rememberSaveable { mutableStateOf("ALL") }
    val items = state.history.filter { selected == "ALL" || it.status.name == selected }
    AppLazyScreen("Lịch sử giao hàng", "Nhìn lại những chuyến đi của bạn", bottomBar = bottomBar, actions = {
        IconButton(onClick = onRefresh, enabled = !state.historyLoading) { Icon(Icons.Outlined.Refresh, "Làm mới lịch sử") }
    }) {
        item(key = "demo") { DemoBadge() }
        item(key = "filters") { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("ALL" to "Tất cả", "COMPLETED" to "Hoàn thành", "CANCELLED" to "Đã hủy").forEach { (key, label) ->
                FilterChip(selected == key, { selected = key }, label = { Text(label) })
            }
        } }
        when {
            state.historyLoading -> item { LoadingState("Đang tải lịch sử…") }
            state.historyError != null -> item { ErrorState(state.historyError, onRefresh) }
            items.isEmpty() -> item { EmptyState("Chưa có chuyến nào", "Các chuyến phù hợp sẽ xuất hiện ở đây sau khi được thực hiện.", Icons.AutoMirrored.Outlined.ReceiptLong) }
            else -> {
                item(key = "count") { Text("${items.size} chuyến", style = MaterialTheme.typography.bodySmall, color = DeliveryMuted) }
                items(items, key = { "trip_${it.deliveryId}" }) { trip ->
                    DeliveryCard {
                        Text(trip.shortId, fontWeight = FontWeight.Bold)
                        TripBadge(trip.status)
                        Text(dateLabel(trip.displayTime), style = MaterialTheme.typography.bodySmall, color = DeliveryMuted)
                        HorizontalDivider(color = DeliveryBorder)
                        RouteStops(trip.pickupAddress, trip.dropoffAddress)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(trip.totalFee?.let { money(it) + " phí giao" } ?: "Đã hủy", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { onDetail(trip.deliveryId) }, modifier = Modifier.testTag("history_${trip.deliveryId}")) { Text("Chi tiết") }
                        }
                    }
                }
            }
        }
    }
}
