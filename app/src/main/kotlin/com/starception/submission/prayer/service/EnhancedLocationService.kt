package com.starception.submission.prayer.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
import com.starception.submission.prayer.model.Location
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * ENHANCED LOCATION SERVICE: Advanced GPS/Network location provider for prayer times
 * 
 * This service provides reliable, fast location acquisition optimized for prayer time
 * calculations. It includes smart timeout handling and multi-strategy location fetching.
 * 
 * KEY FEATURES:
 * - Multiple location strategies (GPS, Network, Cached)
 * - Smart timeout handling (prevents app hanging in elevators)
 * - Fallback systems for reliable location acquisition
 * - Google Play Services integration for high accuracy
 * - Permission and service availability checking
 * - Geocoding support for city/country names
 * - Timezone estimation for accurate prayer times
 * 
 * LOCATION STRATEGIES:
 * 1. getBestAvailableLocation() - Smart multi-level fallback system
 * 2. getLocationQuick() - Optimized for instant UI updates
 * 3. getCurrentLocation() - Standard GPS location with timeout
 * 4. getCurrentLocationHighAccuracy() - High precision GPS
 * 5. getLastKnownLocation() - Fastest cached location
 * 
 * TIMEOUT HANDLING:
 * - All location requests have 3-second timeouts
 * - Prevents app freezing in poor signal areas
 * - Graceful fallbacks when GPS is unavailable
 * 
 * PERMISSION REQUIREMENTS:
 * - ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION
 * - Location services must be enabled on device
 * 
 * EDIT THIS SERVICE TO:
 * - Change timeout durations (currently 3 seconds)
 * - Modify fallback behavior
 * - Add new location providers
 * - Customize timezone calculations
 * - Add location caching strategies
 */
@Singleton
class EnhancedLocationService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }
    
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
     * PERMISSION CHECKER: Validates location permissions for the app
     * 
     * This checks whether the app has been granted location permissions by the user.
     * Required before any location operations can be performed.
     * 
     * PERMISSIONS CHECKED:
     * - ACCESS_FINE_LOCATION (GPS + Network)
     * - ACCESS_COARSE_LOCATION (Network only)
     * 
     * @return true if either fine or coarse location permission is granted
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
     * LOCATION SERVICES CHECKER: Validates if device location services are enabled
     * 
     * This checks whether location services are turned on at the device level.
     * Even with permissions, location won't work if services are disabled.
     * 
     * SERVICES CHECKED:
     * - GPS Provider (satellite-based location)
     * - Network Provider (cell towers + WiFi)
     * 
     * @return true if at least one location provider is enabled
     */
    fun isLocationEnabled(): Boolean {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
    
    /**
     * HIGH ACCURACY LOCATION: Gets precise GPS location with power-intensive method
     * 
     * This method prioritizes accuracy over battery life, using GPS satellites
     * for the most precise location possible. Has fallback to balanced accuracy.
     * 
     * ACCURACY PRIORITY:
     * 1. PRIORITY_HIGH_ACCURACY (GPS satellites)
     * 2. Fallback to PRIORITY_BALANCED_POWER_ACCURACY
     * 
     * USE WHEN:
     * - Initial setup or location verification
     * - User explicitly requests high accuracy
     * - Battery life is not a concern
     * 
     * TIMEOUT: 3 seconds to prevent UI blocking
     * 
     * @return Result containing high-accuracy Location or error
     */
    suspend fun getCurrentLocationHighAccuracy(): Result<android.location.Location> {
        if (!hasLocationPermission()) {
            return Result.failure(SecurityException("Location permission not granted"))
        }
        
        if (!isLocationEnabled()) {
            return Result.failure(IllegalStateException("Location services are disabled"))
        }
        
        return try {
            val location = getCurrentLocationInternal(Priority.PRIORITY_HIGH_ACCURACY)
            if (location != null) {
                Result.success(location)
            } else {
                // Fallback to balanced power accuracy
                val fallbackLocation = getCurrentLocationInternal(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                if (fallbackLocation != null) {
                    Result.success(fallbackLocation)
                } else {
                    Result.failure(RuntimeException("Unable to determine location"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * BALANCED ACCURACY LOCATION: Gets location optimized for speed and battery
     * 
     * This method balances accuracy with power consumption, typically using
     * network-based location (cell towers + WiFi) with GPS as secondary.
     * 
     * CHARACTERISTICS:
     * - Faster than high accuracy mode
     * - Lower battery consumption
     * - Good accuracy for prayer time calculations
     * - Uses PRIORITY_BALANCED_POWER_ACCURACY
     * 
     * RECOMMENDED FOR:
     * - Regular prayer time calculations
     * - Background location updates
     * - Battery-conscious operations
     * 
     * TIMEOUT: 3 seconds to prevent UI blocking
     * 
     * @return Result containing balanced-accuracy Location or error
     */
    suspend fun getCurrentLocation(): Result<android.location.Location> {
        if (!hasLocationPermission()) {
            return Result.failure(SecurityException("Location permission not granted"))
        }
        
        if (!isLocationEnabled()) {
            return Result.failure(IllegalStateException("Location services are disabled"))
        }
        
        return try {
            val location = getCurrentLocationInternal(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            if (location != null) {
                Result.success(location)
            } else {
                Result.failure(RuntimeException("Unable to determine location"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * LOCATION REQUEST WITH TIMEOUT: Core location fetching with elevator-friendly timeout
     * 
     * This is the key improvement that prevents the app from hanging in elevators!
     * 
     * TIMEOUT BEHAVIOR:
     * - 3 seconds maximum wait (instead of previous 15 seconds)
     * - Prevents app freezing in elevators, underground areas, poor signal
     * - Returns null after timeout, triggering fallback to cached location
     * 
     * EDIT THIS TO:
     * - Change timeout duration (currently 3000ms = 3 seconds)
     * - Modify fallback behavior
     * - Add different timeouts for different priorities
     */
    private suspend fun getCurrentLocationInternal(priority: Int): android.location.Location? {
        return withTimeoutOrNull(3000) { // 3 second timeout - EDIT THIS VALUE to change timeout duration
            suspendCancellableCoroutine { continuation ->
                val cancellationTokenSource = CancellationTokenSource()
                
                continuation.invokeOnCancellation {
                    cancellationTokenSource.cancel()
                }
                
                try {
                    @Suppress("MissingPermission") // We check permissions above
                    fusedLocationClient.getCurrentLocation(
                        priority,
                        cancellationTokenSource.token
                    ).addOnCompleteListener { task ->
                        when {
                            task.isSuccessful && task.result != null -> {
                                continuation.resume(task.result)
                            }
                            task.exception != null -> {
                                continuation.resumeWithException(
                                    task.exception ?: RuntimeException("Location task failed")
                                )
                            }
                            else -> {
                                continuation.resume(null)
                            }
                        }
                    }
                } catch (e: SecurityException) {
                    continuation.resumeWithException(e)
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            }
        }
    }
    
    /**
     * CACHED LOCATION RETRIEVAL: Gets previously stored location (instant)
     * 
     * This returns the last known location stored by the system, providing
     * instant results without any GPS or network activity.
     * 
     * CHARACTERISTICS:
     * - Instant response (no network/GPS delay)
     * - No battery usage
     * - May be stale (minutes to hours old)
     * - Perfect for app startup scenarios
     * 
     * USE CASES:
     * - App startup before GPS is available
     * - Fallback when current location fails
     * - When you need immediate location estimate
     * 
     * LIMITATIONS:
     * - Location may be outdated
     * - May not exist if location was never used
     * - Accuracy depends on when it was cached
     * 
     * @return Result containing cached Location or error if none available
     */
    suspend fun getLastKnownLocation(): Result<android.location.Location> {
        if (!hasLocationPermission()) {
            return Result.failure(SecurityException("Location permission not granted"))
        }
        
        return try {
            suspendCancellableCoroutine { continuation ->
                try {
                    @Suppress("MissingPermission")
                    fusedLocationClient.lastLocation.addOnCompleteListener { task ->
                        when {
                            task.isSuccessful && task.result != null -> {
                                continuation.resume(Result.success(task.result))
                            }
                            task.exception != null -> {
                                continuation.resume(
                                    Result.failure(
                                        task.exception ?: RuntimeException("Last location task failed")
                                    )
                                )
                            }
                            else -> {
                                continuation.resume(
                                    Result.failure(RuntimeException("No last known location available"))
                                )
                            }
                        }
                    }
                } catch (e: SecurityException) {
                    continuation.resume(Result.failure(e))
                } catch (e: Exception) {
                    continuation.resume(Result.failure(e))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * LOCATION ENRICHMENT: Adds city/country names to GPS coordinates
     * 
     * This function takes raw GPS coordinates and enriches them with human-readable
     * location information using reverse geocoding.
     * 
     * ENRICHMENT PROCESS:
     * 1. Takes raw android.location.Location (lat/lon only)
     * 2. Calculates timezone offset for the location
     * 3. Uses Geocoder to find city and country names
     * 4. Returns enhanced Location object with all details
     * 
     * FALLBACK BEHAVIOR:
     * - If geocoding fails, returns location with coordinates only
     * - 3-second timeout prevents delays
     * - Always returns a valid Location object
     * 
     * ENHANCED DATA:
     * - City name (e.g., "Dubai", "New York")
     * - Country name (e.g., "UAE", "United States")
     * - Timezone offset (e.g., +4.0 for UAE)
     * - Altitude if available
     * 
     * @param androidLocation Raw GPS location from system
     * @return Enhanced Location with city, country, and timezone
     */
    suspend fun getLocationDetails(androidLocation: android.location.Location): Location {
        val baseLocation = Location(
            latitude = androidLocation.latitude,
            longitude = androidLocation.longitude,
            timeZoneOffset = getTimezoneOffset(androidLocation.latitude, androidLocation.longitude),
            altitude = androidLocation.altitude
        )
        
        return try {
            geocoder?.let { gc ->
                withTimeoutOrNull(3000) { // 3 second timeout for geocoding to prevent delays
                    val addresses = gc.getFromLocation(
                        androidLocation.latitude,
                        androidLocation.longitude,
                        1
                    )
                    
                    addresses?.firstOrNull()?.let { address ->
                        baseLocation.copy(
                            city = address.locality ?: address.subAdminArea ?: "",
                            country = address.countryName ?: "",
                            countryCode = address.countryCode ?: ""
                        )
                    }
                }
            } ?: baseLocation
        } catch (e: Exception) {
            // If geocoding fails, return location with coordinates only
            baseLocation
        }
    }
    
    /**
     * LOCATION SEARCH: Find coordinates by searching city/address names
     * 
     * This function performs forward geocoding - converting place names or addresses
     * into GPS coordinates and location details.
     * 
     * SEARCH CAPABILITIES:
     * - City names (e.g., "Dubai", "New York")
     * - Addresses (e.g., "123 Main St, Dubai")
     * - Landmarks (e.g., "Burj Khalifa")
     * - Countries (e.g., "United Arab Emirates")
     * 
     * SEARCH BEHAVIOR:
     * - Returns up to 5 matching locations
     * - Sorted by relevance
     * - 5-second timeout for network requests
     * - Includes timezone calculation for each result
     * 
     * USE CASES:
     * - User manual location selection
     * - Location picker in settings
     * - Address validation
     * 
     * @param query Search term (city, address, landmark, etc.)
     * @return Result containing list of matching Locations or error
     */
    suspend fun searchLocation(query: String): Result<List<Location>> {
        return try {
            geocoder?.let { gc ->
                withTimeoutOrNull(5000) { // 5 seconds for search is reasonable
                    val addresses = gc.getFromLocationName(query, 5)
                    val locations = addresses?.map { address ->
                        Location(
                            latitude = address.latitude,
                            longitude = address.longitude,
                            timeZoneOffset = getTimezoneOffset(address.latitude, address.longitude),
                            city = address.locality ?: address.subAdminArea ?: "",
                            country = address.countryName ?: "",
                            countryCode = address.countryCode ?: ""
                        )
                    } ?: emptyList()
                    
                    Result.success(locations)
                } ?: Result.failure(RuntimeException("Geocoding timeout"))
            } ?: Result.failure(RuntimeException("Geocoder not available"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * TIMEZONE CALCULATOR: Determines UTC offset for prayer time calculations
     * 
     * This function calculates the timezone offset needed for accurate prayer times.
     * It uses a two-tier approach: known regions first, then longitude estimation.
     * 
     * CALCULATION METHOD:
     * 1. Check if coordinates match known regions (high accuracy)
     * 2. Fall back to longitude-based estimation (approximate)
     * 
     * KNOWN REGIONS (exact timezones):
     * - Middle East: UAE (+4), Saudi Arabia (+3), Qatar (+3), etc.
     * - South Asia: Pakistan (+5), India (+5.5), Bangladesh (+6)
     * - Southeast Asia: Malaysia/Singapore (+8)
     * - And more...
     * 
     * LONGITUDE FALLBACK:
     * - Divides longitude by 15 degrees per hour
     * - Rounds to nearest half-hour
     * - Clamps to valid range (-12 to +12)
     * 
     * ACCURACY:
     * - Known regions: Exact (accounts for political boundaries)
     * - Longitude estimate: ±30 minutes typically
     * 
     * @param latitude GPS latitude coordinate
     * @param longitude GPS longitude coordinate
     * @return UTC offset in hours (e.g., 4.0 for GMT+4, -5.0 for GMT-5)
     */
    private fun getTimezoneOffset(latitude: Double, longitude: Double): Double {
        // Known timezone corrections for major cities/regions
        when {
            // UAE (Dubai, Abu Dhabi, Sharjah, etc.) - GMT+4
            latitude in 24.0..26.5 && longitude in 54.0..56.5 -> return 4.0
            
            // Saudi Arabia (Riyadh, Jeddah, Mecca, Medina) - GMT+3
            latitude in 20.0..32.0 && longitude in 36.0..51.0 -> return 3.0
            
            // Kuwait - GMT+3
            latitude in 28.5..30.5 && longitude in 46.5..48.5 -> return 3.0
            
            // Qatar - GMT+3
            latitude in 24.0..26.5 && longitude in 50.0..52.0 -> return 3.0
            
            // Bahrain - GMT+3
            latitude in 25.5..26.5 && longitude in 50.0..51.0 -> return 3.0
            
            // Oman - GMT+4
            latitude in 16.0..26.5 && longitude in 51.5..60.0 -> return 4.0
            
            // Egypt (Cairo, Alexandria) - GMT+2
            latitude in 22.0..32.0 && longitude in 24.0..37.0 -> return 2.0
            
            // Pakistan - GMT+5
            latitude in 23.0..37.0 && longitude in 60.0..78.0 -> return 5.0
            
            // India - GMT+5.5
            latitude in 6.0..38.0 && longitude in 68.0..98.0 -> return 5.5
            
            // Bangladesh - GMT+6
            latitude in 20.0..27.0 && longitude in 88.0..93.0 -> return 6.0
            
            // Malaysia/Singapore - GMT+8
            latitude in 1.0..7.5 && longitude in 99.5..120.0 -> return 8.0
            
            // Turkey - GMT+3
            latitude in 35.0..43.0 && longitude in 25.0..45.0 -> return 3.0
        }
        
        // Fallback to longitude-based approximation
        val estimatedOffset = longitude / 15.0
        return (kotlin.math.round(estimatedOffset * 2.0) / 2.0).coerceIn(-12.0, 12.0)
    }
    
    /**
     * SMART LOCATION STRATEGY: Multi-level fallback system for reliable location
     * 
     * This implements a smart strategy to get location quickly and reliably:
     * 
     * STRATEGY PRIORITY:
     * 1. Recent cached location (within 10 minutes) - INSTANT
     * 2. Current GPS location (3-second timeout) - FAST
     * 3. Any available cached location - RELIABLE FALLBACK
     * 
     * BENEFITS:
     * - No more waiting in elevators
     * - Uses recent cached data when GPS is slow
     * - Always provides a location (fallback to any cached)
     * 
     * EDIT THIS TO:
     * - Change cache validity period (currently 10 minutes)
     * - Modify strategy order
     * - Add new location sources
     */
    suspend fun getBestAvailableLocation(): Result<android.location.Location> {
        if (!hasLocationPermission()) {
            return Result.failure(SecurityException("Location permission not granted"))
        }
        
        // STRATEGY 1: Try recent cached location first (fastest - no network/GPS delay)
        getLastKnownLocation().fold(
            onSuccess = { lastKnown ->
                // Check if cached location is recent enough to use immediately
                val ageMs = System.currentTimeMillis() - lastKnown.time
                if (ageMs <= 10 * 60 * 1000) { // 10 minutes - EDIT THIS to change cache validity
                    return Result.success(lastKnown)
                }
            },
            onFailure = { /* Continue to next strategy */ }
        )
        
        // STRATEGY 2: Try current GPS location with 3-second timeout
        getCurrentLocation().fold(
            onSuccess = { return Result.success(it) },
            onFailure = { /* Continue to final fallback if GPS fails/times out */ }
        )
        
        // STRATEGY 3: Final fallback - use any available cached location (even if old)
        // This ensures we always have a location, even if it's not perfectly current
        return getLastKnownLocation()
    }
    
    /**
     * QUICK LOCATION FOR UI: Super-fast location for responsive UI updates
     * 
     * This is optimized for immediate UI updates without blocking the user interface.
     * 
     * BEHAVIOR:
     * - Always tries cached location first (instant response)
     * - Falls back to 3-second GPS if no cache available
     * - Perfect for prayer time calculations during app startup
     * 
     * USE THIS METHOD WHEN:
     * - User is waiting for prayer times to load
     * - App needs location for immediate display
     * - You want to avoid any UI blocking
     * 
     * EDIT THIS TO:
     * - Change fallback behavior
     * - Add different timeout for UI updates
     */
    suspend fun getLocationQuick(): Result<android.location.Location> {
        if (!hasLocationPermission()) {
            return Result.failure(SecurityException("Location permission not granted"))
        }
        
        // PRIORITY 1: Try cached location first (instant - no waiting)
        getLastKnownLocation().fold(
            onSuccess = { return Result.success(it) },
            onFailure = { /* No cached location available, try GPS */ }
        )
        
        // PRIORITY 2: Try current GPS location with 3-second timeout
        return getCurrentLocation()
    }
}