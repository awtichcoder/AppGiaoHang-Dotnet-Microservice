package com.deliveryapp.shipper.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deliveryapp.shipper.data.model.TripStatus
import com.deliveryapp.shipper.ui.theme.*
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun money(value: Long): String = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN")).format(value) + " ₫"
fun dateLabel(value: Instant?): String = value?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("HH:mm · dd/MM/yyyy")) ?: "Chưa có thời gian"

@Composable
fun BrandMark(modifier: Modifier = Modifier, inverse: Boolean = false) {
    Box(modifier.size(44.dp).background(if (inverse) Color.White else DeliveryGreen, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
        Text("D", color = if (inverse) DeliveryGreen else Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
    }
}
@Composable
fun AppScreen(
    title: String, subtitle: String? = null, onBack: (() -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {}, actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    AppFrame(title, subtitle, onBack, bottomBar, actions) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
        }
    }
}

@Composable
fun AppLazyScreen(
    title: String, subtitle: String? = null, bottomBar: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}, content: LazyListScope.() -> Unit,
) {
    AppFrame(title, subtitle, null, bottomBar, actions) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 640.dp).fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
        }
    }
}

@Composable
private fun AppFrame(
    title: String, subtitle: String?, onBack: (() -> Unit)?, bottomBar: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit, content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        containerColor = DeliveryCanvas, contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) IconButton(onClick = onBack, modifier = Modifier.testTag("back")) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Quay lại") }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = DeliveryMuted) }
                }
                actions()
            }
        },
        bottomBar = bottomBar,
        content = content,
    )
}
@Composable
fun DeliveryCard(modifier: Modifier = Modifier, color: Color = DeliverySurface, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = color, border = BorderStroke(1.dp, DeliveryBorder)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
@Composable
fun PrimaryAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false, icon: ImageVector? = null) {
    Button(onClick, modifier.fillMaxWidth().heightIn(min = 54.dp), enabled = enabled && !loading,
        shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)) {
        if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        else {
            icon?.let { Icon(it, null, Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)) }
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}
@Composable
fun SecondaryAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    OutlinedButton(onClick, modifier.fillMaxWidth().heightIn(min = 50.dp), enabled = enabled,
        shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, DeliveryBorder),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        icon?.let { Icon(it, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(label, color = if (enabled) DeliveryText else DeliveryMuted)
    }
}
@Composable
fun StatusBadge(label: String, color: Color = DeliveryGreenDark, background: Color = DeliveryGreenSoft, icon: ImageVector = Icons.Outlined.Circle) {
    Surface(color = background, shape = RoundedCornerShape(9.dp)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(14.dp), tint = color)
            Spacer(Modifier.width(6.dp))
            Text(label, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}
@Composable
fun TripBadge(status: TripStatus) {
    val danger = status == TripStatus.CANCELLED
    StatusBadge(status.label, color = if (danger) DeliveryDanger else DeliveryGreenDark,
        background = if (danger) Color(0xFFFFEEEC) else DeliveryGreenSoft,
        icon = if (status == TripStatus.COMPLETED) Icons.Outlined.CheckCircle else Icons.Outlined.LocalShipping)
}
/** True when the app runs on MockShipperRepository; demo-only labels and actions are hidden otherwise. */
val LocalDemoMode = staticCompositionLocalOf { true }

@Composable
fun DemoBadge() {
    if (LocalDemoMode.current) StatusBadge("Dữ liệu mô phỏng", DeliveryMuted, Color(0xFFEDF1EF), Icons.Outlined.Science)
}

@Composable
fun SectionTitle(title: String, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        action?.invoke()
    }
}
@Composable
fun RouteStops(pickup: String, dropoff: String) {
    Stop("LẤY HÀNG", pickup, Icons.Outlined.Storefront, DeliveryGreen)
    Box(Modifier.padding(start = 16.dp).width(1.dp).height(18.dp).background(DeliveryBorder))
    Stop("GIAO HÀNG", dropoff, Icons.Outlined.LocationOn, Color(0xFF986100))
}
@Composable
private fun Stop(label: String, address: String, icon: ImageVector, color: Color) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(34.dp).background(color.copy(alpha = .09f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(19.dp), color)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = DeliveryMuted)
            Text(address, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}
@Composable
fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = DeliveryMuted)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
@Composable
fun EmptyState(title: String, message: String, icon: ImageVector = Icons.Outlined.Inbox, action: (@Composable () -> Unit)? = null) {
    DeliveryCard {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(72.dp).background(DeliveryGreenSoft, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(34.dp), DeliveryGreen)
            }
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = DeliveryMuted)
            action?.invoke()
        }
    }
}
@Composable
fun LoadingState(message: String = "Đang tải dữ liệu…") {
    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
        Text(message, color = DeliveryMuted, style = MaterialTheme.typography.bodyMedium)
    }
}
@Composable
fun ErrorState(message: String, onRetry: (() -> Unit)? = null) {
    DeliveryCard(color = Color(0xFFFFEEEC)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.ErrorOutline, null, tint = DeliveryDanger)
            Spacer(Modifier.width(8.dp))
            Text("Chưa thể thực hiện", fontWeight = FontWeight.SemiBold, color = DeliveryDanger)
        }
        Text(message, style = MaterialTheme.typography.bodyMedium)
        onRetry?.let { TextButton(onClick = it) { Text("Thử lại") } }
    }
}
@Composable
fun ConfirmDialog(title: String, message: String, confirm: String = "Xác nhận", onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(message) },
        confirmButton = { Button(onClick = onConfirm, modifier = Modifier.testTag("confirm")) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Quay lại") } })
}
