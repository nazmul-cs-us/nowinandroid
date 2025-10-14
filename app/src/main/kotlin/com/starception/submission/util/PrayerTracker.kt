/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.starception.submission.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Tracks which prayers have been marked as prayed by the user
 */
object PrayerTracker {
    private const val TAG = "PrayerTracker"
    private const val PREFS_NAME = "prayer_tracker_prefs"
    private const val KEY_PRAYED_PRAYERS_PREFIX = "prayed_prayers_"
    
    private lateinit var prefs: SharedPreferences
    
    /**
     * Initialize the tracker with application context
     */
    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        Log.d(TAG, "PrayerTracker initialized")
    }
    
    /**
     * Mark a prayer as prayed for today
     */
    fun markPrayerAsPrayed(prayerName: String) {
        if (!::prefs.isInitialized) {
            Log.e(TAG, "PrayerTracker not initialized!")
            return
        }
        
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val key = "$KEY_PRAYED_PRAYERS_PREFIX$today"
        
        // Get existing prayed prayers for today
        val prayedPrayers = getPrayedPrayersForDate(today).toMutableSet()
        
        // Add the new prayer
        prayedPrayers.add(prayerName)
        
        // Save back to preferences
        prefs.edit()
            .putStringSet(key, prayedPrayers)
            .apply()
        
        Log.i(TAG, "✅ Marked $prayerName as prayed for $today. Total prayed: ${prayedPrayers.size}")
    }
    
    /**
     * Get the count of prayers marked as prayed today
     */
    fun getPrayedCountToday(): Int {
        if (!::prefs.isInitialized) {
            Log.e(TAG, "PrayerTracker not initialized!")
            return 0
        }
        
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        return getPrayedPrayersForDate(today).size
    }
    
    /**
     * Get all prayers marked as prayed for a specific date
     */
    private fun getPrayedPrayersForDate(date: String): Set<String> {
        if (!::prefs.isInitialized) {
            return emptySet()
        }
        
        val key = "$KEY_PRAYED_PRAYERS_PREFIX$date"
        return prefs.getStringSet(key, emptySet()) ?: emptySet()
    }
    
    /**
     * Check if a specific prayer has been marked as prayed today
     */
    fun isPrayerMarkedToday(prayerName: String): Boolean {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        return getPrayedPrayersForDate(today).contains(prayerName)
    }
    
    /**
     * Clear all prayed prayers for today (useful for testing)
     */
    fun clearToday() {
        if (!::prefs.isInitialized) {
            return
        }
        
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val key = "$KEY_PRAYED_PRAYERS_PREFIX$today"
        prefs.edit().remove(key).apply()
        Log.d(TAG, "Cleared prayed prayers for $today")
    }
    
    /**
     * Clean up old prayer records (older than 7 days)
     */
    fun cleanupOldRecords() {
        if (!::prefs.isInitialized) {
            return
        }
        
        val sevenDaysAgo = LocalDate.now().minusDays(7).format(DateTimeFormatter.ISO_LOCAL_DATE)
        val allKeys = prefs.all.keys
        val keysToRemove = allKeys.filter { key ->
            key.startsWith(KEY_PRAYED_PRAYERS_PREFIX) &&
            key.removePrefix(KEY_PRAYED_PRAYERS_PREFIX) < sevenDaysAgo
        }
        
        if (keysToRemove.isNotEmpty()) {
            val editor = prefs.edit()
            keysToRemove.forEach { editor.remove(it) }
            editor.apply()
            Log.d(TAG, "Cleaned up ${keysToRemove.size} old prayer records")
        }
    }
}

