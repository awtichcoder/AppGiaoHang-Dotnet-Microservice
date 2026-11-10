package com.deliveryapp.customer

import android.app.Application

class DeliveryCustomerApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
