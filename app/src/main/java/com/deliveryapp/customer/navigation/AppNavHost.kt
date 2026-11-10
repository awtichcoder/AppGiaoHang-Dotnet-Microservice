package com.deliveryapp.customer.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.deliveryapp.customer.DeliveryCustomerApplication
import com.deliveryapp.customer.feature.auth.AuthViewModel
import com.deliveryapp.customer.feature.auth.LoginScreen
import com.deliveryapp.customer.feature.auth.RegisterScreen
import com.deliveryapp.customer.feature.auth.SessionCheckScreen
import com.deliveryapp.customer.feature.auth.SessionViewModel
import com.deliveryapp.customer.feature.home.HomeScreen
import com.deliveryapp.customer.feature.home.HomeViewModel
import com.deliveryapp.customer.feature.order.CreateOrderScreen
import com.deliveryapp.customer.feature.order.CreateOrderViewModel
import com.deliveryapp.customer.feature.order.OrderTrackingScreen
import com.deliveryapp.customer.feature.order.OrderTrackingViewModel
import com.deliveryapp.customer.feature.order.OrdersViewModel
import com.deliveryapp.customer.feature.order.OrdersScreen
import com.deliveryapp.customer.feature.profile.ProfileScreen
import com.deliveryapp.customer.feature.profile.ProfileViewModel
import com.deliveryapp.customer.feature.rewards.RewardsScreen
import com.deliveryapp.customer.feature.rewards.RewardsViewModel

@Composable
fun AppNavHost(navController: NavHostController) {
    val container = (LocalContext.current.applicationContext as DeliveryCustomerApplication).container

    fun openLoginCleared() {
        navController.navigate(AppRoutes.Login) {
            launchSingleTop = true
            popUpTo(0) { inclusive = true }
        }
    }

    fun openMain(route: String) {
        navController.navigate(route) {
            launchSingleTop = true
            popUpTo(AppRoutes.Home) { inclusive = false }
        }
    }

    NavHost(navController = navController, startDestination = AppRoutes.Session) {
        composable(AppRoutes.Session) {
            val sessionViewModel: SessionViewModel = viewModel(factory = SessionViewModel.factory(container))
            val state = sessionViewModel.state
            LaunchedEffect(state.isChecking, state.isAuthenticated) {
                if (!state.isChecking) {
                    navController.navigate(if (state.isAuthenticated) AppRoutes.Home else AppRoutes.Login) {
                        popUpTo(AppRoutes.Session) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            SessionCheckScreen()
        }
        composable(AppRoutes.Login) {
            val authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.factory(container))
            LoginScreen(
                viewModel = authViewModel,
                onLogin = {
                    navController.navigate(AppRoutes.Home) {
                        popUpTo(AppRoutes.Login) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onRegister = { navController.navigate(AppRoutes.Register) }
            )
        }
        composable(AppRoutes.Register) {
            val authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.factory(container))
            RegisterScreen(
                viewModel = authViewModel,
                onRegistered = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }
        composable(AppRoutes.Home) {
            val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(container))
            HomeScreen(
                viewModel = homeViewModel,
                onCreateOrder = { navController.navigate(AppRoutes.CreateOrder) },
                onOrders = { openMain(AppRoutes.Orders) },
                onRewards = { openMain(AppRoutes.Rewards) },
                onProfile = { openMain(AppRoutes.Profile) },
                onSessionExpired = { openLoginCleared() }
            )
        }
        composable(AppRoutes.CreateOrder) {
            val createViewModel: CreateOrderViewModel = viewModel(factory = CreateOrderViewModel.factory(container))
            CreateOrderScreen(
                viewModel = createViewModel,
                onBack = { navController.popBackStack() },
                // Back from tracking must not reopen the filled-in form (it would create a second order).
                onCreated = { orderId ->
                    navController.navigate(AppRoutes.orderTracking(orderId)) {
                        popUpTo(AppRoutes.CreateOrder) { inclusive = true }
                    }
                },
                onSessionExpired = { openLoginCleared() }
            )
        }
        composable(
            AppRoutes.OrderTracking,
            arguments = listOf(navArgument("orderId") { type = NavType.StringType })
        ) { entry ->
            val orderId = entry.arguments?.getString("orderId").orEmpty()
            val trackingViewModel: OrderTrackingViewModel =
                viewModel(factory = OrderTrackingViewModel.factory(container, orderId))
            OrderTrackingScreen(
                viewModel = trackingViewModel,
                onOrders = { openMain(AppRoutes.Orders) },
                onReorder = { navController.navigate(AppRoutes.CreateOrder) },
                onSessionExpired = { openLoginCleared() }
            )
        }
        composable(AppRoutes.Orders) {
            val ordersViewModel: OrdersViewModel = viewModel(factory = OrdersViewModel.factory(container))
            OrdersScreen(
                viewModel = ordersViewModel,
                onOpen = { navController.navigate(AppRoutes.orderTracking(it)) },
                onCreateOrder = { navController.navigate(AppRoutes.CreateOrder) },
                onSessionExpired = { openLoginCleared() },
                onHome = { openMain(AppRoutes.Home) },
                onRewards = { openMain(AppRoutes.Rewards) },
                onProfile = { openMain(AppRoutes.Profile) }
            )
        }
        composable(AppRoutes.Rewards) {
            val rewardsViewModel: RewardsViewModel = viewModel(factory = RewardsViewModel.factory(container))
            RewardsScreen(
                viewModel = rewardsViewModel,
                onBack = { openMain(AppRoutes.Home) },
                onUsePoints = { navController.navigate(AppRoutes.CreateOrder) },
                onSessionExpired = { openLoginCleared() }
            )
        }
        composable(AppRoutes.Profile) {
            val profileViewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.factory(container))
            ProfileScreen(
                viewModel = profileViewModel,
                onLogout = { openLoginCleared() },
                onHome = { openMain(AppRoutes.Home) },
                onOrders = { openMain(AppRoutes.Orders) },
                onRewards = { openMain(AppRoutes.Rewards) },
                onSessionExpired = { openLoginCleared() }
            )
        }
    }
}
