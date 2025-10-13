package com.starception.submission.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ActivityTracker - Singleton to track current user activity
 * 
 * This provides a way for the PrayerNotificationService to update the current activity
 * and for UI components to observe the current activity state.
 */
object ActivityTracker {
    private val _currentActivity = MutableStateFlow("UNKNOWN")
    val currentActivity: StateFlow<String> = _currentActivity.asStateFlow()
    
    /**
     * Update the current activity (called from PrayerNotificationService)
     */
    fun updateActivity(activity: String) {
        _currentActivity.value = activity
    }
    
    /**
     * Get current activity synchronously for UI
     */
    fun getCurrentActivity(): String {
        return _currentActivity.value
    }
}