package com.deliveryapp.shipper.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.deliveryapp.shipper.data.model.Point
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class DeviceFix(val point: Point, val accuracyM: Float, val recordedAtMs: Long)
fun interface LocationReader {
    /** A fix at most 60 s old: enough for the backend's matching freshness window. */
    suspend fun read(): DeviceFix?
    /** A fix at most 10 s old, for checks against a precise spot (pickup must be within 200 m). */
    suspend fun readFresh(): DeviceFix? = read()
}
class DeviceLocationReader(context: Context) : LocationReader {
    private val context = context.applicationContext
    override suspend fun read(): DeviceFix? = readWithin(60_000_000_000L)
    override suspend fun readFresh(): DeviceFix? = readWithin(10_000_000_000L)

    @SuppressLint("MissingPermission")
    private suspend fun readWithin(maxAgeNanos: Long): DeviceFix? = withContext(Dispatchers.Main) {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return@withContext null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = (if (fine) listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER) else listOf(LocationManager.NETWORK_PROVIDER))
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        val cached = providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .filter { (SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos) in 0..maxAgeNanos }
            .minByOrNull(Location::getAccuracy)
        if (cached != null) return@withContext cached.fix()
        if (providers.isEmpty()) return@withContext null
        withTimeoutOrNull(12_000) {
            suspendCancellableCoroutine { continuation ->
                // A single listener for all eligible providers; always removed on completion/cancellation.
                lateinit var callback: LocationListener
                callback = LocationListener { location ->
                    manager.removeUpdates(callback)
                    if (continuation.isActive) continuation.resume(location.fix())
                }
                continuation.invokeOnCancellation { manager.removeUpdates(callback) }
                var registered = false
                for (provider in providers) {
                    if (runCatching { manager.requestLocationUpdates(provider, 0L, 0f, callback, Looper.getMainLooper()) }.isSuccess) registered = true
                }
                if (!registered && continuation.isActive) continuation.resume(null)
            }
        }
    }
    private fun Location.fix() = DeviceFix(Point(latitude, longitude), accuracy, time)
}
