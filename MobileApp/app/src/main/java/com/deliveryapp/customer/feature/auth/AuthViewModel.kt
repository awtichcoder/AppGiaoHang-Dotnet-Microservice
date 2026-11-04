package com.deliveryapp.customer.feature.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.deliveryapp.customer.AppContainer
import com.deliveryapp.customer.core.network.ApiResult
import com.deliveryapp.customer.data.dto.LoginRequest
import com.deliveryapp.customer.data.dto.RegisterRequest
import com.deliveryapp.customer.data.repository.AuthGateway
import kotlinx.coroutines.launch

class AuthViewModel(private val authGateway: AuthGateway) : ViewModel() {
    var state by mutableStateOf(AuthUiState())
        private set

    fun onLoginEmailChange(value: String) {
        state = state.copy(loginEmail = value, loginEmailError = null, message = null)
    }

    fun onLoginPasswordChange(value: String) {
        state = state.copy(loginPassword = value, loginPasswordError = null, message = null)
    }

    fun onRegisterNameChange(value: String) {
        state = state.copy(registerName = value, registerNameError = null, message = null)
    }

    fun onRegisterEmailChange(value: String) {
        state = state.copy(registerEmail = value, registerEmailError = null, message = null)
    }

    fun onRegisterPhoneChange(value: String) {
        state = state.copy(registerPhone = value, registerPhoneError = null, message = null)
    }

    fun onRegisterPasswordChange(value: String) {
        state = state.copy(registerPassword = value, registerPasswordError = null, message = null)
    }

    fun submitLogin() {
        val validation = validateLogin(state.loginEmail, state.loginPassword)
        state = state.copy(
            loginEmailError = validation.emailError,
            loginPasswordError = validation.passwordError,
            message = null
        )
        if (!validation.isValid) return

        viewModelScope.launch {
            state = state.copy(isLoading = true)
            val result = authGateway.login(
                LoginRequest(
                    email = state.loginEmail.trim(),
                    password = state.loginPassword
                )
            )
            state = when (result) {
                is ApiResult.Success -> state.copy(isLoading = false, loginCompleted = true)
                is ApiResult.Error -> state.copy(isLoading = false, message = result.message)
                ApiResult.Loading -> state.copy(isLoading = true)
            }
        }
    }

    fun submitRegister() {
        val validation = validateRegister(state)
        state = state.copy(
            registerNameError = validation.nameError,
            registerEmailError = validation.emailError,
            registerPhoneError = validation.phoneError,
            registerPasswordError = validation.passwordError,
            message = null
        )
        if (!validation.isValid) return

        viewModelScope.launch {
            state = state.copy(isLoading = true)
            val result = authGateway.register(
                RegisterRequest(
                    email = state.registerEmail.trim(),
                    password = state.registerPassword,
                    fullName = state.registerName.trim(),
                    phone = state.registerPhone.trim()
                )
            )
            state = when (result) {
                is ApiResult.Success -> state.copy(
                    isLoading = false,
                    registerCompleted = true,
                    message = "Tạo tài khoản thành công. Hãy đăng nhập để tiếp tục."
                )
                is ApiResult.Error -> state.copy(isLoading = false, message = result.message)
                ApiResult.Loading -> state.copy(isLoading = true)
            }
        }
    }

    fun consumeLoginCompleted() {
        state = state.copy(loginCompleted = false)
    }

    fun consumeRegisterCompleted() {
        state = state.copy(registerCompleted = false)
    }

    fun clearMessage() {
        state = state.copy(message = null)
    }

    private fun validateLogin(email: String, password: String): LoginValidation {
        val emailError = validateEmail(email)
        val passwordError = when {
            password.isBlank() -> "Nhập mật khẩu"
            password.length < 6 -> "Mật khẩu tối thiểu 6 ký tự"
            else -> null
        }
        return LoginValidation(emailError, passwordError)
    }

    private fun validateRegister(state: AuthUiState): RegisterValidation {
        val name = state.registerName.trim()
        val phone = state.registerPhone.trim()
        val password = state.registerPassword
        return RegisterValidation(
            nameError = when {
                name.isBlank() -> "Nhập họ tên"
                name.length > 200 -> "Họ tên tối đa 200 ký tự"
                else -> null
            },
            emailError = validateEmail(state.registerEmail),
            phoneError = when {
                phone.isBlank() -> "Nhập số điện thoại"
                phone.length > 20 -> "Số điện thoại tối đa 20 ký tự"
                !phone.matches(Regex("^[0-9+\\s-]{9,20}$")) -> "Số điện thoại chưa hợp lệ"
                else -> null
            },
            passwordError = when {
                password.isBlank() -> "Nhập mật khẩu"
                password.length < 6 -> "Mật khẩu tối thiểu 6 ký tự"
                password.length > 100 -> "Mật khẩu tối đa 100 ký tự"
                else -> null
            }
        )
    }

    private fun validateEmail(email: String): String? {
        val trimmed = email.trim()
        return when {
            trimmed.isBlank() -> "Nhập email"
            trimmed.length > 256 -> "Email tối đa 256 ký tự"
            !trimmed.matches(Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) -> "Email chưa hợp lệ"
            else -> null
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AuthViewModel(container.authRepository) as T
        }
    }
}

data class AuthUiState(
    val loginEmail: String = "",
    val loginPassword: String = "",
    val registerName: String = "",
    val registerEmail: String = "",
    val registerPhone: String = "",
    val registerPassword: String = "",
    val loginEmailError: String? = null,
    val loginPasswordError: String? = null,
    val registerNameError: String? = null,
    val registerEmailError: String? = null,
    val registerPhoneError: String? = null,
    val registerPasswordError: String? = null,
    val isLoading: Boolean = false,
    val message: String? = null,
    val loginCompleted: Boolean = false,
    val registerCompleted: Boolean = false
)

private data class LoginValidation(val emailError: String?, val passwordError: String?) {
    val isValid: Boolean = emailError == null && passwordError == null
}

private data class RegisterValidation(
    val nameError: String?,
    val emailError: String?,
    val phoneError: String?,
    val passwordError: String?
) {
    val isValid: Boolean = nameError == null && emailError == null && phoneError == null && passwordError == null
}
