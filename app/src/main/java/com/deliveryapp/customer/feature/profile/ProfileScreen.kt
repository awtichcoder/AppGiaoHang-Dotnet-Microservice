package com.deliveryapp.customer.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.deliveryapp.customer.ui.components.AppSurface
import com.deliveryapp.customer.ui.components.CustomerBottomBar
import com.deliveryapp.customer.ui.components.DeliveryCard
import com.deliveryapp.customer.ui.components.ErrorState
import com.deliveryapp.customer.ui.components.InfoRow
import com.deliveryapp.customer.ui.components.LoadingState
import com.deliveryapp.customer.ui.components.PrimaryAction
import com.deliveryapp.customer.ui.components.SecondaryAction
import com.deliveryapp.customer.ui.components.SessionExpiredDialog
import com.deliveryapp.customer.ui.components.StatusBanner
import com.deliveryapp.customer.ui.theme.DeliveryMuted

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onLogout: () -> Unit,
    onHome: () -> Unit,
    onOrders: () -> Unit,
    onRewards: () -> Unit,
    onSessionExpired: () -> Unit
) {
    val state = viewModel.state

    LaunchedEffect(state.logoutCompleted) {
        if (state.logoutCompleted) {
            viewModel.consumeLogoutCompleted()
            onLogout()
        }
    }

    if (state.sessionExpired) {
        SessionExpiredDialog {
            viewModel.consumeSessionExpired()
            onSessionExpired()
        }
    }

    AppSurface {
        Scaffold(
            bottomBar = { CustomerBottomBar("profile", onHome, onOrders, onRewards, {}) }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(text = "Tài khoản", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "Quản lý hồ sơ khách hàng và phiên đăng nhập.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DeliveryMuted
                )

                when {
                    state.isLoading -> LoadingState("Đang tải tài khoản")
                    state.errorMessage != null && state.user == null -> ErrorState(state.errorMessage, viewModel::load)
                    else -> AccountContent(state, viewModel::logout)
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun AccountContent(state: ProfileUiState, onLogoutClick: () -> Unit) {
    val user = state.user
    StatusBanner(
        title = "Hạng ${state.tier}",
        subtitle = "Bạn đang có ${state.points} điểm thưởng khả dụng."
    )

    DeliveryCard {
        Text(text = "Thông tin cá nhân", style = MaterialTheme.typography.titleLarge)
        InfoRow("Họ tên", user?.fullName ?: "-")
        InfoRow("Email", user?.email ?: "-")
        InfoRow("Điện thoại", user?.phone ?: "-")
        InfoRow("Vai trò", user?.role ?: "Customer")
    }

    DeliveryCard {
        Text(text = "Phiên đăng nhập", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "Access token được gửi tự động bằng Authorization Bearer. Khi token hết hạn, ứng dụng sẽ thử refresh trước khi yêu cầu đăng nhập lại.",
            style = MaterialTheme.typography.bodyMedium,
            color = DeliveryMuted
        )
        PrimaryAction(
            label = if (state.isLoggingOut) "Đang đăng xuất" else "Đăng xuất",
            onClick = onLogoutClick,
            loading = state.isLoggingOut
        )
        SecondaryAction(
            label = "Đăng xuất khỏi thiết bị này",
            onClick = onLogoutClick,
            enabled = !state.isLoggingOut,
            danger = true
        )
    }

    state.errorMessage?.let {
        StatusBanner(
            title = "Một phần dữ liệu chưa tải được",
            subtitle = it,
            warning = true
        )
    }
}
