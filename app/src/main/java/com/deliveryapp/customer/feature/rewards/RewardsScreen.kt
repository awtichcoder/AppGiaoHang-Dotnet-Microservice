package com.deliveryapp.customer.feature.rewards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deliveryapp.customer.data.dto.LoyaltyAccountResponse
import com.deliveryapp.customer.data.dto.LoyaltyTransactionResponse
import com.deliveryapp.customer.ui.components.AppSurface
import com.deliveryapp.customer.ui.components.DeliveryCard
import com.deliveryapp.customer.ui.components.EmptyState
import com.deliveryapp.customer.ui.components.ErrorState
import com.deliveryapp.customer.ui.components.InfoRow
import com.deliveryapp.customer.ui.components.LoadingState
import com.deliveryapp.customer.ui.components.PrimaryAction
import com.deliveryapp.customer.ui.components.SecondaryAction
import com.deliveryapp.customer.ui.components.SessionExpiredDialog
import com.deliveryapp.customer.ui.components.StatusBanner
import com.deliveryapp.customer.ui.theme.DeliveryGreen
import com.deliveryapp.customer.ui.theme.DeliveryMuted

@Composable
fun RewardsScreen(
    viewModel: RewardsViewModel,
    onBack: () -> Unit,
    onUsePoints: () -> Unit,
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
        Scaffold { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(text = "Điểm thưởng", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "Theo dõi hạng thành viên và lịch sử cộng/trừ điểm.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DeliveryMuted
                )

                when {
                    state.isLoading -> LoadingState("Đang tải điểm thưởng")
                    state.errorMessage != null -> ErrorState(state.errorMessage, viewModel::load)
                    state.loyalty != null -> RewardsContent(state.loyalty, onUsePoints, onBack)
                    else -> EmptyState("Chưa có dữ liệu", "Không tìm thấy tài khoản điểm thưởng cho khách hàng này.")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun RewardsContent(
    loyalty: LoyaltyAccountResponse,
    onUsePoints: () -> Unit,
    onBack: () -> Unit
) {
    StatusBanner(
        title = "${loyalty.points} điểm",
        subtitle = "Hạng ${loyalty.tier}. Điểm sẽ được đồng bộ từ backend loyalty."
    )

    DeliveryCard {
        Text(text = "Tổng quan", style = MaterialTheme.typography.titleLarge)
        InfoRow("Điểm hiện có", loyalty.points.toString())
        InfoRow("Hạng", loyalty.tier)
    }

    DeliveryCard {
        Text(text = "Cách dùng điểm", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "Điểm có thể được áp dụng khi tạo đơn ở bước thanh toán. Quy đổi và giới hạn dùng điểm lấy theo chính sách backend.",
            style = MaterialTheme.typography.bodyMedium,
            color = DeliveryMuted
        )
        PrimaryAction("Tạo đơn và dùng điểm", onUsePoints)
        SecondaryAction("Về trang chủ", onBack)
    }

    if (loyalty.recentTransactions.isEmpty()) {
        EmptyState(
            title = "Chưa có giao dịch điểm",
            message = "Sau khi đơn hoàn tất, lịch sử cộng/trừ điểm sẽ xuất hiện tại đây."
        )
    } else {
        DeliveryCard {
            Text(text = "Giao dịch gần đây", style = MaterialTheme.typography.titleLarge)
            loyalty.recentTransactions.take(5).forEach { transaction ->
                TransactionRow(transaction)
            }
        }
    }
}

@Composable
private fun TransactionRow(transaction: LoyaltyTransactionResponse) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = transaction.type, style = MaterialTheme.typography.titleMedium)
            Text(
                text = transaction.orderId?.let { "Đơn $it" } ?: transaction.createdAt,
                style = MaterialTheme.typography.bodyMedium,
                color = DeliveryMuted
            )
        }
        Text(
            text = if (transaction.pointsDelta >= 0) "+${transaction.pointsDelta}" else transaction.pointsDelta.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = DeliveryGreen,
            fontWeight = FontWeight.Bold
        )
    }
}
