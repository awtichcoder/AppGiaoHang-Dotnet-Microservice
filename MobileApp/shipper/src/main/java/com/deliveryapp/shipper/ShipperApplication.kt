package com.deliveryapp.shipper
import android.app.Application
class ShipperApplication : Application() {
    val container by lazy { AppContainer(this) }
}
