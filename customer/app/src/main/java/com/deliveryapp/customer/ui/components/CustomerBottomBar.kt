package com.deliveryapp.customer.ui.components

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun CustomerBottomBar(
    selected: String,
    onHome: () -> Unit,
    onOrders: () -> Unit,
    onRewards: () -> Unit,
    onProfile: () -> Unit
) {
    val items = listOf(
        NavItem("home", "Nhà", "Trang chủ", onHome),
        NavItem("orders", "Đơn", "Đơn hàng", onOrders),
        NavItem("rewards", "Điểm", "Điểm", onRewards),
        NavItem("profile", "Tôi", "Tài khoản", onProfile)
    )
    NavigationBar {
        items.forEach { item ->
            NavigationBarItem(
                selected = selected == item.route,
                onClick = item.action,
                icon = { Text(item.iconText) },
                label = { Text(item.label) }
            )
        }
    }
}

private data class NavItem(
    val route: String,
    val iconText: String,
    val label: String,
    val action: () -> Unit
)
