@file:Suppress("DEPRECATION")
package com.deliveryapp.shipper.feature.delivery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.deliveryapp.shipper.data.model.Point
import com.deliveryapp.shipper.data.model.Trip
import com.deliveryapp.shipper.ui.components.LocalDemoMode
import com.deliveryapp.shipper.ui.components.StatusBadge
import com.deliveryapp.shipper.ui.theme.*
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

private val demoDriver = Point(10.7755, 106.6996)

@Composable
fun DeliveryMap(
    trip: Trip?, location: LocationUiState,
    modifier: Modifier = Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(20.dp)),
) {
    var attempt by remember { mutableIntStateOf(0) }
    key(attempt) {
        NativeMap(trip, location, modifier, onRetry = { attempt++ })
    }
}
@Composable
private fun NativeMap(trip: Trip?, location: LocationUiState, modifier: Modifier, onRetry: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val cameraPadding = with(LocalDensity.current) { 90.dp.roundToPx() }
    var ready by remember(owner) { mutableStateOf<MapLibreMap?>(null) }
    var error by remember(owner) { mutableStateOf<String?>(null) }
    var disposed by remember(owner) { mutableStateOf(false) }
    val mapView = remember(context, owner) {
        MapLibre.getInstance(context)
        MapView(context).apply {
            onCreate(null)
            addOnDidFailLoadingMapListener { if (!disposed) error = "Không tải được nền bản đồ. Kiểm tra mạng hoặc mở Google Maps." }
        }
    }
    DisposableEffect(mapView, owner) {
        val lifecycle = owner.lifecycle
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            disposed = true
            lifecycle.removeObserver(observer)
            mapView.onPause(); mapView.onStop(); mapView.onDestroy()
        }
    }
    LaunchedEffect(mapView) {
        delay(15_000)
        if (ready == null) error = "Bản đồ tải chậm. Bạn có thể thử lại hoặc mở Google Maps."
    }
    val driver = location.fix?.point ?: demoDriver
    LaunchedEffect(ready, driver, trip?.deliveryId, cameraPadding) {
        val map = ready ?: return@LaunchedEffect
        map.clear()
        val points = mutableListOf<LatLng>()
        fun mark(point: Point?, title: String) {
            if (point == null) return
            val latLng = LatLng(point.latitude, point.longitude)
            points.add(latLng)
            map.addMarker(MarkerOptions().position(latLng).title(title))
        }
        mark(trip?.pickup, "A · Lấy hàng")
        mark(trip?.dropoff, "B · Giao hàng")
        mark(driver, if (location.fix != null) "Vị trí thiết bị" else "Vị trí tài xế mô phỏng")
        mapView.post {
            if (!disposed) {
                if (points.size >= 2) map.moveCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(points).build(), cameraPadding))
                else map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(driver.latitude, driver.longitude), 13.0))
            }
        }
    }
    Box(modifier.background(DeliveryGreenSoft)) {
        AndroidView(factory = {
            mapView.apply { getMapAsync { map ->
                if (!disposed) {
                    map.uiSettings.isCompassEnabled = true
                    map.setStyle("https://tiles.openfreemap.org/styles/liberty") {
                        if (!disposed) { error = null; ready = map }
                    }
                }
            } }
        }, modifier = Modifier.fillMaxSize())
        if (LocalDemoMode.current) Box(Modifier.align(Alignment.TopStart).padding(12.dp)) {
            StatusBadge(if (location.fix != null) "GPS thiết bị · Điểm giao mẫu" else "Vị trí & điểm giao mô phỏng", icon = Icons.Outlined.Map)
        }
        if (ready == null && error == null) CircularProgressIndicator(Modifier.align(Alignment.Center).size(30.dp).testTag("map_loading"))
        error?.let { message ->
            Surface(Modifier.align(Alignment.Center).padding(24.dp), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp)) { Text(message, style = MaterialTheme.typography.bodySmall); TextButton(onClick = onRetry) { Text("Tải lại bản đồ") } }
            }
        }
        Surface(Modifier.align(Alignment.BottomStart).padding(6.dp), color = DeliverySurface.copy(alpha = .9f)) {
            Text("© OpenFreeMap · OpenMapTiles · OpenStreetMap", Modifier.padding(4.dp), style = MaterialTheme.typography.labelSmall)
        }
    }
}
