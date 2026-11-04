package com.deliveryapp.customer

import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController
import com.deliveryapp.customer.navigation.AppNavHost

@Composable
fun DeliveryCustomerApp() {
    val navController = rememberNavController()
    AppNavHost(navController = navController)
}
