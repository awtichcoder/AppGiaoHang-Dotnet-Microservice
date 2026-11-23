@file:Suppress("DEPRECATION")
package com.deliveryapp.customer.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.deliveryapp.customer.data.repository.GeoPoint
import com.deliveryapp.customer.ui.theme.DeliveryGreen
import com.deliveryapp.customer.ui.theme.DeliveryGreenSoft
import com.deliveryapp.customer.ui.theme.DeliveryText
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

/** Same tiles as the driver app. */
private const val MAP_STYLE = "https://tiles.openfreemap.org/styles/liberty"
val HoChiMinhCity = GeoPoint(10.7769, 106.7009)
val PickupColor = DeliveryGreen
val DropoffColor = Color(0xFFD92D20)
val DriverColor = Color(0xFF1E6FD9)

data class MapMarker(val point: GeoPoint, val label: String, val color: Color, val title: String)

/**
 * MapLibre map in Compose.
 * - With [markers]: shows them and fits the camera when a marker is added (not on every move, so
 *   polling the driver position does not fight the user's panning).
 * - With [onCenterChange]: picker mode; reports the camera centre whenever the map stops moving.
 * - [focus] moves the camera there whenever its value changes (for "Dùng vị trí hiện tại").
 * - [interactive] = false for maps embedded in a scrolling screen, so a swipe scrolls the page.
 */
@Composable
fun DeliveryMap(
    modifier: Modifier,
    markers: List<MapMarker> = emptyList(),
    focus: GeoPoint? = null,
    onCenterChange: ((GeoPoint) -> Unit)? = null,
    interactive: Boolean = true
) {
    var attempt by remember { mutableIntStateOf(0) }
    key(attempt) { NativeMap(modifier, markers, focus, onCenterChange, interactive, onRetry = { attempt++ }) }
}

@Composable
private fun NativeMap(
    modifier: Modifier,
    markers: List<MapMarker>,
    focus: GeoPoint?,
    onCenterChange: ((GeoPoint) -> Unit)?,
    interactive: Boolean,
    onRetry: () -> Unit
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val padding = with(LocalDensity.current) { 56.dp.roundToPx() }
    val centerCallback by rememberUpdatedState(onCenterChange)
    var ready by remember(owner) { mutableStateOf<MapLibreMap?>(null) }
    var error by remember(owner) { mutableStateOf<String?>(null) }
    var disposed by remember(owner) { mutableStateOf(false) }
    var fittedLabels by remember { mutableStateOf<Set<String>>(emptySet()) }
    val mapView = remember(context, owner) {
        MapLibre.getInstance(context)
        MapView(context).apply {
            onCreate(null)
            addOnDidFailLoadingMapListener { if (!disposed) error = "Không tải được bản đồ. Kiểm tra kết nối mạng." }
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
        if (ready == null && error == null) error = "Bản đồ tải chậm. Hãy thử tải lại."
    }
    LaunchedEffect(ready, markers) {
        val map = ready ?: return@LaunchedEffect
        map.clear()
        val icons = IconFactory.getInstance(context)
        markers.forEach { marker ->
            map.addMarker(
                MarkerOptions().position(LatLng(marker.point.latitude, marker.point.longitude))
                    .title(marker.title)
                    .icon(icons.fromBitmap(markerBitmap(context, marker.label, marker.color)))
            )
        }
        val labels = markers.map { it.label }.toSet()
        if (onCenterChange == null && markers.isNotEmpty() && labels != fittedLabels) {
            fittedLabels = labels
            mapView.post {
                if (disposed) return@post
                if (markers.size >= 2) {
                    val bounds = LatLngBounds.Builder().includes(markers.map { LatLng(it.point.latitude, it.point.longitude) }).build()
                    map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))
                } else {
                    val p = markers.first().point
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(p.latitude, p.longitude), 15.0))
                }
            }
        }
    }
    LaunchedEffect(ready, focus) {
        val map = ready ?: return@LaunchedEffect
        val target = focus ?: return@LaunchedEffect
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(target.latitude, target.longitude), 16.0))
    }
    Box(modifier.background(DeliveryGreenSoft)) {
        AndroidView(factory = {
            mapView.apply {
                getMapAsync { map ->
                    if (disposed) return@getMapAsync
                    map.uiSettings.isCompassEnabled = false
                    map.uiSettings.isRotateGesturesEnabled = false
                    map.uiSettings.isTiltGesturesEnabled = false
                    if (!interactive) map.uiSettings.setAllGesturesEnabled(false)
                    val start = focus ?: markers.firstOrNull()?.point ?: HoChiMinhCity
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(start.latitude, start.longitude), 15.0))
                    map.addOnCameraIdleListener {
                        val target = map.cameraPosition.target ?: return@addOnCameraIdleListener
                        centerCallback?.invoke(GeoPoint(target.latitude, target.longitude))
                    }
                    map.setStyle(MAP_STYLE) { if (!disposed) { error = null; ready = map } }
                }
            }
        }, modifier = Modifier.fillMaxSize())
        if (ready == null && error == null) CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), color = DeliveryGreen)
        error?.let { message ->
            Surface(Modifier.align(Alignment.Center).padding(20.dp), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text(message, style = MaterialTheme.typography.bodyMedium, color = DeliveryText)
                    TextButton(onClick = onRetry) { Text("Tải lại bản đồ", color = DeliveryGreen) }
                }
            }
        }
        Text(
            "© OpenFreeMap · OpenStreetMap",
            Modifier.align(Alignment.BottomStart).padding(6.dp).background(Color.White.copy(alpha = .85f)).padding(horizontal = 4.dp),
            style = MaterialTheme.typography.labelSmall, color = DeliveryText
        )
    }
}

/** Round pin with a letter (A, B, TX) in the marker colour, drawn once per marker. */
private fun markerBitmap(context: Context, label: String, color: Color): Bitmap {
    val density = context.resources.displayMetrics.density
    val size = (34 * density).toInt()
    val stem = (8 * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size + stem, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb() }
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = android.graphics.Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 3 * density }
    val radius = size / 2f - 2 * density
    canvas.drawRect(size / 2f - 1.5f * density, size / 2f, size / 2f + 1.5f * density, (size + stem).toFloat(), fill)
    canvas.drawCircle(size / 2f, size / 2f, radius, fill)
    canvas.drawCircle(size / 2f, size / 2f, radius, ring)
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = android.graphics.Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        textSize = (if (label.length > 1) 11 else 15) * density
    }
    canvas.drawText(label, size / 2f, size / 2f - (text.descent() + text.ascent()) / 2, text)
    return bitmap
}
