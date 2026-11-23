package com.deliveryapp.customer.core.location

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
import com.deliveryapp.customer.data.repository.GeoPoint
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

fun hasLocationPermission(context: Context): Boolean =
    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

/** Current device position (a fix up to 30 s old, or a new one within 12 s); null without permission/signal. */
@SuppressLint("MissingPermission")
suspend fun currentLocation(context: Context): GeoPoint? = withContext(Dispatchers.Main) {
    if (!hasLocationPermission(context)) return@withContext null
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
    val cached = providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
        .filter { SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos in 0..30_000_000_000L }
        .minByOrNull(Location::getAccuracy)
    if (cached != null) return@withContext GeoPoint(cached.latitude, cached.longitude)
    if (providers.isEmpty()) return@withContext null
    withTimeoutOrNull(12_000) {
        suspendCancellableCoroutine { continuation ->
            lateinit var listener: LocationListener
            listener = LocationListener { location ->
                manager.removeUpdates(listener)
                if (continuation.isActive) continuation.resume(GeoPoint(location.latitude, location.longitude))
            }
            continuation.invokeOnCancellation { manager.removeUpdates(listener) }
            var registered = false
            for (provider in providers) {
                if (runCatching { manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper()) }.isSuccess) registered = true
            }
            if (!registered && continuation.isActive) continuation.resume(null)
        }
    }
}

/**
 * Turns a map point into a readable address with OpenStreetMap Nominatim (one request per confirmed
 * pick, as its usage policy asks). It only pre-fills an empty address field, so a failure is ignored.
 */
object ReverseGeocoder {
    private val client = OkHttpClient.Builder().callTimeout(6, TimeUnit.SECONDS).build()

    suspend fun address(point: GeoPoint): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=18&accept-language=vi" +
                    "&lat=${point.latitude}&lon=${point.longitude}")
                .header("User-Agent", "DeliveryApp-Customer/1.0 (Android)")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                format(JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject)
            }
        }.getOrNull()
    }

    internal fun format(json: JsonObject): String? {
        val address = json.getAsJsonObject("address")
        fun part(vararg keys: String) = keys.firstNotNullOfOrNull { key ->
            address?.get(key)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf(String::isNotBlank)
        }
        val street = listOfNotNull(part("house_number"), part("road", "pedestrian", "footway")).joinToString(" ")
        val parts = listOfNotNull(
            part("amenity", "shop", "building", "tourism").takeIf { street.isBlank() },
            street.ifBlank { null },
            part("quarter", "suburb", "neighbourhood"),
            part("city_district", "district", "county"),
            part("city", "town", "state")
        ).distinct()
        return parts.joinToString(", ").ifBlank { json.get("display_name")?.asString }
    }
}
