package com.example.helpnearby.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.roundToInt

data class DetectedLocation(
    val latitude: Double,
    val longitude: Double,
    val city: String? = null,
    val area: String? = null,
    val stateName: String? = null
)

@Singleton
class LocationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun hasCoarseLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Fetches an approximate (~1 km rounded) device location using FusedLocationProvider
     * (`getCurrentLocation` then `lastLocation`), falling back to platform [LocationManager],
     * and reverse-geocodes city/area/state via [Geocoder].
     */
    @SuppressLint("MissingPermission")
    suspend fun fetchApproximateLocation(): Result<DetectedLocation> {
        if (!hasCoarseLocationPermission()) {
            return Result.failure(IllegalStateException("Location permission not granted."))
        }

        val rawLocation = resolveRawLocation()
            ?: return Result.failure(IllegalStateException("Unable to read device location. Enable GPS or select your city manually."))

        // Round to ~1.1 km grid (0.01 deg) to protect exact address privacy
        val approxLat = (rawLocation.latitude * 100.0).roundToInt() / 100.0
        val approxLon = (rawLocation.longitude * 100.0).roundToInt() / 100.0

        val address = reverseGeocode(approxLat, approxLon)
        val city = address?.locality
            ?: address?.subAdminArea
            ?: address?.adminArea
        val area = address?.subLocality
            ?: address?.thoroughfare
            ?: address?.featureName?.takeIf { !it.all { ch -> ch.isDigit() } }
            ?: city
        val stateName = address?.adminArea

        return Result.success(
            DetectedLocation(
                latitude = approxLat,
                longitude = approxLon,
                city = city?.takeIf { it.isNotBlank() },
                area = area?.takeIf { it.isNotBlank() },
                stateName = stateName?.takeIf { it.isNotBlank() }
            )
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun resolveRawLocation(): Location? {
        val fusedClient = runCatching { LocationServices.getFusedLocationProviderClient(context) }.getOrNull()
        if (fusedClient != null) {
            val current = withTimeoutOrNull(4_500L) {
                val cts = CancellationTokenSource()
                runCatching {
                    fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                        .awaitNullable()
                }.getOrNull()
            }
            if (current != null) return current

            val last = withTimeoutOrNull(2_000L) {
                runCatching { fusedClient.lastLocation.awaitNullable() }.getOrNull()
            }
            if (last != null) return last
        }

        val lm = runCatching {
            context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        }.getOrNull() ?: return null

        val providers = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        return providers.mapNotNull { provider ->
            runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }
    }

    private suspend fun reverseGeocode(lat: Double, lon: Double): Address? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        val geocoder = Geocoder(context, Locale.getDefault())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            withTimeoutOrNull(3_000L) {
                suspendCancellableCoroutine { cont ->
                    runCatching {
                        geocoder.getFromLocation(
                            lat,
                            lon,
                            1,
                            object : Geocoder.GeocodeListener {
                                override fun onGeocode(addresses: MutableList<Address>) {
                                    if (cont.isActive) cont.resume(addresses.firstOrNull())
                                }

                                override fun onError(errorMessage: String?) {
                                    if (cont.isActive) cont.resume(null)
                                }
                            }
                        )
                    }.onFailure {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
        } else {
            @Suppress("DEPRECATION")
            runCatching { geocoder.getFromLocation(lat, lon, 1)?.firstOrNull() }.getOrNull()
        }
    }

    private suspend fun <T> Task<T>.awaitNullable(): T? = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result ->
            if (cont.isActive) cont.resume(result)
        }
        addOnFailureListener {
            if (cont.isActive) cont.resume(null)
        }
        addOnCanceledListener {
            if (cont.isActive) cont.resume(null)
        }
    }
}
