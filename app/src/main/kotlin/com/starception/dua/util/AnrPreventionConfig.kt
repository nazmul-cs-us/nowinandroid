package com.starception.submission.util

import android.util.Log

/**
 * Configuration class for ANR prevention settings
 * Centralizes all timeout and performance settings to prevent Application Not Responding issues
 */
object AnrPreventionConfig {
    
    private const val TAG = "AnrPreventionConfig"
    
    // Service timeout settings
    const val SERVICE_STARTUP_TIMEOUT_MS = 4000L // 4 seconds (below 5s ANR threshold)
    const val MAX_SERVICE_RUNTIME_MS = 30 * 60 * 1000L // 30 minutes max service time
    const val SERVICE_UPDATE_INTERVAL_MS = 60000L // 1 minute between updates (production)
    
    // Always-on display optimization
    const val ALWAYS_ON_DISPLAY_UPDATE_INTERVAL_MS = 30000L // 30 seconds for always-on display to prevent color flashing
    
    // Thread priority settings
    const val BACKGROUND_THREAD_PRIORITY = Thread.NORM_PRIORITY - 1
    const val SERVICE_THREAD_PRIORITY = Thread.NORM_PRIORITY - 2
    
    // Initialization settings
    const val ENABLE_LAZY_INITIALIZATION = true
    const val ENABLE_BACKGROUND_SYNC = true // Temporarily enabled to populate topics data
    const val ENABLE_AUTO_SERVICE_START = true // Enabled for notifications
    
    // Performance monitoring
    const val ENABLE_PERFORMANCE_LOGGING = true
    const val LOG_SERVICE_LIFECYCLE = true
    
    /**
     * Check if current configuration is optimized for ANR prevention
     */
    fun isOptimizedForAnrPrevention(): Boolean {
        val isOptimized = SERVICE_STARTUP_TIMEOUT_MS < 5000 &&
                ENABLE_LAZY_INITIALIZATION &&
                !ENABLE_BACKGROUND_SYNC &&
                !ENABLE_AUTO_SERVICE_START
        
        if (ENABLE_PERFORMANCE_LOGGING) {
            Log.d(TAG, "ANR Prevention Config - Optimized: $isOptimized")
            Log.d(TAG, "Service timeout: ${SERVICE_STARTUP_TIMEOUT_MS}ms")
            Log.d(TAG, "Max service runtime: ${MAX_SERVICE_RUNTIME_MS / 1000}s")
            Log.d(TAG, "Lazy init: $ENABLE_LAZY_INITIALIZATION")
            Log.d(TAG, "Background sync: $ENABLE_BACKGROUND_SYNC")
            Log.d(TAG, "Auto service start: $ENABLE_AUTO_SERVICE_START")
        }
        
        return isOptimized
    }
    
    /**
     * Get recommended thread priority for background operations
     */
    fun getBackgroundThreadPriority(): Int {
        return BACKGROUND_THREAD_PRIORITY
    }
    
    /**
     * Get recommended thread priority for service operations
     */
    fun getServiceThreadPriority(): Int {
        return SERVICE_THREAD_PRIORITY
    }
}
