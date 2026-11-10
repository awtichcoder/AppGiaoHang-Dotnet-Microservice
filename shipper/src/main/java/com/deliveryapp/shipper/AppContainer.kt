package com.deliveryapp.shipper

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.deliveryapp.shipper.core.network.NetworkFactory
import com.deliveryapp.shipper.core.storage.SettingsStore
import com.deliveryapp.shipper.core.storage.TokenManager
import com.deliveryapp.shipper.data.api.DeliveryApi
import com.deliveryapp.shipper.data.api.DriverApi
import com.deliveryapp.shipper.data.api.IdentityApi
import com.deliveryapp.shipper.data.repository.*
import com.deliveryapp.shipper.feature.auth.AuthViewModel
import com.deliveryapp.shipper.feature.work.WorkViewModel
import com.deliveryapp.shipper.feature.profile.SettingsViewModel
import com.deliveryapp.shipper.feature.delivery.LocationViewModel
import com.deliveryapp.shipper.core.location.DeviceLocationReader

class AppContainer(context: Context) {
    /** Build with -PuseMock=true for the offline UI demo; the default build calls the real backend. */
    val mockMode: Boolean = BuildConfig.USE_MOCK_API
    val settings = SettingsStore(context)
    val locationReader = DeviceLocationReader(context)
    val tokens by lazy { TokenManager(context) }
    val retrofit by lazy { NetworkFactory.create(tokens) }

    private val repository: Any by lazy {
        if (mockMode) MockShipperRepository()
        else LiveShipperRepository(
            tokens = tokens,
            identity = retrofit.create(IdentityApi::class.java),
            driver = retrofit.create(DriverApi::class.java),
            delivery = retrofit.create(DeliveryApi::class.java),
            location = locationReader,
        )
    }
    val authRepository: AuthRepository get() = repository as AuthRepository
    val workRepository: WorkRepository get() = repository as WorkRepository

    val factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
            modelClass.isAssignableFrom(AuthViewModel::class.java) -> AuthViewModel(authRepository)
            modelClass.isAssignableFrom(WorkViewModel::class.java) ->
                if (mockMode) WorkViewModel(workRepository)
                else WorkViewModel(workRepository, pollIntervalMs = OFFER_POLL_MS, locationEveryPolls = LOCATION_EVERY_POLLS)
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(settings)
            modelClass.isAssignableFrom(LocationViewModel::class.java) -> LocationViewModel(locationReader)
            else -> error("Unknown ViewModel: $modelClass")
        } as T
    }

    private companion object {
        /** Offers live 30 s; polling every 3 s leaves enough time to respond. */
        const val OFFER_POLL_MS = 3_000L
        /** 5 × 3 s = position every 15 s, well inside the backend's 60 s freshness window. */
        const val LOCATION_EVERY_POLLS = 5
    }
}
