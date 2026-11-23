package com.deliveryapp.customer.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.deliveryapp.customer.ui.components.AppSurface
import com.deliveryapp.customer.ui.components.AppTextField
import com.deliveryapp.customer.ui.components.DeliveryCard
import com.deliveryapp.customer.ui.components.GreenHeader
import com.deliveryapp.customer.ui.components.PrimaryAction
import com.deliveryapp.customer.ui.components.SecondaryAction
import com.deliveryapp.customer.ui.theme.DeliveryMuted

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onLogin: () -> Unit,
    onRegister: () -> Unit
) {
    val state = viewModel.state
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.loginCompleted) {
        if (state.loginCompleted) {
            viewModel.consumeLoginCompleted()
            onLogin()
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    AppSurface {
        Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                GreenHeader(
                    title = "DeliveryApp",
                    subtitle = "Đăng nhập để tạo đơn và theo dõi giao hàng"
                )
                DeliveryCard(
                    modifier = Modifier.padding(horizontal = 20.dp)
                ) {
                    Text(text = "Đăng nhập", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        text = "Sử dụng email và mật khẩu đã đăng ký.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DeliveryMuted
                    )
                    AppTextField(
                        value = state.loginEmail,
                        onValueChange = viewModel::onLoginEmailChange,
                        label = "Email",
                        error = state.loginEmailError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )
                    AppTextField(
                        value = state.loginPassword,
                        onValueChange = viewModel::onLoginPasswordChange,
                        label = "Mật khẩu",
                        error = state.loginPasswordError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = PasswordVisualTransformation()
                    )
                    PrimaryAction(
                        label = "Đăng nhập",
                        onClick = viewModel::submitLogin,
                        loading = state.isLoading
                    )
                    TextButton(onClick = onRegister, modifier = Modifier.fillMaxWidth()) {
                        Text("Chưa có tài khoản? Tạo tài khoản")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun RegisterScreen(
    viewModel: AuthViewModel,
    onRegistered: () -> Unit,
    onBack: () -> Unit
) {
    val state = viewModel.state
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.registerCompleted) {
        if (state.registerCompleted) {
            viewModel.consumeRegisterCompleted()
            onRegistered()
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    AppSurface {
        Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                GreenHeader(
                    title = "Tạo tài khoản",
                    subtitle = "Bắt đầu đặt giao hàng với hồ sơ khách hàng mới"
                )
                DeliveryCard(
                    modifier = Modifier.padding(horizontal = 20.dp)
                ) {
                    Text(text = "Thông tin đăng ký", style = MaterialTheme.typography.headlineMedium)
                    AppTextField(
                        value = state.registerName,
                        onValueChange = viewModel::onRegisterNameChange,
                        label = "Họ và tên",
                        error = state.registerNameError
                    )
                    AppTextField(
                        value = state.registerEmail,
                        onValueChange = viewModel::onRegisterEmailChange,
                        label = "Email",
                        error = state.registerEmailError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )
                    AppTextField(
                        value = state.registerPhone,
                        onValueChange = viewModel::onRegisterPhoneChange,
                        label = "Số điện thoại",
                        error = state.registerPhoneError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                    AppTextField(
                        value = state.registerPassword,
                        onValueChange = viewModel::onRegisterPasswordChange,
                        label = "Mật khẩu",
                        error = state.registerPasswordError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = PasswordVisualTransformation()
                    )
                    PrimaryAction(
                        label = "Tạo tài khoản",
                        onClick = viewModel::submitRegister,
                        loading = state.isLoading
                    )
                    SecondaryAction(label = "Quay lại đăng nhập", onClick = onBack)
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun SessionCheckScreen() {
    AppSurface {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            GreenHeader(
                title = "DeliveryApp",
                subtitle = "Đang kiểm tra phiên đăng nhập"
            )
        }
    }
}
