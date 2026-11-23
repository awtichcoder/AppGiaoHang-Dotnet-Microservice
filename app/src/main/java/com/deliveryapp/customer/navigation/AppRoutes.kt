package com.deliveryapp.customer.navigation

object AppRoutes {
    const val Session = "session"
    const val Login = "login"
    const val Register = "register"
    const val Home = "home"
    const val CreateOrder = "create-order"
    const val OrderTracking = "order/{orderId}"
    const val Orders = "orders"
    const val Rewards = "rewards"
    const val Profile = "profile"
    const val Complaints = "complaints"
    const val ComplaintDetail = "complaints/{complaintId}"
    const val ComplaintNew = "complaint-new/{orderId}"

    fun orderTracking(orderId: String) = "order/$orderId"
    fun complaintDetail(complaintId: String) = "complaints/$complaintId"
    fun complaintNew(orderId: String) = "complaint-new/$orderId"
}
