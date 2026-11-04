package com.deliveryapp.shipper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.deliveryapp.shipper.feature.auth.AuthViewModel
import com.deliveryapp.shipper.feature.work.WorkViewModel
import com.deliveryapp.shipper.feature.profile.SettingsViewModel
import com.deliveryapp.shipper.navigation.ShipperNavHost
import com.deliveryapp.shipper.ui.components.LocalDemoMode
import com.deliveryapp.shipper.ui.theme.DeliveryShipperTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as ShipperApplication).container
        setContent {
            val auth: AuthViewModel = viewModel(factory = container.factory)
            val work: WorkViewModel = viewModel(factory = container.factory)
            val settings: SettingsViewModel = viewModel(factory = container.factory)
            val large by settings.largeText.collectAsStateWithLifecycle()
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, density.fontScale * if (large) 1.15f else 1f),
                LocalDemoMode provides container.mockMode,
            ) {
                DeliveryShipperTheme { ShipperNavHost(auth, work, settings, large) }
            }
        }
    }
}
