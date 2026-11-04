package com.deliveryapp.customer

import com.deliveryapp.customer.core.network.ApiResult
import com.deliveryapp.customer.data.dto.LoginRequest
import com.deliveryapp.customer.data.dto.RegisterRequest
import com.deliveryapp.customer.data.dto.TokenResponse
import com.deliveryapp.customer.data.dto.UserResponse
import com.deliveryapp.customer.data.repository.AuthGateway
import com.deliveryapp.customer.feature.auth.AuthViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun submitRegister_withInvalidFields_showsInlineErrorsAndDoesNotCallApi() = runTest {
        val gateway = FakeAuthGateway()
        val viewModel = AuthViewModel(gateway)

        viewModel.submitRegister()

        assertEquals("Nhập họ tên", viewModel.state.registerNameError)
        assertEquals("Nhập email", viewModel.state.registerEmailError)
        assertEquals("Nhập số điện thoại", viewModel.state.registerPhoneError)
        assertEquals("Nhập mật khẩu", viewModel.state.registerPasswordError)
        assertFalse(gateway.registerCalled)
    }

    @Test
    fun submitLogin_withValidCredentials_marksLoginCompleted() = runTest {
        val gateway = FakeAuthGateway(
            loginResult = ApiResult.Success(TokenResponse("access", "refresh", "2026-09-06T00:00:00Z"))
        )
        val viewModel = AuthViewModel(gateway)

        viewModel.onLoginEmailChange("customer@example.com")
        viewModel.onLoginPasswordChange("secret123")
        viewModel.submitLogin()

        assertTrue(gateway.loginCalled)
        assertTrue(viewModel.state.loginCompleted)
        assertFalse(viewModel.state.isLoading)
    }

    @Test
    fun submitLogin_whenApiFails_showsMessage() = runTest {
        val gateway = FakeAuthGateway(
            loginResult = ApiResult.Error(code = "INVALID_CREDENTIALS", message = "Sai email hoặc mật khẩu", httpStatus = 401)
        )
        val viewModel = AuthViewModel(gateway)

        viewModel.onLoginEmailChange("customer@example.com")
        viewModel.onLoginPasswordChange("secret123")
        viewModel.submitLogin()

        assertTrue(gateway.loginCalled)
        assertEquals("Sai email hoặc mật khẩu", viewModel.state.message)
        assertFalse(viewModel.state.loginCompleted)
    }
}

private class FakeAuthGateway(
    var registerResult: ApiResult<UserResponse> = ApiResult.Success(
        UserResponse("user-1", "Customer", "customer@example.com", "0900000000", "Customer")
    ),
    var loginResult: ApiResult<TokenResponse> = ApiResult.Success(
        TokenResponse("access", "refresh", "2026-09-06T00:00:00Z")
    )
) : AuthGateway {
    var registerCalled = false
    var loginCalled = false

    override suspend fun register(request: RegisterRequest): ApiResult<UserResponse> {
        registerCalled = true
        return registerResult
    }

    override suspend fun login(request: LoginRequest): ApiResult<TokenResponse> {
        loginCalled = true
        return loginResult
    }

    override suspend fun logout() = Unit

    override suspend fun validateStoredSession(): Boolean = true
}
