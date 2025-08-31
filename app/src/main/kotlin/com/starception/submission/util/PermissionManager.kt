package com.starception.submission.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * PERMISSION MANAGER: Centralized permission handling for prayer times app
 * 
 * This utility class manages all app permissions required for optimal prayer time functionality.
 * 
 * PERMISSIONS MANAGED:
 * - Location permissions (fine and coarse) for accurate prayer time calculations
 * - Notification permissions (Android 13+) for prayer alerts
 * - Location services checking for GPS/Network availability
 * 
 * FEATURES:
 * - Graceful permission requests with user-friendly flow
 * - Android version compatibility (handles API differences)
 * - Settings redirect for users who denied permissions
 * - Service availability checking
 * 
 * USAGE:
 * - Check permissions before using location or notifications
 * - Request permissions with proper context
 * - Handle permission results appropriately
 * 
 * EDIT THIS TO:
 * - Add new permission types
 * - Modify permission request strategies
 * - Change permission rationale messages
 */
class PermissionManager(private val activity: FragmentActivity) {
    
    companion object {
        // PERMISSION REQUEST CODES - Used to identify permission results
        const val LOCATION_PERMISSION_REQUEST_CODE = 1001     // For location permissions
        const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1002 // For notification permissions
    }
    
    /**
     * LOCATION PERMISSION CHECKER: Verifies if location permissions are granted
     * 
     * Checks both fine (GPS) and coarse (network) location permissions.
     * Both are required for optimal prayer time accuracy.
     * 
     * PERMISSION TYPES:
     * - ACCESS_FINE_LOCATION: GPS-based location (most accurate)
     * - ACCESS_COARSE_LOCATION: Network-based location (fallback)
     * 
     * EDIT THIS TO:
     * - Require only one permission type
     * - Add background location permission check
     * - Include permission rationale handling
     */
    fun isLocationPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.ACCESS_FINE_LOCATION    // GPS location permission
        ) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.ACCESS_COARSE_LOCATION  // Network location permission
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * NOTIFICATION PERMISSION CHECKER: Verifies if notification permissions are granted
     * 
     * Android 13+ requires explicit notification permission, while older versions
     * have notifications enabled by default.
     * 
     * VERSION HANDLING:
     * - Android 13+: Checks POST_NOTIFICATIONS permission
     * - Android 12 and below: Always returns true (no permission required)
     * 
     * EDIT THIS TO:
     * - Add notification importance level checking
     * - Include Do Not Disturb status checking
     * - Add specific notification channel permission checks
     */
    fun isNotificationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: Explicit notification permission required
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Pre-Android 13: Notifications enabled by default, no permission needed
            true
        }
    }
    
    /**
     * LOCATION SERVICES CHECKER: Verifies if device location services are active
     * 
     * Even with permissions granted, location services must be enabled in device settings.
     * This checks if either GPS or network location is available.
     * 
     * PROVIDERS CHECKED:
     * - GPS_PROVIDER: Satellite-based location (most accurate)
     * - NETWORK_PROVIDER: Wi-Fi/cellular-based location (faster)
     * 
     * Returns true if at least one provider is enabled.
     * 
     * EDIT THIS TO:
     * - Require specific provider types
     * - Add provider accuracy checking
     * - Include provider availability status
     */
    fun isLocationServicesEnabled(): Boolean {
        val locationManager = activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||      // GPS available
               locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)     // Network location available
    }
    
    /**
     * LOCATION PERMISSION REQUESTER: Requests location permissions from user
     * 
     * Shows system permission dialog for location access.
     * Requests both fine and coarse location for best prayer time accuracy.
     * 
     * PERMISSION FLOW:
     * 1. Check if permissions already granted
     * 2. Show system permission dialog
     * 3. Handle user response in onRequestPermissionsResult
     * 
     * EDIT THIS TO:
     * - Add permission rationale dialog before requesting
     * - Request permissions individually
     * - Add custom permission explanation UI
     */
    fun requestLocationPermission() {
        if (!isLocationPermissionGranted()) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,    // GPS location
                    Manifest.permission.ACCESS_COARSE_LOCATION   // Network location
                ),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }
    
    /**
     * Request notification permission (Android 13+)
     */
    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isNotificationPermissionGranted()) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST_CODE
            )
        }
    }
    
    /**
     * Check and request all necessary permissions
     */
    fun checkAndRequestPermissions() {
        // Request location permission if not granted
        if (!isLocationPermissionGranted()) {
            requestLocationPermission()
        }
        
        // Note: Notification permission is NOT requested automatically on startup
        // It will be requested when the prayer times page is opened
    }
    
    /**
     * Request notification permission when prayer times page is opened
     * This is called when user navigates to prayer times
     */
    fun requestNotificationPermissionOnPrayerTimesOpen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isNotificationPermissionGranted()) {
            requestNotificationPermission()
        }
    }
    
    /**
     * Request notification permission during pull-to-refresh
     * This is called when user actively interacts with the app
     */
    fun requestNotificationPermissionOnRefresh() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isNotificationPermissionGranted()) {
            requestNotificationPermission()
        }
    }
    
    /**
     * Check and request only location permissions (for app startup)
     */
    fun checkAndRequestLocationPermissions() {
        // Request location permission if not granted
        if (!isLocationPermissionGranted()) {
            requestLocationPermission()
        }
        
        // Check if location services are enabled
        checkLocationServices()
    }
    
    /**
     * Check if location services are enabled and show settings dialog if needed
     */
    fun checkLocationServices() {
        if (!isLocationServicesEnabled()) {
            // Open location settings directly
            openLocationSettings()
        }
    }
    
    /**
     * Open location settings
     */
    private fun openLocationSettings() {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        activity.startActivity(intent)
    }
}
