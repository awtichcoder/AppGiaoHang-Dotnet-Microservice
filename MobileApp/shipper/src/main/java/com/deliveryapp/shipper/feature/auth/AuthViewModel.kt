package com.deliveryapp.shipper.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deliveryapp.shipper.data.model.Driver
import com.deliveryapp.shipper.data.model.RepositoryException
import com.deliveryapp.shipper.data.repository.AuthRepository
import com.deliveryapp.shipper.data.repository.MockShipperRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val email: String = "", val password: String = "", val loading: Boolean = false,
    val emailError: String? = null, val passwordError: String? = null,
    val error: String? = null, val user: Driver? = null,
)
class AuthViewModel(private val repository: AuthRepository) : ViewModel() {
    private val mutable = MutableStateFlow(AuthUiState())
    val state = mutable.asStateFlow()
    fun email(value: String) { mutable.update { it.copy(email = value, emailError = null, error = null) } }
    fun password(value: String) { mutable.update { it.copy(password = value, passwordError = null, error = null) } }
    fun fillDemo() { mutable.update { it.copy(email = MockShipperRepository.DEMO_EMAIL, password = MockShipperRepository.DEMO_PASSWORD, emailError = null, passwordError = null, error = null) } }
    fun login() {
        if (state.value.loading) return
        val current = state.value
        val email = current.email.trim()
        val emailError = if (!email.matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) "Nhập địa chỉ email hợp lệ." else null
        val passwordError = if (current.password.isBlank()) "Vui lòng nhập mật khẩu." else null
        mutable.update { it.copy(emailError = emailError, passwordError = passwordError, error = null) }
        if (emailError != null || passwordError != null) return
        mutable.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val user = repository.login(email.lowercase(), current.password)
                if (user.role != "DRIVER") {
                    repository.logout()
                    throw RepositoryException("DRIVER_REQUIRED", "Tài khoản này không có quyền tài xế.")
                }
                mutable.value = AuthUiState(user = user)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(error = e.message ?: "Không thể đăng nhập.", loading = false) } }
        }
    }
    fun logout(onSuccess: () -> Unit) {
        if (state.value.loading) return
        mutable.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try { repository.logout(); mutable.value = AuthUiState(); onSuccess() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(loading = false, error = e.message ?: "Chưa thể đăng xuất.") } }
        }
    }
}
