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
 * - Activity recognition permissions for activity tracking with beep notifications
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
        const val ACTIVITY_RECOGNITION_REQUEST_CODE = 1003    // For activity recognition permissions
        const val BACKGROUND_LOCATION_REQUEST_CODE = 1004     // For background location permission
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
     * ACTIVITY RECOGNITION PERMISSION CHECKER: Verifies if activity recognition permission is granted
     *
     * Android 10+ requires explicit activity recognition permission to detect user activity changes.
     * This permission is needed for beep notifications when activity changes.
     *
     * VERSION HANDLING:
     * - Android 10+: Checks ACTIVITY_RECOGNITION permission
     * - Android 9 and below: Always returns true (no permission required)
     *
     * EDIT THIS TO:
     * - Add activity confidence level checking
     * - Include specific activity type permission handling
     */
    fun isActivityRecognitionPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+: Explicit activity recognition permission required
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Pre-Android 10: No permission needed for activity recognition
            true
        }
    }

    /**
     * BACKGROUND LOCATION PERMISSION CHECKER: Verifies if background location permission is granted
     *
     * Android 10+ requires explicit background location permission to receive GPS updates
     * when the app is in the background. This is CRITICAL for driving detection when app is closed.
     *
     * VERSION HANDLING:
     * - Android 10+: Checks ACCESS_BACKGROUND_LOCATION permission
     * - Android 9 and below: Always returns true (no permission required)
     *
     * IMPORTANT: This permission must be requested SEPARATELY after foreground location is granted.
     */
    fun isBackgroundLocationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Pre-Android 10: Background location included with foreground permission
            isLocationPermissionGranted()
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
     * ACTIVITY RECOGNITION PERMISSION REQUESTER: Requests activity recognition permission from user
     *
     * Shows system permission dialog for activity recognition access.
     * This enables the app to detect when user's activity changes (walking, driving, etc.)
     * and provide beep notifications.
     *
     * PERMISSION FLOW:
     * 1. Check if permission already granted
     * 2. Show system permission dialog
     * 3. Handle user response in onRequestPermissionsResult
     *
     * EDIT THIS TO:
     * - Add permission rationale dialog before requesting
     * - Add custom permission explanation UI
     */
    fun requestActivityRecognitionPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !isActivityRecognitionPermissionGranted()) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                ACTIVITY_RECOGNITION_REQUEST_CODE
            )
        }
    }

    /**
     * BACKGROUND LOCATION PERMISSION REQUESTER: Requests background location permission
     *
     * IMPORTANT: This must be called AFTER foreground location permission is granted.
     * Android requires a separate request for background location.
     *
     * This enables the app to receive GPS updates when in background, which is
     * CRITICAL for driving detection when the app is closed.
     *
     * On Android 11+, this opens the app settings page where user must select
     * "Allow all the time" for location access.
     */
    fun requestBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !isBackgroundLocationPermissionGranted()) {
            if (isLocationPermissionGranted()) {
                // Foreground location granted, now request background
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Android 11+: Must request separately and may need to open settings
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                        BACKGROUND_LOCATION_REQUEST_CODE
                    )
                } else {
                    // Android 10: Can request with foreground permissions
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                        BACKGROUND_LOCATION_REQUEST_CODE
                    )
                }
            } else {
                // Request foreground location first
                requestLocationPermission()
            }
        }
    }

    /**
     * Open app settings for manual permission grant
     * Useful when background location needs "Allow all the time" setting
     */
    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", activity.packageName, null)
        }
        activity.startActivity(intent)
    }
    
    /**
     * Check and request all necessary permissions
     */
    fun checkAndRequestPermissions() {
        // Request location permission if not granted
        if (!isLocationPermissionGranted()) {
            requestLocationPermission()
        } else if (!isBackgroundLocationPermissionGranted()) {
            // Foreground location granted, request background location for driving detection
            requestBackgroundLocationPermission()
        }

        // Request activity recognition permission if not granted
        if (!isActivityRecognitionPermissionGranted()) {
            requestActivityRecognitionPermission()
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
        
        // Also request activity recognition permission for beep notifications
        if (!isActivityRecognitionPermissionGranted()) {
            requestActivityRecognitionPermission()
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
