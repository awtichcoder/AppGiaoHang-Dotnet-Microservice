package com.deliveryapp.customer.feature.order

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deliveryapp.customer.data.dto.OrderPricePreview
import com.deliveryapp.customer.data.dto.OrderResponse
import com.deliveryapp.customer.ui.components.InfoRow
import com.deliveryapp.customer.ui.components.PickupColor
import com.deliveryapp.customer.ui.components.DropoffColor
import com.deliveryapp.customer.ui.theme.DeliveryBorder
import com.deliveryapp.customer.ui.theme.DeliveryDanger
import com.deliveryapp.customer.ui.theme.DeliveryGreen
import com.deliveryapp.customer.ui.theme.DeliveryGreenSoft
import com.deliveryapp.customer.ui.theme.DeliveryMuted
import com.deliveryapp.customer.ui.theme.DeliveryText
import com.deliveryapp.customer.ui.theme.DeliveryWarning

@Composable
fun PointBadge(label: String, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(28.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

/** "A  Điểm lấy hàng / address" row used by the order list and tracking. */
@Composable
fun RouteStop(label: String, title: String, address: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        PointBadge(label, color)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = DeliveryMuted)
            Text(address, style = MaterialTheme.typography.titleMedium, color = DeliveryText)
        }
    }
}

@Composable
fun RouteSummary(pickupAddress: String, dropoffAddress: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RouteStop("A", "Điểm lấy hàng", pickupAddress, PickupColor)
        RouteStop("B", "Điểm giao hàng", dropoffAddress, DropoffColor)
    }
}

@Composable
fun StatusChip(status: String) {
    val (background, content) = when {
        OrderStatuses.isWarning(status) -> DeliveryWarning to Color(0xFF9A6200)
        status == OrderStatuses.COMPLETED -> DeliveryGreen to Color.White
        else -> DeliveryGreenSoft to DeliveryGreen
    }
    Text(
        OrderStatuses.label(status),
        Modifier.background(background, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
        color = content, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold
    )
}

@Composable
fun MoneyRow(label: String, amount: Long, discount: Boolean = false) {
    if (discount && amount == 0L) return
    InfoRow(label, if (discount) "−${vnd(amount)}" else vnd(amount))
}

@Composable
fun TotalRow(label: String, amount: Long) {
    HorizontalDivider(color = DeliveryBorder)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = DeliveryText)
        Text(vnd(amount), style = MaterialTheme.typography.titleLarge, color = DeliveryGreen, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PriceBreakdown(preview: OrderPricePreview, pointsDiscount: Long, total: Long) {
    InfoRow("Khoảng cách", km(preview.distanceKm))
    MoneyRow("Phí cơ bản", preview.baseFee)
    MoneyRow("Phụ phí", preview.surcharge)
    MoneyRow("Phí nền tảng", preview.platformFee)
    MoneyRow("Giảm giá ưu đãi", preview.discountPromotion, discount = true)
    MoneyRow("Giảm bằng điểm", pointsDiscount, discount = true)
    TotalRow("Tổng thanh toán", total)
}

@Composable
fun OrderFees(order: OrderResponse) {
    InfoRow("Khoảng cách", km(order.distanceKm))
    MoneyRow("Phí cơ bản", order.baseFee)
    MoneyRow("Phụ phí", order.surcharge)
    MoneyRow("Phí nền tảng", order.platformFee)
    MoneyRow("Giảm giá ưu đãi", order.discountPromotion, discount = true)
    MoneyRow("Giảm bằng điểm", order.discountPoints, discount = true)
    TotalRow("Tổng thanh toán", order.totalFee)
}

@Composable
fun FieldError(message: String?) {
    if (message != null) Text(message, color = DeliveryDanger, style = MaterialTheme.typography.bodyMedium)
}
