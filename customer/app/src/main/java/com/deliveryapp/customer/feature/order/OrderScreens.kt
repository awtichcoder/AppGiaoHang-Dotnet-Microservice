package com.deliveryapp.customer.feature.order

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.deliveryapp.customer.AppContainer
import com.deliveryapp.customer.core.network.ApiResult
import com.deliveryapp.customer.core.network.isSessionExpired
import com.deliveryapp.customer.data.dto.OrderResponse
import com.deliveryapp.customer.data.repository.OrderGateway
import com.deliveryapp.customer.ui.components.AppSurface
import com.deliveryapp.customer.ui.components.CustomerBottomBar
import com.deliveryapp.customer.ui.components.DeliveryCard
import com.deliveryapp.customer.ui.components.DropoffColor
import com.deliveryapp.customer.ui.components.EmptyState
import com.deliveryapp.customer.ui.components.ErrorState
import com.deliveryapp.customer.ui.components.LoadingState
import com.deliveryapp.customer.ui.components.PickupColor
import com.deliveryapp.customer.ui.components.PrimaryAction
import com.deliveryapp.customer.ui.components.SessionExpiredDialog
import com.deliveryapp.customer.ui.theme.DeliveryGreen
import com.deliveryapp.customer.ui.theme.DeliveryMuted
import com.deliveryapp.customer.ui.theme.DeliveryText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class OrdersViewModel(private val gateway: OrderGateway) : ViewModel() {
    var state by mutableStateOf(OrdersUiState(isLoading = true))
        private set

    suspend fun refresh() {
        state = when (val result = gateway.orders()) {
            is ApiResult.Success -> OrdersUiState(orders = result.data.items)
            is ApiResult.Error -> state.copy(
                isLoading = false,
                errorMessage = if (state.orders.isEmpty()) result.message else null,
                sessionExpired = result.isSessionExpired()
            )
            ApiResult.Loading -> state
        }
    }

    fun consumeSessionExpired() { state = state.copy(sessionExpired = false) }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = OrdersViewModel(container.orderRepository) as T
        }
    }
}

data class OrdersUiState(
    val isLoading: Boolean = false,
    val orders: List<OrderResponse> = emptyList(),
    val errorMessage: String? = null,
    val sessionExpired: Boolean = false
)

@Composable
fun OrdersScreen(
    viewModel: OrdersViewModel,
    onOpen: (String) -> Unit,
    onCreateOrder: () -> Unit,
    onHome: () -> Unit,
    onRewards: () -> Unit,
    onProfile: () -> Unit,
    onSessionExpired: () -> Unit
) {
    val state = viewModel.state
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Refresh whenever the tab is shown and every 10 s while visible: statuses change on their own.
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { viewModel.refresh(); delay(10_000) }
        }
    }
    if (state.sessionExpired) {
        SessionExpiredDialog { viewModel.consumeSessionExpired(); onSessionExpired() }
    }
    AppSurface {
        Scaffold(bottomBar = { CustomerBottomBar("orders", onHome, {}, onRewards, onProfile) }) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Đơn hàng của bạn", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Chạm vào một đơn để xem trạng thái và vị trí tài xế.",
                    style = MaterialTheme.typography.bodyMedium, color = DeliveryMuted
                )
                when {
                    state.isLoading -> LoadingState("Đang tải đơn hàng")
                    state.errorMessage != null -> ErrorState(state.errorMessage, onRetry = { scope.launch { viewModel.refresh() } })
                    state.orders.isEmpty() -> {
                        EmptyState("Chưa có đơn nào", "Tạo đơn đầu tiên để bắt đầu giao hàng.")
                        PrimaryAction("Tạo đơn giao hàng", onCreateOrder)
                    }
                    else -> {
                        PrimaryAction("Tạo đơn giao hàng", onCreateOrder)
                        state.orders.forEach { order -> OrderRow(order) { onOpen(order.orderId) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderRow(order: OrderResponse, onClick: () -> Unit) {
    DeliveryCard(Modifier.clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(orderCode(order.orderId), style = MaterialTheme.typography.titleMedium, color = DeliveryText, fontWeight = FontWeight.Bold)
            StatusChip(order.status)
        }
        listOf("A" to order.pickupAddress, "B" to order.dropoffAddress).forEach { (label, address) ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                PointBadge(label, if (label == "A") PickupColor else DropoffColor)
                Text(address, style = MaterialTheme.typography.bodyMedium, color = DeliveryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(shortDateTime(order.createdAt), style = MaterialTheme.typography.bodyMedium, color = DeliveryMuted)
            Text(vnd(order.totalFee), style = MaterialTheme.typography.titleMedium, color = DeliveryGreen, fontWeight = FontWeight.Bold)
        }
    }
}
