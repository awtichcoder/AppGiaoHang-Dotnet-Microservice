package com.deliveryapp.customer

import android.content.Context
import com.deliveryapp.customer.core.network.NetworkFactory
import com.deliveryapp.customer.core.storage.TokenManager
import com.deliveryapp.customer.data.api.DeliveryApi
import com.deliveryapp.customer.data.api.IdentityApi
import com.deliveryapp.customer.data.api.LoyaltyApi
import com.deliveryapp.customer.data.api.OrderApi
import com.deliveryapp.customer.data.api.PromotionApi
import com.deliveryapp.customer.data.repository.AuthRepository
import com.deliveryapp.customer.data.repository.LoyaltyRepository
import com.deliveryapp.customer.data.repository.OrderDraftHolder
import com.deliveryapp.customer.data.repository.OrderRepository
import com.deliveryapp.customer.data.repository.ProfileRepository

class AppContainer(context: Context) {
    val tokenManager = TokenManager(context.applicationContext)

    private val retrofit = NetworkFactory.create(tokenManager)

    val identityApi: IdentityApi = retrofit.create(IdentityApi::class.java)
    val orderApi: OrderApi = retrofit.create(OrderApi::class.java)
    val deliveryApi: DeliveryApi = retrofit.create(DeliveryApi::class.java)
    val loyaltyApi: LoyaltyApi = retrofit.create(LoyaltyApi::class.java)
    val promotionApi: PromotionApi = retrofit.create(PromotionApi::class.java)

    val authRepository = AuthRepository(identityApi, tokenManager)
    val profileRepository = ProfileRepository(identityApi)
    val loyaltyRepository = LoyaltyRepository(loyaltyApi)
    val orderRepository = OrderRepository(orderApi, deliveryApi, loyaltyApi, promotionApi)
    val orderDrafts = OrderDraftHolder()
}
