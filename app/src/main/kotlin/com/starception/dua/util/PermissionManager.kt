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
 * Utility class to manage app permissions including location and notifications
 */
class PermissionManager(private val activity: FragmentActivity) {
    
    companion object {
        const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1002
    }
    
    /**
     * Check if location permission is granted
     */
    fun isLocationPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Check if notification permission is granted (Android 13+)
     */
    fun isNotificationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Pre-Android 13, notifications are enabled by default
        }
    }
    
    /**
     * Check if location services are enabled
     */
    fun isLocationServicesEnabled(): Boolean {
        val locationManager = activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
               locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
    
    /**
     * Request location permission
     */
    fun requestLocationPermission() {
        if (!isLocationPermissionGranted()) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
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
        
        // Request notification permission if not granted (Android 13+)
        if (!isNotificationPermissionGranted()) {
            requestNotificationPermission()
        }
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
