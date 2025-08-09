package com.starception.dua.prayer.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.starception.dua.prayer.model.Location
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Service for handling device location and geocoding
 */
@Singleton
class LocationService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val locationManager: LocationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }
    
    private val geocoder: Geocoder? by lazy {
        if (Geocoder.isPresent()) {
            Geocoder(context, Locale.getDefault())
        } else {
            null
        }
    }
    
    /**
     * Checks if location permissions are granted
     */
    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Checks if GPS is enabled
     */
    fun isGpsEnabled(): Boolean {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
               locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
    
    /**
     * Gets current device location
     */
    suspend fun getCurrentLocation(): Result<Location> = withTimeoutOrNull(30000) {
        suspendCancellableCoroutine { continuation ->
            if (!hasLocationPermission()) {
                continuation.resume(Result.failure(SecurityException("Location permission not granted")))
                return@suspendCancellableCoroutine
            }
            
            if (!isGpsEnabled()) {
                continuation.resume(Result.failure(Exception("GPS is disabled")))
                return@suspendCancellableCoroutine
            }
            
            val providers = mutableListOf<String>()
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                providers.add(LocationManager.GPS_PROVIDER)
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                providers.add(LocationManager.NETWORK_PROVIDER)
            }
            
            if (providers.isEmpty()) {
                continuation.resume(Result.failure(Exception("No location providers available")))
                return@suspendCancellableCoroutine
            }
            
            var bestLocation: android.location.Location? = null
            var providersToCheck = providers.size
            
            val locationListener = object : LocationListener {
                override fun onLocationChanged(location: android.location.Location) {
                    if (bestLocation == null || location.accuracy < bestLocation!!.accuracy) {
                        bestLocation = location
                    }
                    
                    providersToCheck--
                    if (providersToCheck <= 0) {
                        bestLocation?.let { loc ->
                            val timeZoneOffset = getTimeZoneOffset()
                            val result = Location(
                                latitude = loc.latitude,
                                longitude = loc.longitude,
                                timeZoneOffset = timeZoneOffset,
                                altitude = loc.altitude
                            )
                            continuation.resume(Result.success(result))
                        } ?: continuation.resume(Result.failure(Exception("No location found")))
                    }
                }
                
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            }
            
            try {
                // Try to get last known location first
                val lastKnownLocation = providers.mapNotNull { provider ->
                    locationManager.getLastKnownLocation(provider)
                }.minByOrNull { it.accuracy }
                
                if (lastKnownLocation != null && System.currentTimeMillis() - lastKnownLocation.time < 60000) {
                    // Use last known location if it's less than 1 minute old
                    val timeZoneOffset = getTimeZoneOffset()
                    val result = Location(
                        latitude = lastKnownLocation.latitude,
                        longitude = lastKnownLocation.longitude,
                        timeZoneOffset = timeZoneOffset,
                        altitude = lastKnownLocation.altitude
                    )
                    continuation.resume(Result.success(result))
                } else {
                    // Request fresh location updates
                    providers.forEach { provider ->
                        locationManager.requestLocationUpdates(
                            provider,
                            0L,
                            0f,
                            locationListener
                        )
                    }
                    
                    continuation.invokeOnCancellation {
                        locationManager.removeUpdates(locationListener)
                    }
                }
            } catch (e: SecurityException) {
                continuation.resume(Result.failure(e))
            }
        }
    } ?: Result.failure(Exception("Location request timed out"))
    
    /**
     * Gets location details (city, country) from coordinates
     */
    suspend fun getLocationDetails(location: Location): Location = suspendCancellableCoroutine { continuation ->
        geocoder?.let { geocoder ->
            try {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                
                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    val updatedLocation = location.copy(
                        city = address.locality ?: address.subAdminArea ?: "",
                        country = address.countryName ?: ""
                    )
                    continuation.resume(updatedLocation)
                } else {
                    continuation.resume(location)
                }
            } catch (e: Exception) {
                // Return original location if geocoding fails
                continuation.resume(location)
            }
        } ?: run {
            continuation.resume(location)
        }
    }
    
    /**
     * Searches for locations by name
     */
    suspend fun searchLocation(query: String): Result<List<Location>> = suspendCancellableCoroutine { continuation ->
        geocoder?.let { geocoder ->
            try {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocationName(query, 5)
                
                if (!addresses.isNullOrEmpty()) {
                    val locations = addresses.map { address ->
                        Location(
                            latitude = address.latitude,
                            longitude = address.longitude,
                            timeZoneOffset = getTimeZoneOffset(),
                            city = address.locality ?: address.subAdminArea ?: "",
                            country = address.countryName ?: "",
                            altitude = 0.0
                        )
                    }
                    continuation.resume(Result.success(locations))
                } else {
                    continuation.resume(Result.success(emptyList()))
                }
            } catch (e: Exception) {
                continuation.resume(Result.failure(e))
            }
        } ?: run {
            continuation.resume(Result.failure(Exception("Geocoder not available")))
        }
    }
    
    /**
     * Gets current timezone offset in hours
     */
    private fun getTimeZoneOffset(): Double {
        val timeZone = TimeZone.getDefault()
        val offset = timeZone.rawOffset + timeZone.dstSavings
        return offset / (1000.0 * 60.0 * 60.0) // Convert milliseconds to hours
    }
    
    /**
     * Validates if coordinates are within valid range
     */
    fun isValidCoordinates(latitude: Double, longitude: Double): Boolean {
        return latitude in -90.0..90.0 && longitude in -180.0..180.0
    }
    
    /**
     * Creates location from manual input
     */
    fun createManualLocation(
        latitude: Double,
        longitude: Double,
        city: String = "",
        country: String = ""
    ): Location? {
        return if (isValidCoordinates(latitude, longitude)) {
            Location(
                latitude = latitude,
                longitude = longitude,
                timeZoneOffset = getTimeZoneOffset(),
                city = city,
                country = country
            )
        } else {
            null
        }
    }
}