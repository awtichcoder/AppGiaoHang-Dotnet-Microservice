package com.deliveryapp.shipper.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.deliveryapp.shipper.feature.auth.AuthUiState
import com.deliveryapp.shipper.feature.work.WorkUiState
import com.deliveryapp.shipper.ui.components.*
import com.deliveryapp.shipper.ui.theme.*

@Composable
fun ProfileScreen(auth: AuthUiState, work: WorkUiState, largeText: Boolean, settingsError: String?,
    bottomBar: @Composable () -> Unit, onText: (Boolean) -> Unit, onDemo: () -> Unit, onLogout: () -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    val demo = LocalDemoMode.current
    if (confirm) ConfirmDialog("Đăng xuất?",
        if (demo) "Phiên dùng thử và lịch sử trong phiên sẽ được đặt lại." else "Bạn sẽ được chuyển sang trạng thái nghỉ và lịch sử trong phiên sẽ được đặt lại.", "Đăng xuất",
        { confirm = false }, { confirm = false; onLogout() })
    AppScreen("Tài khoản", bottomBar = bottomBar) {
        DeliveryCard {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(76.dp).background(DeliveryGreenSoft, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Person, null, Modifier.size(42.dp), DeliveryGreenDark)
                }
                Text(auth.user?.fullName ?: "Tài xế", style = MaterialTheme.typography.titleLarge)
                StatusBadge("Tài xế · Tài khoản dùng thử", icon = Icons.Outlined.Verified)
            }
            HorizontalDivider(color = DeliveryBorder)
            InfoRow("Email", auth.user?.email ?: "—")
            InfoRow("Điện thoại", auth.user?.phone ?: "Chưa cập nhật")
            InfoRow("Trạng thái", work.availability.label)
        }
        SectionTitle("Cài đặt")
        settingsError?.let { ErrorState(it) }
        DeliveryCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Chữ lớn hơn", style = MaterialTheme.typography.titleMedium)
                    Text("Lưu trên thiết bị", style = MaterialTheme.typography.bodySmall, color = DeliveryMuted)
                }
                Switch(largeText, onText, Modifier.testTag("large_text"))
            }
            if (demo) {
                HorizontalDivider(color = DeliveryBorder)
                SecondaryAction("Thử tình huống mô phỏng", onDemo, icon = Icons.Outlined.Science)
            }
        }
        DeliveryCard(color = DeliveryGreenSoft) {
            DemoBadge()
            Text("DeliveryApp dành cho tài xế", style = MaterialTheme.typography.titleMedium)
            Text(
                if (demo) "Chuyến đi và lịch sử là dữ liệu dùng thử. Phiên được giữ khi xoay màn hình; khi ứng dụng bị hệ thống đóng, hãy đăng nhập lại để bắt đầu phiên mới."
                else "Lời mời và chuyến đi lấy từ máy chủ DeliveryApp. Lịch sử hiển thị các chuyến hoàn thành trong phiên đăng nhập này; khi ứng dụng bị hệ thống đóng, hãy đăng nhập lại.",
                style = MaterialTheme.typography.bodySmall, color = DeliveryMuted)
        }
        auth.error?.let { ErrorState(it) }
        if (work.active != null) Text("Hoàn thành chuyến đang giao trước khi đăng xuất.", style = MaterialTheme.typography.bodySmall, color = DeliveryMuted)
        SecondaryAction(if (auth.loading) "Đang đăng xuất…" else "Đăng xuất", { confirm = true },
            Modifier.testTag("logout"), enabled = !auth.loading && !work.loading && work.active == null, icon = Icons.AutoMirrored.Outlined.Logout)
        Text("Phiên bản 1.0.0 · Shipper Android", style = MaterialTheme.typography.bodySmall, color = DeliveryMuted)
    }
}
