/**
 * SMART CONTENT UTILS
 * 
 * This file contains intelligent content generation utilities that provide contextual,
 * time-aware content for the Prayer Times feature. It creates dynamic spiritual guidance
 * and progress tracking based on the current time of day and prayer completion status.
 * 
 * WHAT IT DOES:
 * - Generates time-based spiritual content and guidance messages
 * - Provides contextual titles that change throughout the day
 * - Calculates daily prayer completion progress and statistics
 * - Creates motivational messages based on prayer completion status
 * - Offers Islamic spiritual guidance appropriate for different times
 * - Tracks prayer completion metrics and provides encouraging feedback
 * 
 * WHERE IT'S USED:
 * - SwipeableBigTiles.kt: Smart Info Tile and Daily Stats Tile (lines ~490-501)
 * - PrayerTimesScreen.kt: Called indirectly through SwipeableBigTiles component
 * - Replaces ~90 lines of inline smart content generation functions
 * - Called through SmartContentUtils.functionName() static methods
 * 
 * CONTENT CATEGORIES:
 * 
 * TIME-BASED CONTENT:
 * - Morning Focus (5-11 AM): "Start your day with intention and gratitude"
 * - Afternoon Progress (12-17 PM): "Keep Allah in your thoughts as you work"
 * - Evening Reflection (18-22 PM): "Reflect on today's blessings and lessons"
 * - Night Preparation (23-4 AM): "Prepare your heart for tomorrow's opportunities"
 * 
 * PRAYER PROGRESS TRACKING:
 * - Counts completed prayers (prayers whose time has passed)
 * - Calculates progress ratio (completed/total prayers)
 * - Provides encouraging titles based on completion status
 * - Generates motivational messages for remaining prayers
 * 
 * KEY FUNCTIONS:
 * - getSmartTitle(): Returns time-appropriate titles (Morning Focus, etc.)
 * - getSmartContent(): Provides spiritual guidance messages
 * - getSmartFooter(): Shows current prayer context ("In Fajr time", etc.)
 * - getPrayerProgress(): Calculates daily completion (3/5 prayers completed)
 * - getDailyStatsTitle(): Dynamic titles based on progress ("Great Progress!", etc.)
 * - getDailyStatsMessage(): Encouraging messages ("2 prayers remaining today")
 * 
 * PROGRESS STATUS LOGIC:
 * - Perfect Day: All 5 prayers completed
 * - Great Progress: 3+ prayers completed
 * - Keep Going: 1-2 prayers completed
 * - New Day Begins: No prayers completed yet
 * 
 * SPIRITUAL GUIDANCE PRINCIPLES:
 * - Islamic context: References Allah and Islamic concepts appropriately
 * - Positive messaging: Always encouraging and supportive
 * - Time-sensitive: Content changes based on natural daily rhythms
 * - Progress-focused: Celebrates achievements and motivates completion
 * 
 * DATA DEPENDENCIES:
 * - DayPrayerTimes: For calculating prayer completion progress
 * - LocalTime: For time-based content selection and progress tracking
 * - Current/Next prayer data: For contextual footer messages
 * 
 * DESIGN PATTERNS:
 * - Object singleton: All functions are static utility methods
 * - Pure functions: Deterministic output based on input parameters
 * - Islamic UX: Content respects Islamic values and terminology
 * - Motivational design: Always positive and encouraging messaging
 */
package com.starception.submission.feature.prayertimes

import com.starception.submission.prayer.model.DayPrayerTimes
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.Duration

object SmartContentUtils {
    
    // Get smart information based on time of day and prayer status
    fun getSmartTitle(currentTime: LocalTime): String {
        val hour = currentTime.hour
        
        return when {
            hour in 5..11 -> "Morning Focus"
            hour in 12..17 -> "Afternoon Progress"
            hour in 18..22 -> "Evening Reflection"
            else -> "Night Preparation"
        }
    }
    
    fun getSmartContent(
        currentTime: LocalTime,
        prayerTimes: DayPrayerTimes? = null,
        getCurrentPrayer: (() -> Pair<String, LocalTime>?)? = null
    ): String {
        // Use the new formatTimeSinceCurrentPrayer function for consistent display
        val timeSinceCurrentPrayer = getMinutesSinceCurrentPrayer(prayerTimes, currentTime, getCurrentPrayer)
        val formatted = formatTimeSinceCurrentPrayer(timeSinceCurrentPrayer)
        
        // If we have time since current prayer data, show it
        if (formatted.isNotEmpty()) {
            return formatted
        }
        
        // Otherwise, fall back to spiritual guidance
        return getFallbackContent(currentTime)
    }
    
    private fun getFallbackContent(currentTime: LocalTime): String {
        // Fallback to time-based spiritual guidance
        val hour = currentTime.hour
        val result = when {
            hour in 5..11 -> "Start your day with intention and gratitude"
            hour in 12..17 -> "Keep Allah in your thoughts as you work"
            hour in 18..22 -> "Reflect on today's blessings and lessons"
            else -> "Prepare your heart for tomorrow's opportunities"
        }
        android.util.Log.d("SmartContentUtils", "Returning fallback content: $result")
        return result
    }
    
    fun getSmartFooter(
        currentPrayer: Pair<String, LocalTime>?,
        nextPrayer: Pair<String, LocalTime>?
    ): String {
        return when {
            currentPrayer != null -> "In ${currentPrayer.first} time"
            nextPrayer != null -> "Approaching ${nextPrayer.first}"
            else -> "Stay mindful"
        }
    }
    
    // Calculate daily prayer progress
    fun getPrayerProgress(
        prayerTimes: DayPrayerTimes?,
        currentTime: LocalTime
    ): Pair<Int, Int> {
        val times = prayerTimes ?: return Pair(0, 5)
        
        val prayers = listOf(
            "Fajr" to times.fajr,
            "Dhuhr" to times.dhuhr,
            "Asr" to times.asr,
            "Maghrib" to times.maghrib,
            "Isha" to times.isha
        )
        
        val completedCount = prayers.count { it.second.isBefore(currentTime) }
        return Pair(completedCount, 5)
    }
    
    fun getDailyStatsTitle(completed: Int, total: Int): String {
        return when {
            completed == total -> "Perfect Day!"
            completed >= 3 -> "Great Progress"
            completed >= 1 -> "Keep Going"
            else -> "New Day Begins"
        }
    }
    
    fun getDailyStatsMessage(completed: Int, total: Int): String {
        val remaining = total - completed
        
        return when {
            completed == total -> "All prayers completed with devotion"
            remaining == 1 -> "1 prayer remaining today"
            remaining > 1 -> "$remaining prayers remaining today"
            else -> "Ready to begin the day with prayer"
        }
    }
    
    /**
     * Calculate minutes since current prayer (optimized to prevent ANRs)
     * Returns positive if current prayer has passed, negative if current prayer is upcoming, null if no prayer times
     */
    fun getMinutesSinceCurrentPrayer(
        prayerTimes: DayPrayerTimes?,
        currentTime: LocalTime,
        getCurrentPrayer: (() -> Pair<String, LocalTime>?)? = null
    ): Pair<Int, String>? {
        return try {
            val times = prayerTimes ?: return null
            
            // First try to get current prayer (within 30-min window)
            val currentPrayer = getCurrentPrayer?.invoke()
            val targetPrayer = if (currentPrayer != null) {
                currentPrayer
            } else {
                // If no current prayer, find the most recent prayer that has passed
                val allPrayerTimes = listOf(
                    "Fajr" to times.fajr,
                    "Dhuhr" to times.dhuhr,
                    "Asr" to times.asr,
                    "Maghrib" to times.maghrib,
                    "Isha" to times.isha
                )
                
                allPrayerTimes
                    .filter { (_, time) -> currentTime.isAfter(time) }
                    .maxByOrNull { (_, time) -> time }
            }
            
            if (targetPrayer != null) {
                val (prayerName, prayerTime) = targetPrayer
                val duration = Duration.between(prayerTime, currentTime)
                val minutes = duration.toMinutes().toInt()
                
                // Limit to reasonable range to prevent display issues
                return when {
                    minutes < -720 -> null // More than 12 hours before prayer - don't show
                    minutes > 720 -> null  // More than 12 hours after prayer - don't show
                    else -> Pair(minutes, prayerName)
                }
            }
            
            return null
        } catch (e: Exception) {
            null // Return null on any error
        }
    }
    
    /**
     * Format minutes since current prayer for display (optimized to prevent ANRs)
     */
    fun formatTimeSinceCurrentPrayer(minutesAndPrayer: Pair<Int, String>?): String {
        // Return empty string if calculation fails to prevent ANRs
        return try {
            when {
                minutesAndPrayer == null -> ""
                else -> {
                    val (minutes, prayerName) = minutesAndPrayer
                    when {
                        minutes == 0 -> "Just finished $prayerName"
                        minutes < 0 -> "$prayerName in ${-minutes}m"
                        minutes < 60 -> "${minutes}m since $prayerName"
                        else -> {
                            val hours = minutes / 60
                            val remainingMinutes = minutes % 60
                            when {
                                remainingMinutes == 0 -> "${hours}h since $prayerName"
                                hours == 1 -> "1h ${remainingMinutes}m since $prayerName"
                                else -> "${hours}h ${remainingMinutes}m since $prayerName"
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            "" // Return empty on any error to prevent crashes
        }
    }
    
    /**
     * @deprecated Use getMinutesSinceCurrentPrayer instead
     * Calculate minutes since Asr prayer (optimized to prevent ANRs)
     * Returns positive if Asr has passed, negative if Asr is upcoming, null if no prayer times
     */
    @Deprecated("Use getMinutesSinceCurrentPrayer instead")
    fun getMinutesSinceAsr(
        prayerTimes: DayPrayerTimes?,
        currentTime: LocalTime
    ): Int? {
        return try {
            val times = prayerTimes ?: return null
            
            // Calculate duration from Asr to current time with error handling
            val duration = Duration.between(times.asr, currentTime)
            val minutes = duration.toMinutes().toInt()
            
            // Limit to reasonable range to prevent display issues
            return when {
                minutes < -720 -> null // More than 12 hours before Asr - don't show
                minutes > 720 -> null  // More than 12 hours after Asr - don't show
                else -> minutes
            }
        } catch (e: Exception) {
            null // Return null on any error
        }
    }
    
    /**
     * @deprecated Use formatTimeSinceCurrentPrayer instead
     * Format minutes since Asr for display (optimized to prevent ANRs)
     */
    @Deprecated("Use formatTimeSinceCurrentPrayer instead")
    fun formatTimeSinceAsr(minutesSinceAsr: Int?): String {
        // Return empty string if calculation fails to prevent ANRs
        return try {
            when {
                minutesSinceAsr == null -> ""
                minutesSinceAsr == 0 -> "Just now"
                minutesSinceAsr < 0 -> "Asr in ${-minutesSinceAsr}m"
                minutesSinceAsr < 60 -> "${minutesSinceAsr}m since Asr"
                else -> {
                    val hours = minutesSinceAsr / 60
                    val remainingMinutes = minutesSinceAsr % 60
                    when {
                        remainingMinutes == 0 -> "${hours}h since Asr"
                        hours == 1 -> "1h ${remainingMinutes}m since Asr"
                        else -> "${hours}h ${remainingMinutes}m since Asr"
                    }
                }
            }
        } catch (e: Exception) {
            "" // Return empty on any error to prevent crashes
        }
    }
}