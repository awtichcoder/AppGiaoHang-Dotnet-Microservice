package com.deliveryapp.shipper.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.deliveryapp.shipper.ShipperApplication
import com.deliveryapp.shipper.feature.auth.*
import com.deliveryapp.shipper.feature.work.*
import com.deliveryapp.shipper.feature.home.HomeScreen
import com.deliveryapp.shipper.feature.promotion.PromotionScreen
import com.deliveryapp.shipper.data.model.Availability
import com.deliveryapp.shipper.ui.components.DriverBottomBar
import com.deliveryapp.shipper.feature.delivery.*
import com.deliveryapp.shipper.feature.history.HistoryScreen
import com.deliveryapp.shipper.feature.profile.*
import com.deliveryapp.shipper.ui.components.DemoScenarios
import com.deliveryapp.shipper.ui.theme.*
import kotlinx.coroutines.delay

object Routes {
    const val Splash = "splash"; const val Login = "login"; const val Home = "home"
    const val Offer = "offer"; const val Detail = "detail"; const val Tracking = "tracking"
    const val Completed = "completed"; const val History = "history"; const val Profile = "profile"
    const val HistoryDetail = "history/{id}"; const val Promotions = "promotions"
}
@Composable
fun ShipperNavHost(auth: AuthViewModel, work: WorkViewModel, settings: SettingsViewModel, largeText: Boolean) {
    val nav = rememberNavController()
    val authState by auth.state.collectAsStateWithLifecycle()
    val workState by work.state.collectAsStateWithLifecycle()
    val settingsError by settings.error.collectAsStateWithLifecycle()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    var demoDialog by rememberSaveable { mutableStateOf(false) }
    val owner = LocalLifecycleOwner.current
    val container = (LocalContext.current.applicationContext as ShipperApplication).container
    val location: LocationViewModel = viewModel(factory = container.factory)
    val context = LocalContext.current
    // The backend only offers trips to drivers with a fresh GPS position, so ask before going online.
    val goOnlinePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        work.toggleAvailability()
    }
    fun toggleAvailability() {
        val goingOnline = work.state.value.availability == com.deliveryapp.shipper.data.model.Availability.OFFLINE
        val granted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (!container.mockMode && goingOnline && !granted) {
            goOnlinePermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else work.toggleAvailability()
    }

    fun mainRoute(route: String) {
        nav.navigate(route) { popUpTo(Routes.Home) { saveState = true }; launchSingleTop = true; restoreState = true }
    }
    fun home() { nav.navigate(Routes.Home) { popUpTo(Routes.Home) { inclusive = true }; launchSingleTop = true } }
    LaunchedEffect(authState.user, currentRoute) {
        if (currentRoute == Routes.Login && authState.user != null) {
            nav.navigate(Routes.Home) { popUpTo(Routes.Login) { inclusive = true } }
        } else if (authState.user == null && currentRoute != null && currentRoute != Routes.Splash && currentRoute != Routes.Login) {
            work.reset()
            location.reset()
            nav.navigate(Routes.Login) { popUpTo(0) { inclusive = true } }
        }
    }
    LaunchedEffect(work, nav) {
        work.events.collect { event ->
            if (auth.state.value.user != null) when (event) {
                // Figma keeps the accepted trip on the home screen (expanded order card).
                WorkEvent.Accepted -> home()
                WorkEvent.Completed -> nav.navigate(Routes.Completed) { popUpTo(Routes.Home); launchSingleTop = true }
            }
        }
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) work.tick() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    if (demoDialog) DemoScenarios({ demoDialog = false }, work::scenario)
    val bottom: @Composable () -> Unit = { DriverBottomBar(currentRoute, ::mainRoute) }
    // Keep the home map on the device position while the driver is online (same reader as the backend heartbeat).
    val online = workState.availability != Availability.OFFLINE
    LaunchedEffect(currentRoute, online) {
        if (currentRoute != Routes.Home || !online) return@LaunchedEffect
        while (true) {
            val granted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
            if (granted) location.refresh()
            delay(15_000)
        }
    }
    NavHost(navController = nav, startDestination = Routes.Splash) {
        composable(Routes.Splash) {
            SplashScreen()
            LaunchedEffect(Unit) { delay(750); nav.navigate(if (auth.state.value.user == null) Routes.Login else Routes.Home) { popUpTo(Routes.Splash) { inclusive = true } } }
        }
        composable(Routes.Login) { LoginScreen(authState, auth::email, auth::password, auth::login, auth::fillDemo) }
        composable(Routes.Home) {
            // Offers, the active trip and its steps all live on the home screen in the Figma design.
            val locationState by location.state.collectAsStateWithLifecycle()
            HomeScreen(authState.user, workState, locationState, bottom, ::toggleAvailability,
                work::accept, work::reject, work::advance)
        }
        composable(Routes.Completed) { CompletedScreen(::home) }
        composable(Routes.Promotions) { PromotionScreen(bottom) }
        composable(Routes.History) {
            LaunchedEffect(Unit) { work.loadHistory() }
            HistoryScreen(workState, bottom, work::loadHistory) { id -> nav.navigate("history/${Uri.encode(id)}") }
        }
        composable(Routes.HistoryDetail) { entry ->
            val trip = workState.history.find { it.deliveryId == entry.arguments?.getString("id") }
            DetailScreen(trip, workState, true, { nav.popBackStack() }, {}, {})
        }
        composable(Routes.Profile) {
            ProfileScreen(authState, workState, largeText, settingsError, bottom, settings::largeText, { demoDialog = true }) {
                if (work.state.value.active == null && !work.state.value.loading) auth.logout { work.reset(); location.reset() }
            }
        }
    }
}
