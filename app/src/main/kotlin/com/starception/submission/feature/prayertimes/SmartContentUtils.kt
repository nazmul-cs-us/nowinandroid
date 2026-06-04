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
            hour in 5..11 -> "Morning Light"
            hour in 12..17 -> "Day's Journey"
            hour in 18..22 -> "Evening Peace"
            else -> "Night's Rest"
        }
    }
    
    fun getSmartContent(
        currentTime: LocalTime,
        prayerTimes: DayPrayerTimes? = null,
        getCurrentPrayer: (() -> Pair<String, LocalTime>?)? = null
    ): String {
        // Use the formatTimeSinceCurrentPrayer function for consistent display
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
            hour in 5..11 -> "Begin with gratitude"
            hour in 12..17 -> "Remember Allah"
            hour in 18..22 -> "Count your blessings"
            else -> "Rest with peace"
        }
        android.util.Log.d("SmartContentUtils", "Returning fallback content: $result")
        return result
    }
    
    fun getSmartFooter(
        currentPrayer: Pair<String, LocalTime>?,
        nextPrayer: Pair<String, LocalTime>?
    ): String {
        return when {
            currentPrayer != null -> "${currentPrayer.first} time"
            nextPrayer != null -> "${nextPrayer.first} approaching"
            else -> "Be mindful"
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
            completed == total -> "Alhamdulillah"
            completed >= 3 -> "Keep it up"
            completed >= 1 -> "Continue strong"
            else -> "Fresh start"
        }
    }

    fun getDailyStatsMessage(completed: Int, total: Int): String {
        val remaining = total - completed

        return when {
            completed == total -> "All prayers done"
            remaining == 1 -> "One prayer left"
            remaining > 1 -> "$remaining prayers left"
            else -> "Begin your day"
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
            val times = prayerTimes ?: run {
                android.util.Log.d("SmartPrediction", "❌ No prayer times available")
                return null
            }
            
            android.util.Log.d("SmartPrediction", "📊 Current time: $currentTime")
            android.util.Log.d("SmartPrediction", "📊 Prayer times - Fajr: ${times.fajr}, Dhuhr: ${times.dhuhr}, Asr: ${times.asr}, Maghrib: ${times.maghrib}, Isha: ${times.isha}")
            
            // First try to get current prayer (within 30-min window)
            val currentPrayer = getCurrentPrayer?.invoke()
            
            if (currentPrayer != null) {
                // We have a current active prayer
                val (prayerName, prayerTime) = currentPrayer
                val duration = Duration.between(prayerTime, currentTime)
                val minutes = duration.toMinutes().toInt()
                
                android.util.Log.d("SmartPrediction", "✅ Current active prayer: $prayerName at $prayerTime")
                android.util.Log.d("SmartPrediction", "⏱️ Minutes since $prayerName: $minutes")
                
                return when {
                    minutes < -720 -> {
                        android.util.Log.d("SmartPrediction", "⚠️ Minutes < -720, returning null")
                        null
                    }
                    minutes > 720 -> {
                        android.util.Log.d("SmartPrediction", "⚠️ Minutes > 720, returning null")
                        null
                    }
                    else -> {
                        android.util.Log.d("SmartPrediction", "✅ Returning: ($minutes, $prayerName)")
                        Pair(minutes, prayerName)
                    }
                }
            }
            
            android.util.Log.d("SmartPrediction", "ℹ️ No current active prayer")
            
            // No current active prayer - determine if we should show last prayer or next prayer
            val allPrayerTimes = listOf(
                "Fajr" to times.fajr,
                "Dhuhr" to times.dhuhr,
                "Asr" to times.asr,
                "Maghrib" to times.maghrib,
                "Isha" to times.isha
            )
            
            val passedPrayers = allPrayerTimes.filter { (_, time) -> currentTime.isAfter(time) }
            val upcomingPrayers = allPrayerTimes.filter { (_, time) -> currentTime.isBefore(time) }
            
            android.util.Log.d("SmartPrediction", "📋 Passed prayers: ${passedPrayers.map { it.first }}")
            android.util.Log.d("SmartPrediction", "📋 Upcoming prayers: ${upcomingPrayers.map { it.first }}")
            
            // Determine which prayer to show
            val targetPrayer = when {
                upcomingPrayers.isNotEmpty() -> {
                    // There's an upcoming prayer today - show countdown to it
                    val next = upcomingPrayers.first()
                    android.util.Log.d("SmartPrediction", "⏭️ Next upcoming prayer: ${next.first} at ${next.second}")
                    next
                }
                passedPrayers.isNotEmpty() -> {
                    // All today's prayers passed - show time since last prayer (Isha)
                    // But only if it's been less than 3 hours since Isha
                    val lastPrayer = passedPrayers.last()
                    val minutesSinceLast = Duration.between(lastPrayer.second, currentTime).toMinutes().toInt()
                    android.util.Log.d("SmartPrediction", "🌙 All prayers passed. Last: ${lastPrayer.first} at ${lastPrayer.second}")
                    android.util.Log.d("SmartPrediction", "⏱️ Minutes since ${lastPrayer.first}: $minutesSinceLast")
                    
                    if (minutesSinceLast <= 180) { // 3 hours
                        android.util.Log.d("SmartPrediction", "✅ Within 3 hours, showing time since ${lastPrayer.first}")
                        lastPrayer
                    } else {
                        // It's late night, show countdown to tomorrow's Fajr
                        val minutesUntilMidnight = Duration.between(currentTime, LocalTime.MAX).toMinutes()
                        val minutesFromMidnightToFajr = Duration.between(LocalTime.MIN, times.fajr).toMinutes()
                        val totalMinutesUntilFajr = (minutesUntilMidnight + minutesFromMidnightToFajr + 1).toInt()
                        
                        android.util.Log.d("SmartPrediction", "🌃 Late night mode activated")
                        android.util.Log.d("SmartPrediction", "⏰ Minutes until midnight: $minutesUntilMidnight")
                        android.util.Log.d("SmartPrediction", "⏰ Minutes from midnight to Fajr: $minutesFromMidnightToFajr")
                        android.util.Log.d("SmartPrediction", "⏰ Total minutes until tomorrow's Fajr: $totalMinutesUntilFajr")
                        android.util.Log.d("SmartPrediction", "✅ Returning: (-$totalMinutesUntilFajr, Fajr)")
                        
                        return Pair(-totalMinutesUntilFajr, "Fajr")
                    }
                }
                else -> {
                    android.util.Log.d("SmartPrediction", "⚠️ No passed or upcoming prayers found")
                    null
                }
            }
            
            if (targetPrayer != null) {
                val (prayerName, prayerTime) = targetPrayer
                val duration = if (currentTime.isBefore(prayerTime)) {
                    Duration.between(currentTime, prayerTime)
                } else {
                    Duration.between(prayerTime, currentTime)
                }
                val minutes = if (currentTime.isBefore(prayerTime)) {
                    -duration.toMinutes().toInt() // Negative for upcoming prayers
                } else {
                    duration.toMinutes().toInt() // Positive for passed prayers
                }
                
                android.util.Log.d("SmartPrediction", "🎯 Target prayer: $prayerName at $prayerTime")
                android.util.Log.d("SmartPrediction", "⏱️ Calculated minutes: $minutes")
                
                return when {
                    minutes < -720 -> {
                        android.util.Log.d("SmartPrediction", "⚠️ Minutes < -720, returning null")
                        null
                    }
                    minutes > 720 -> {
                        android.util.Log.d("SmartPrediction", "⚠️ Minutes > 720, returning null")
                        null
                    }
                    else -> {
                        android.util.Log.d("SmartPrediction", "✅ Final result: ($minutes, $prayerName)")
                        Pair(minutes, prayerName)
                    }
                }
            }
            
            android.util.Log.d("SmartPrediction", "❌ No target prayer determined, returning null")
            return null
        } catch (e: Exception) {
            android.util.Log.e("SmartPrediction", "❌ Exception in getMinutesSinceCurrentPrayer", e)
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
                        minutes == 0 -> "$prayerName ended"
                        minutes < 0 -> "$prayerName in ${-minutes}m"
                        minutes < 60 -> "${minutes}m after $prayerName"
                        else -> {
                            val hours = minutes / 60
                            val remainingMinutes = minutes % 60
                            when {
                                remainingMinutes == 0 -> "${hours}h after $prayerName"
                                else -> "${hours}h ${remainingMinutes}m after $prayerName"
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
    
    /**
     * Get notification-synchronized prayer content
     * Returns the exact same text that appears in prayer time notifications
     * This ensures consistency between the Smart Prediction tile and notifications
     * 
     * IMPORTANT: This function uses ADJUSTED prayer times (base + user offsets).
     * This matches the small tiles display and ensures consistency across the UI.
     * 
     * @param prayerTimes BASE prayer times (DayPrayerTimes contains astronomical times only, no offsets)
     * @param currentTime Current local time
     * @param timeOffsets User offset adjustments to apply to base times
     * @return NotificationSyncContent with title, content, and next prayer info
     */
    fun getNotificationSyncContent(
        prayerTimes: DayPrayerTimes?,
        currentTime: LocalTime,
        timeOffsets: com.starception.submission.prayer.model.PrayerTimeOffsets = com.starception.submission.prayer.model.PrayerTimeOffsets(),
        goToMosqueDurationMinutes: (String) -> Int = { 20 },
    ): NotificationSyncContent? {
        return try {
            val times = prayerTimes ?: return null

            android.util.Log.d("SmartPrediction", "🔔 getNotificationSyncContent called at $currentTime")
            android.util.Log.d("SmartPrediction", "📅 BASE Prayer times: Fajr=${times.fajr}, Dhuhr=${times.dhuhr}, Asr=${times.asr}, Maghrib=${times.maghrib}, Isha=${times.isha}")
            android.util.Log.d("SmartPrediction", "⏰ User offsets: Fajr=${timeOffsets.fajr}, Dhuhr=${timeOffsets.dhuhr}, Asr=${timeOffsets.asr}, Maghrib=${timeOffsets.maghrib}, Isha=${timeOffsets.isha}")

            // CRITICAL: Apply user offsets to base times to match small tiles display
            // This ensures smart prediction shows countdowns to adjusted times (base + offset)
            // which matches what users see in the small prayer tiles
            // Apply offsets using the utility function from PrayerTimesUtils
            val adjustedFajr = com.starception.submission.feature.prayertimes.utils.applyOffsetToTime(times.fajr, timeOffsets.fajr)
            val adjustedDhuhr = com.starception.submission.feature.prayertimes.utils.applyOffsetToTime(times.dhuhr, timeOffsets.dhuhr)
            val adjustedAsr = com.starception.submission.feature.prayertimes.utils.applyOffsetToTime(times.asr, timeOffsets.asr)
            val adjustedMaghrib = com.starception.submission.feature.prayertimes.utils.applyOffsetToTime(times.maghrib, timeOffsets.maghrib)
            val adjustedIsha = com.starception.submission.feature.prayertimes.utils.applyOffsetToTime(times.isha, timeOffsets.isha)

            android.util.Log.d("SmartPrediction", "📅 ADJUSTED Prayer times (base + offset): Fajr=$adjustedFajr, Dhuhr=$adjustedDhuhr, Asr=$adjustedAsr, Maghrib=$adjustedMaghrib, Isha=$adjustedIsha")

            // Find current and next prayer using ADJUSTED times (base + offset)
            // This ensures smart prediction matches small tiles display
            val allPrayers = listOf(
                "Fajr" to adjustedFajr,
                "Dhuhr" to adjustedDhuhr,
                "Asr" to adjustedAsr,
                "Maghrib" to adjustedMaghrib,
                "Isha" to adjustedIsha
            )

            // Find current prayer (the most recent prayer that has passed TODAY)
            var currentPrayer = allPrayers.findLast { (_, time) ->
                currentTime.isAfter(time)
            }

            android.util.Log.d("SmartPrediction", "🕌 Current prayer (last passed today): ${currentPrayer?.first ?: "None"}")

            // CROSS-MIDNIGHT FIX: If no prayer has passed today yet (we're before Fajr),
            // use yesterday's Isha as the current prayer if it's been less than 12 hours
            if (currentPrayer == null && currentTime.isBefore(times.fajr)) {
                // We're in the early morning hours between midnight and Fajr
                // Assume yesterday's Isha was at the same time as today's Isha
                val yesterdayIsha = times.isha // Same time, but conceptually yesterday
                val minutesSinceMidnight = Duration.between(LocalTime.MIDNIGHT, currentTime).toMinutes()
                val minutesFromIshaToMidnight = Duration.between(times.isha, LocalTime.MAX).toMinutes() + 1
                val totalMinutesSinceYesterdayIsha = minutesFromIshaToMidnight + minutesSinceMidnight

                android.util.Log.d("SmartPrediction", "🌙 Cross-midnight scenario detected")
                android.util.Log.d("SmartPrediction", "⏰ Minutes since yesterday's Isha: $totalMinutesSinceYesterdayIsha")

                // Only use yesterday's Isha if it's been less than 12 hours (720 minutes)
                if (totalMinutesSinceYesterdayIsha <= 720) {
                    currentPrayer = "Isha" to yesterdayIsha
                    android.util.Log.d("SmartPrediction", "✅ Using yesterday's Isha as current prayer")
                } else {
                    android.util.Log.d("SmartPrediction", "⚠️ Too long since Isha, won't show content")
                }
            }

            // Find next prayer (the next upcoming prayer)
            val nextPrayerToday = allPrayers.find { (_, time) ->
                currentTime.isBefore(time)
            }

            val nextPrayer: Pair<String, LocalTime>?
            val isNextPrayerTomorrow: Boolean

            if (nextPrayerToday != null) {
                // There's a prayer left today
                nextPrayer = nextPrayerToday
                isNextPrayerTomorrow = false
            } else {
                // All prayers passed, next is tomorrow's Fajr
                nextPrayer = allPrayers.first()  // Fajr
                isNextPrayerTomorrow = true
            }

            // Show content if we have a current prayer (continues tracking until next prayer starts)
            if (currentPrayer != null && nextPrayer != null) {
                val (prayerName, prayerTime) = currentPrayer

                // Calculate elapsed time, handling cross-midnight scenario
                val elapsedMinutes = if (prayerName == "Isha" && currentTime.isBefore(times.fajr)) {
                    // Cross-midnight: We're tracking yesterday's Isha
                    val minutesSinceMidnight = Duration.between(LocalTime.MIDNIGHT, currentTime).toMinutes()
                    val minutesFromIshaToMidnight = Duration.between(times.isha, LocalTime.MAX).toMinutes() + 1
                    minutesFromIshaToMidnight + minutesSinceMidnight
                } else {
                    // Normal case: prayer is from today
                    Duration.between(prayerTime, currentTime).toMinutes()
                }

                android.util.Log.d("SmartPrediction", "⏱️ Elapsed since $prayerName: $elapsedMinutes minutes")

                // Format elapsed time exactly like notification service
                val elapsedText = formatNotificationElapsedTime(elapsedMinutes)
                val content = "$elapsedText since $prayerName"

                // Calculate prayer phase using the user's per-prayer "Go to Mosque" duration.
                val mosqueWindowMinutes = goToMosqueDurationMinutes(prayerName)
                val title = when {
                    elapsedMinutes <= mosqueWindowMinutes -> "Go to Mosque for $prayerName"
                    elapsedMinutes <= 60 -> "Best Time to Pray $prayerName"
                    else -> "Make Time for $prayerName"
                }

                android.util.Log.d("SmartPrediction", "📝 Generated title: $title")
                android.util.Log.d("SmartPrediction", "📝 Generated content: $content")

                // Next prayer countdown with "Next •" format to match notification exactly
                val nextPrayerText = if (nextPrayer != null) {
                    val (nextName, nextTime) = nextPrayer
                    val timeUntilNext = if (isNextPrayerTomorrow) {
                        // Calculate time until tomorrow's Fajr
                        val minutesUntilMidnight = Duration.between(currentTime, LocalTime.MAX).toMinutes()
                        val minutesFromMidnightToFajr = Duration.between(LocalTime.MIN, nextTime).toMinutes()
                        minutesUntilMidnight + minutesFromMidnightToFajr + 1
                    } else {
                        // Calculate time until today's next prayer
                        Duration.between(currentTime, nextTime).toMinutes()
                    }
                    val nextFormatted = formatNotificationTimeRemaining(timeUntilNext)
                    "Next • $nextName in $nextFormatted"
                } else ""

                android.util.Log.d("SmartPrediction", "📝 Next prayer info: $nextPrayerText")

                return NotificationSyncContent(
                    title = title,
                    content = content,
                    nextPrayerInfo = nextPrayerText
                )
            }

            android.util.Log.d("SmartPrediction", "❌ No content generated - currentPrayer=${currentPrayer?.first}, nextPrayer=${nextPrayer?.first}")
            return null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Format elapsed time exactly like the notification service
     * Uses "minutes" spelled out for < 60 minutes, abbreviated for >= 60 minutes
     */
    private fun formatNotificationElapsedTime(minutes: Long): String {
        return when {
            minutes == 0L -> "Just started"
            minutes == 1L -> "1 minute"
            minutes < 60 -> "$minutes minutes"
            else -> {
                val hours = minutes / 60
                val remainingMinutes = minutes % 60
                when {
                    remainingMinutes == 0L -> "${hours}h"
                    else -> "${hours}h ${remainingMinutes}m"
                }
            }
        }
    }
    
    /**
     * Format time remaining exactly like the notification service
     */
    private fun formatNotificationTimeRemaining(minutes: Long): String {
        return when {
            minutes < 60 -> "${minutes}m"
            else -> {
                val hours = minutes / 60
                val remainingMinutes = minutes % 60
                when {
                    remainingMinutes == 0L -> "${hours}h"
                    else -> "${hours}h ${remainingMinutes}m"
                }
            }
        }
    }
}

/**
 * Data class to hold notification-synchronized content
 */
data class NotificationSyncContent(
    val title: String,          // e.g., "Best Time to Pray Fajr"
    val content: String,        // e.g., "6h 51m since Fajr"
    val nextPrayerInfo: String  // e.g., "Next • Dhuhr in 2h 15m"
)