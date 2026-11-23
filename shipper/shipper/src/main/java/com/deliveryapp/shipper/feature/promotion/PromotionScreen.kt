package com.deliveryapp.shipper.feature.promotion

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.runtime.Composable
import com.deliveryapp.shipper.ui.components.AppScreen
import com.deliveryapp.shipper.ui.components.EmptyState

/** Tab from the Figma navigation bar. The backend has no driver promotion API yet. */
@Composable
fun PromotionScreen(bottomBar: @Composable () -> Unit) {
    AppScreen("Khuyến mãi", bottomBar = bottomBar) {
        EmptyState("Chưa có khuyến mãi", "Ưu đãi dành cho tài xế sẽ hiển thị ở đây khi hệ thống hỗ trợ.", Icons.Outlined.ConfirmationNumber)
    }
}
