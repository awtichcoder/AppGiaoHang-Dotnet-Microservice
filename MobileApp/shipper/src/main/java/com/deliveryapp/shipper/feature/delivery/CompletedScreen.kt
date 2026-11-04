package com.deliveryapp.shipper.feature.delivery

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deliveryapp.shipper.ui.theme.FigmaAction
import com.deliveryapp.shipper.ui.theme.FigmaCompleteBg

/** Figma "iPhone 17 - 11": congratulation text + "Quay về Trang Chủ". */
@Composable
fun CompletedScreen(onHome: () -> Unit) {
    BackHandler(onBack = onHome)
    Box(Modifier.fillMaxSize().background(Color.White).systemBarsPadding()) {
        // Title sits at ~35% of the screen height in the design (y 314 of 887).
        Column(Modifier.align(BiasAlignment(0f, -0.3f)).padding(horizontal = 43.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(80.dp)) {
            Text("Chúc Mừng Bạn Đã Hoàn Thành Đơn Hàng", Modifier.widthIn(max = 316.dp).testTag("completed_title"),
                color = Color.Black, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                lineHeight = 24.sp)
            Box(Modifier.clip(RoundedCornerShape(12.dp)).background(FigmaCompleteBg)
                .border(1.dp, FigmaAction, RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClick = onHome).padding(10.dp).testTag("completion_home")) {
                Text("Quay về Trang Chủ", color = Color.Black, fontSize = 14.sp)
            }
        }
    }
}
