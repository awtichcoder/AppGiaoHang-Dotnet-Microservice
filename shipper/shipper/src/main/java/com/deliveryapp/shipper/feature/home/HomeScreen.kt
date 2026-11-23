package com.deliveryapp.shipper.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.deliveryapp.shipper.R
import com.deliveryapp.shipper.data.model.*
import com.deliveryapp.shipper.feature.delivery.DeliveryMap
import com.deliveryapp.shipper.feature.delivery.LocationUiState
import com.deliveryapp.shipper.feature.work.WorkUiState
import com.deliveryapp.shipper.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

/**
 * Driver home, built from Figma "Project giao hàng" (iPhone 17 - 3, 8, 9, 10):
 * green header with availability toggle, map, then "Đơn hàng của bạn" with offer cards
 * (Từ chối / Nhận đơn) or the active trip card that expands into pickup/drop-off details.
 */
@Composable
fun HomeScreen(
    driver: Driver?, state: WorkUiState, location: LocationUiState, bottomBar: @Composable () -> Unit,
    onToggle: () -> Unit, onAccept: () -> Unit, onReject: () -> Unit, onAdvance: (TripStatus) -> Unit,
) {
    var confirmComplete by rememberSaveable { mutableStateOf(false) }
    // Figma text uses "leading: normal"; don't inherit the theme's 24sp body line height.
    ProvideTextStyle(TextStyle(lineHeight = 1.2.em)) { Box(Modifier.fillMaxSize()) {
        Scaffold(containerColor = Color.White, contentWindowInsets = WindowInsets(0, 0, 0, 0), bottomBar = bottomBar) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                DriverHeader(driver, state, onToggle)
                Spacer(Modifier.height(24.dp))
                DeliveryMap(state.active, location,
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(459.dp).clip(RoundedCornerShape(12.dp)))
                Spacer(Modifier.height(19.dp))
                OrdersSection(state, onAccept, onReject) { status ->
                    // Figma shows a confirmation only before the final step.
                    if (status == TripStatus.DELIVERING) confirmComplete = true else onAdvance(status)
                }
            }
        }
        // Status bar stays readable while the content scrolls underneath it.
        Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(Color.White))
        if (confirmComplete) ConfirmDeliveredDialog(
            onBack = { confirmComplete = false },
            onConfirm = { confirmComplete = false; onAdvance(TripStatus.DELIVERING) },
        )
    } }
}

@Composable
private fun DriverHeader(driver: Driver?, state: WorkUiState, onToggle: () -> Unit) {
    val online = state.availability != Availability.OFFLINE
    val enabled = !state.loading && state.active == null
    Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth()
        .background(FigmaPrimary, RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
        .statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(91.dp).padding(start = 23.dp, end = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(Color.White, CircleShape))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(driver?.fullName ?: "Tài xế", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                // Figma: "Tài xế • ★ 4.9" — the backend has no driver rating yet.
                Text("Tài xế", color = Color.White, fontSize = 10.sp)
            }
            Box(Modifier.size(width = 43.dp, height = 53.dp)
                .clickable(enabled = enabled, role = Role.Switch, onClick = onToggle)
                .semantics { contentDescription = if (online) "Đang nhận đơn. Chạm để nghỉ" else "Đang nghỉ. Chạm để nhận đơn" }
                .testTag("availability")) {
                Text("bật / tắt", Modifier.align(Alignment.TopCenter), color = Color.White, fontSize = 10.sp)
                Image(painterResource(if (online) R.drawable.ic_toggle_on else R.drawable.ic_toggle_off), null,
                    Modifier.align(Alignment.BottomCenter).size(43.dp), alpha = if (enabled) 1f else .6f)
            }
        }
    }
}

@Composable
private fun OrdersSection(state: WorkUiState, onAccept: () -> Unit, onReject: () -> Unit, onAdvance: (TripStatus) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Column {
            Text("Đơn hàng của bạn", Modifier.padding(start = 5.dp), color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            (state.error ?: state.locationWarning)?.let {
                // Leave room for the collapse handle that overhangs the trip card's top edge by ~16 dp.
                Text(it, Modifier.padding(start = 5.dp, bottom = if (state.active != null) 22.dp else 8.dp), color = FigmaRed, fontSize = 11.sp)
            }
            val trip = state.active
            val offer = state.offer
            when {
                trip != null -> Box {
                    ActiveTripCard(trip, expanded, state.loading, onAdvance)
                    // Collapse handle centred on the card's top edge (Ellipse 7 + Vector 1); anchored to the
                    // card so an error line above it cannot slide underneath.
                    Image(
                        painterResource(R.drawable.ic_order_chevron), if (expanded) "Thu gọn chi tiết" else "Xem chi tiết",
                        Modifier.align(Alignment.TopCenter).offset(y = (-15.5).dp).size(31.dp).rotate(if (expanded) 0f else 180f)
                            .clip(CircleShape).clickable(role = Role.Button) { expanded = !expanded }.testTag("toggle_details"),
                    )
                }
                offer != null && offer.status == OfferStatus.OFFERED ->
                    OfferCard(offer, state.seconds, state.canRespond, onAccept, onReject)
                else -> InfoCard(
                    when {
                        state.loading -> "Đang cập nhật…"
                        state.availability == Availability.OFFLINE -> "Bật nhận đơn để bắt đầu nhận lời mời."
                        offer?.status == OfferStatus.EXPIRED -> "Lời mời đã hết hạn. Đang chờ lời mời tiếp theo…"
                        offer?.status == OfferStatus.REJECTED -> "Đã từ chối lời mời. Đang chờ lời mời tiếp theo…"
                        offer?.status == OfferStatus.CANCELLED || offer?.status == OfferStatus.PROCESSED ->
                            "Lời mời không còn hiệu lực. Đang chờ lời mời tiếp theo…"
                        else -> "Đang chờ lời mời gần bạn…"
                    }
                )
            }
        }
    }
}

private val CardShape = RoundedCornerShape(14.dp)

@Composable
private fun OrderCardFrame(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color.White, CardShape).border(1.dp, FigmaCardBorder, CardShape).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
private fun OrderSummary(orderId: String, distanceKm: Double?, pickup: String, dropoff: String, income: Long?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(orderCode(orderId), color = FigmaInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        distanceKm?.let { Text(km(it), color = FigmaAction, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) }
    }
    Text("A  $pickup", color = FigmaInk, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    Text("B  $dropoff", color = FigmaMuted, fontSize = 10.sp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Thu nhập dự kiến", color = FigmaMuted, fontSize = 9.sp)
        Text(income?.let(::vnd) ?: "—", color = FigmaInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun OfferCard(offer: Offer, seconds: Int, canRespond: Boolean, onAccept: () -> Unit, onReject: () -> Unit) {
    OrderCardFrame {
        OrderSummary(offer.orderId, offer.distanceToPickupKm, offer.pickupAddress, offer.dropoffAddress, offer.incomeEstimate)
        // Figma placeholder "Thông báo": used for the offer's time limit.
        Text("Lời mời hết hạn sau ${seconds}s", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Light)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.width(88.dp).height(36.dp).clip(RoundedCornerShape(10.dp)).background(FigmaRejectBg)
                .clickable(enabled = canRespond, role = Role.Button, onClick = onReject).testTag("reject_offer"),
                contentAlignment = Alignment.Center) {
                Text("Từ chối", color = FigmaRed, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
            Box(Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(10.dp)).background(FigmaAction.copy(alpha = if (canRespond) 1f else .5f))
                .clickable(enabled = canRespond, role = Role.Button, onClick = onAccept).testTag("accept_offer"),
                contentAlignment = Alignment.Center) {
                Text("Nhận đơn", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Figma button labels for the three driver steps. */
private fun stepLabel(status: TripStatus) = when (status) {
    TripStatus.ASSIGNED -> "Đến Nhận hàng"
    TripStatus.PICKED_UP -> "Bắt đầu giao hàng"
    TripStatus.DELIVERING -> "Xác Nhận Đã Giao Hàng"
    else -> null
}

@Composable
private fun ActiveTripCard(trip: Trip, expanded: Boolean, loading: Boolean, onAdvance: (TripStatus) -> Unit) {
    OrderCardFrame {
        OrderSummary(trip.orderId, trip.distanceToPickupKm, trip.pickupAddress, trip.dropoffAddress, trip.incomeEstimate)
        stepLabel(trip.status)?.let { label ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.width(193.dp).clip(RoundedCornerShape(10.dp)).background(FigmaAction.copy(alpha = if (loading) .5f else 1f))
                    .clickable(enabled = !loading, role = Role.Button) { onAdvance(trip.status) }
                    .padding(vertical = 11.dp).testTag("advance_trip"), contentAlignment = Alignment.Center) {
                    if (loading) CircularProgressIndicator(Modifier.size(12.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        AnimatedVisibility(expanded) { TripDetails(trip) }
    }
}

@Composable
private fun TripDetails(trip: Trip) {
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(orderCode(trip.orderId), color = FigmaInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        HorizontalDivider(thickness = 1.dp, color = Color.Black)
        Text("Điểm lấy hàng", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        // Sender name/phone: not provided by the backend yet.
        DetailRow("Tên người gửi", "—")
        DetailRow("số điện thoại", "—")
        DetailRow("Khoảng cách", trip.distanceToPickupKm?.let(::km) ?: "—", FigmaAction, 10.sp, FontWeight.SemiBold)
        DetailRow("địa chỉ", trip.pickupAddress, FigmaInk, weight = FontWeight.Light)
        HorizontalDivider(thickness = 1.dp, color = Color.Black)
        Text("Điểm giao hàng", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        // Receiver name, pickup→drop-off distance and cash-on-delivery: not provided by the backend yet.
        DetailRow("Tên người nhận", "—")
        DetailRow("số điện thoại", trip.receiverPhone ?: "—", weight = FontWeight.Normal)
        DetailRow("Khoảng cách", "—", FigmaAction, 10.sp, FontWeight.SemiBold)
        DetailRow("địa chỉ", trip.dropoffAddress, FigmaMuted, weight = FontWeight.Light)
        DetailRow("Thu người nhận", "—", FigmaRed, 15.sp, FontWeight.Bold)
    }
}

@Composable
private fun DetailRow(label: String, value: String, color: Color = Color.Black, size: TextUnit = 13.sp, weight: FontWeight = FontWeight.Light) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(label, Modifier.weight(1f), color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.ExtraLight)
        Text(value, Modifier.weight(1f), color = color, fontSize = size, fontWeight = weight, textAlign = TextAlign.End)
    }
}

@Composable
private fun InfoCard(message: String) {
    OrderCardFrame {
        Text(message, color = FigmaMuted, fontSize = 13.sp, modifier = Modifier.testTag("orders_message"))
    }
}

/** Figma "iPhone 17 - 10": dim overlay + "Bạn có chắc chắn giao đến nơi?" */
@Composable
private fun ConfirmDeliveredDialog(onBack: () -> Unit, onConfirm: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0x75000000)).clickable(onClick = onBack, indication = null,
        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }),
        contentAlignment = Alignment.Center) {
        Column(Modifier.width(272.dp).shadow(8.dp, RoundedCornerShape(12.dp)).background(Color.White, RoundedCornerShape(12.dp))
            .pointerInput(Unit) { detectTapGestures { } }.padding(top = 15.dp, bottom = 14.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(17.dp)) {
            Text("Bạn có chắc chắn giao đến nơi?", color = FigmaAction, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Box(Modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, Color(0x29000000), RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClick = onBack).padding(10.dp).testTag("confirm_back")) {
                    Text("Quay lại", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Box(Modifier.clip(RoundedCornerShape(12.dp)).background(FigmaConfirmBg).border(1.dp, FigmaAction, RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClick = onConfirm).padding(10.dp).testTag("confirm_delivered")) {
                    Text("Chắc chắn", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun orderCode(orderId: String) = "#" + (if (orderId.length > 12) orderId.take(8) else orderId).uppercase()
private fun km(value: Double) = String.format(Locale.forLanguageTag("vi-VN"), "%.1f km", value)
private fun vnd(value: Long) = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN")).format(value) + "đ"
