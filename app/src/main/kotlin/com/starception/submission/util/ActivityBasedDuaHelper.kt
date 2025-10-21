package com.starception.submission.util

import android.content.Context
import android.content.Intent
import android.util.Log
import com.starception.submission.service.ActivityBasedDuaService

/**
 * ACTIVITY-BASED DUA HELPER: Easy integration for activity detection and dua playing
 * 
 * This helper class provides simple methods to start/stop activity-based dua playing
 * throughout the application. It manages the background service and handles permissions.
 */
object ActivityBasedDuaHelper {
    
    private const val TAG = "ActivityBasedDuaHelper"
    
    /**
     * Start activity detection and dua playing
     */
    @JvmStatic
    fun startActivityDetection(context: Context) {
        try {
            val intent = ActivityBasedDuaService.createStartIntent(context)
            context.startService(intent)
            Log.i(TAG, "Started activity-based dua service")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting activity detection service", e)
        }
    }
    
    /**
     * Stop activity detection and dua playing
     */
    @JvmStatic
    fun stopActivityDetection(context: Context) {
        try {
            val intent = ActivityBasedDuaService.createStopIntent(context)
            context.startService(intent)
            Log.i(TAG, "Stopped activity-based dua service")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping activity detection service", e)
        }
    }
    
    /**
     * Check if activity detection service is running
     */
    fun isServiceRunning(context: Context): Boolean {
        // Note: This is a simplified check. In production, you might want to use
        // a more robust method like checking service state through a bound service
        return true // Placeholder - you could implement proper service state checking
    }
}
