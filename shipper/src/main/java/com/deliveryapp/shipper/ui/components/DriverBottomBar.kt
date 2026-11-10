package com.deliveryapp.shipper.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deliveryapp.shipper.R
import com.deliveryapp.shipper.ui.theme.FigmaNavInactive
import com.deliveryapp.shipper.ui.theme.FigmaPrimary

data class DriverTab(val route: String, val label: String, @DrawableRes val icon: Int)

/** Figma "BottomNavBar": Trang chủ · Đơn hàng · Khuyến mãi · Tài khoản. */
val DriverTabs = listOf(
    DriverTab("home", "Trang chủ", R.drawable.ic_nav_home),
    DriverTab("history", "Đơn hàng", R.drawable.ic_nav_orders),
    DriverTab("promotions", "Khuyến mãi", R.drawable.ic_nav_promo),
    DriverTab("profile", "Tài khoản", R.drawable.ic_nav_account),
)

@Composable
fun DriverBottomBar(currentRoute: String?, onSelect: (String) -> Unit) {
    val shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Color.White).drawBehind {
            // Figma uses border-top only: the top edge plus its two rounded corners.
            val r = 32.dp.toPx()
            val stroke = 1.dp.toPx()
            val inset = stroke / 2
            val path = Path().apply {
                moveTo(inset, r)
                arcTo(Rect(inset, inset, 2 * r, 2 * r), 180f, 90f, false)
                lineTo(size.width - r, inset)
                arcTo(Rect(size.width - 2 * r, inset, size.width - inset, 2 * r), 270f, 90f, false)
            }
            drawPath(path, FigmaPrimary, style = Stroke(stroke))
        }
            .navigationBarsPadding().padding(start = 19.dp, end = 19.dp, top = 9.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DriverTabs.forEach { tab ->
            val selected = currentRoute == tab.route
            val color = if (selected) FigmaPrimary else FigmaNavInactive
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Tab, onClick = { onSelect(tab.route) })
                    .alpha(if (selected) .8f else 1f).padding(8.dp).testTag("tab_${tab.route}"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.height(24.dp), contentAlignment = Alignment.Center) {
                    Icon(painterResource(tab.icon), null, tint = color)
                }
                Text(tab.label, color = color, fontSize = 12.sp, lineHeight = 12.sp, letterSpacing = 0.6.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}
