package com.deliveryapp.shipper.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deliveryapp.shipper.data.repository.MockShipperRepository
import com.deliveryapp.shipper.ui.components.*
import com.deliveryapp.shipper.ui.theme.*

@Composable
fun SplashScreen() {
    Column(Modifier.fillMaxSize().background(DeliveryGreen), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        BrandMark(inverse = true)
        Spacer(Modifier.height(24.dp))
        Text("DeliveryApp", color = Color.White, style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text("ĐỒNG HÀNH CÙNG TÀI XẾ", color = Color.White, fontSize = 12.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(40.dp))
        CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
    }
}
@Composable
fun LoginScreen(state: AuthUiState, onEmail: (String) -> Unit, onPassword: (String) -> Unit, onLogin: () -> Unit, onDemo: () -> Unit) {
    var visible by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    AppScreen("DeliveryApp", "ỨNG DỤNG TÀI XẾ", actions = { BrandMark() }) {
        Surface(color = DeliveryGreenDark, shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.DeliveryDining, null, Modifier.size(52.dp), Color(0xFF9FE3BC))
                Text("Mỗi chuyến đi,\nmột niềm tin.", style = MaterialTheme.typography.headlineLarge, color = Color.White)
                Text("Sẵn sàng cho hành trình hôm nay cùng DeliveryApp.", color = Color(0xFFD1EEE0), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Chào mừng trở lại", style = MaterialTheme.typography.headlineMedium)
            Text("Đăng nhập bằng tài khoản tài xế của bạn.", color = DeliveryMuted, style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedTextField(state.email, onEmail, Modifier.fillMaxWidth().testTag("email"), enabled = !state.loading,
            label = { Text("Email") }, leadingIcon = { Icon(Icons.Outlined.MailOutline, null) },
            shape = RoundedCornerShape(14.dp), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            isError = state.emailError != null, supportingText = state.emailError?.let { { Text(it) } })
        OutlinedTextField(state.password, onPassword, Modifier.fillMaxWidth().testTag("password"), enabled = !state.loading,
            label = { Text("Mật khẩu") }, leadingIcon = { Icon(Icons.Outlined.Lock, null) },
            trailingIcon = { IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (visible) "Ẩn mật khẩu" else "Hiện mật khẩu") } },
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            shape = RoundedCornerShape(14.dp), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); onLogin() }),
            isError = state.passwordError != null, supportingText = state.passwordError?.let { { Text(it) } })
        state.error?.let { ErrorState(it) }
        PrimaryAction("Đăng nhập", { focus.clearFocus(); onLogin() }, Modifier.testTag("login"), loading = state.loading, icon = Icons.AutoMirrored.Outlined.ArrowForward)
        Text("Tài khoản tài xế do quản trị viên cấp. Liên hệ quản trị viên nếu cần đặt lại mật khẩu.", style = MaterialTheme.typography.bodySmall, color = DeliveryMuted)
        // Live mode: the same credentials are the backend's local development seed (debug builds only).
        val demo = LocalDemoMode.current
        if (demo || com.deliveryapp.shipper.BuildConfig.DEBUG) DeliveryCard(color = DeliveryGreenSoft) {
            DemoBadge()
            Text(if (demo) "Dùng thử giao diện, không cần máy chủ." else "Tài khoản tài xế phát triển do backend local tạo sẵn.", fontWeight = FontWeight.SemiBold)
            Text(MockShipperRepository.DEMO_EMAIL + " / " + MockShipperRepository.DEMO_PASSWORD, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onDemo, enabled = !state.loading, modifier = Modifier.testTag("fill_demo")) { Text("Điền tài khoản dùng thử") }
        }
    }
}
