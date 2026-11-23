package com.deliveryapp.customer.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deliveryapp.customer.ui.components.AppSurface
import com.deliveryapp.customer.ui.components.CustomerBottomBar
import com.deliveryapp.customer.ui.components.DeliveryCard
import com.deliveryapp.customer.ui.components.EmptyState
import com.deliveryapp.customer.ui.components.ErrorState
import com.deliveryapp.customer.ui.components.LoadingState
import com.deliveryapp.customer.ui.components.PrimaryAction
import com.deliveryapp.customer.ui.components.SecondaryAction
import com.deliveryapp.customer.ui.components.SessionExpiredDialog
import com.deliveryapp.customer.ui.components.StatusBanner
import com.deliveryapp.customer.ui.theme.DeliveryGreen
import com.deliveryapp.customer.ui.theme.DeliveryGreenSoft
import com.deliveryapp.customer.ui.theme.DeliveryMuted
import com.deliveryapp.customer.ui.theme.DeliveryText

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onCreateOrder: () -> Unit,
    onOrders: () -> Unit,
    onRewards: () -> Unit,
    onProfile: () -> Unit,
    onSessionExpired: () -> Unit
) {
    val state = viewModel.state
    if (state.sessionExpired) {
        SessionExpiredDialog {
            viewModel.consumeSessionExpired()
            onSessionExpired()
        }
    }

    AppSurface {
        Scaffold(
            bottomBar = { CustomerBottomBar("home", {}, onOrders, onRewards, onProfile) }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HomeHeader(fullName = state.fullName)
                when {
                    state.isLoading -> LoadingState("Đang tải hồ sơ")
                    state.errorMessage != null -> ErrorState(state.errorMessage, viewModel::load)
                    else -> HomeContent(onCreateOrder, onOrders, onRewards)
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(fullName: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(DeliveryGreen, RoundedCornerShape(8.dp))
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "DeliveryApp", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text(text = "Chào, $fullName", color = Color.White, style = MaterialTheme.typography.headlineMedium)
            Text(
                text = "Tạo đơn giao hàng nhanh và theo dõi trạng thái ngay trong ứng dụng.",
                color = Color.White.copy(alpha = 0.86f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun HomeContent(
    onCreateOrder: () -> Unit,
    onOrders: () -> Unit,
    onRewards: () -> Unit
) {
    DeliveryCard {
        Text(text = "Điểm lấy hàng", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "Chọn tạo đơn để nhập điểm lấy, điểm giao và ghi chú hàng hóa.",
            style = MaterialTheme.typography.bodyMedium,
            color = DeliveryMuted
        )
        PrimaryAction("Tạo đơn giao hàng", onCreateOrder)
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        QuickActionCard(
            title = "Đơn hàng",
            subtitle = "Theo dõi lịch sử",
            modifier = Modifier.weight(1f),
            onClick = onOrders
        )
        QuickActionCard(
            title = "Điểm",
            subtitle = "Xem ưu đãi",
            modifier = Modifier.weight(1f),
            onClick = onRewards
        )
    }

    StatusBanner(
        title = "Ưu đãi thành viên",
        subtitle = "Điểm thưởng sẽ được cập nhật sau mỗi đơn giao thành công."
    )

    EmptyState(
        title = "Đơn gần đây",
        message = "Xem trạng thái và vị trí tài xế của các đơn đã đặt trong mục Đơn hàng."
    )
}

@Composable
private fun QuickActionCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    DeliveryCard(modifier = modifier) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = DeliveryText)
        Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = DeliveryMuted)
        SecondaryAction(label = "Mở", onClick = onClick)
    }
}
