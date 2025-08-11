package com.starception.dua.prayer.service

import android.content.Context
import com.starception.dua.prayer.model.DayPrayerTimes
import com.starception.dua.prayer.model.PrayerTime
import java.time.LocalTime
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Smart prayer notification logic that provides context-aware prayer information
 */
@Singleton
class SmartPrayerNotificationService @Inject constructor() {
    
    /**
     * Prayer phases within a prayer window
     */
    enum class PrayerPhase {
        TRAVEL_TIME,    // First 10 minutes: Travel to mosque
        BEST_TIME,      // Next 30 minutes: Optimal prayer time  
        LAST_CHANCE     // Remaining time: Last chance to pray
    }
    
    /**
     * Complete prayer status information
     */
    data class PrayerStatus(
        val currentPrayer: String?,
        val nextPrayer: String,
        val nextPrayerTime: LocalTime,
        val isInPrayerWindow: Boolean,
        val phase: PrayerPhase?,
        val timeText: String,
        val progressPercentage: Int,
        val detailedMessage: String
    )
    
    /**
     * Calculate smart prayer status based on current time and prayer times
     */
    fun calculatePrayerStatus(prayerTimes: DayPrayerTimes): PrayerStatus {
        val now = LocalTime.now()
        val prayers = getPrayerList(prayerTimes)
        
        // Find current position relative to prayers
        val currentPrayerIndex = findCurrentPrayerIndex(prayers, now)
        val nextPrayerIndex = findNextPrayerIndex(prayers, now)
        
        val currentPrayer = if (currentPrayerIndex >= 0) prayers[currentPrayerIndex] else null
        val nextPrayer = prayers[nextPrayerIndex]
        
        // Determine if we're in prayer window or waiting for next prayer
        return if (currentPrayer != null) {
            calculateInPrayerWindowStatus(currentPrayer, nextPrayer, now)
        } else {
            calculateWaitingForPrayerStatus(nextPrayer, now, prayerTimes)
        }
    }
    
    /**
     * Calculate status when currently in a prayer time window
     */
    private fun calculateInPrayerWindowStatus(
        currentPrayer: PrayerInfo,
        nextPrayer: PrayerInfo,
        now: LocalTime
    ): PrayerStatus {
        val prayerDuration = Duration.between(currentPrayer.time, nextPrayer.time)
        val timeSincePrayerStart = Duration.between(currentPrayer.time, now)
        val timeUntilNextPrayer = Duration.between(now, nextPrayer.time)
        
        // Calculate progress percentage
        val progressPercentage = (timeSincePrayerStart.toMinutes() * 100 / prayerDuration.toMinutes()).toInt()
        
        // Determine prayer phase
        val minutesSinceStart = timeSincePrayerStart.toMinutes()
        val phase = when {
            minutesSinceStart <= 10 -> PrayerPhase.TRAVEL_TIME
            minutesSinceStart <= 40 -> PrayerPhase.BEST_TIME  // 10 + 30 minutes
            else -> PrayerPhase.LAST_CHANCE
        }
        
        // Check if we should show time passed or time remaining
        val halfwayPoint = prayerDuration.dividedBy(2)
        val showTimeRemaining = timeSincePrayerStart > halfwayPoint
        
        val timeText = if (showTimeRemaining) {
            formatDuration(timeUntilNextPrayer) + " until ${nextPrayer.name}"
        } else {
            formatDuration(timeSincePrayerStart) + " since ${currentPrayer.name}"
        }
        
        val detailedMessage = buildPrayerPhaseMessage(phase, currentPrayer.name, timeUntilNextPrayer)
        
        return PrayerStatus(
            currentPrayer = currentPrayer.name,
            nextPrayer = nextPrayer.name,
            nextPrayerTime = nextPrayer.time,
            isInPrayerWindow = true,
            phase = phase,
            timeText = timeText,
            progressPercentage = progressPercentage,
            detailedMessage = detailedMessage
        )
    }
    
    /**
     * Calculate status when waiting for next prayer
     */
    private fun calculateWaitingForPrayerStatus(
        nextPrayer: PrayerInfo,
        now: LocalTime,
        prayerTimes: DayPrayerTimes
    ): PrayerStatus {
        // Check if next prayer is Fajr and we're past Isha
        if (nextPrayer.name == "Fajr" && now.isAfter(prayerTimes.isha)) {
            // Since we're past today's Isha, next Fajr is tomorrow
            // Calculate duration to tomorrow's Fajr by adding the remaining time today plus time until Fajr tomorrow
            val timeUntilMidnight = Duration.between(now, LocalTime.MAX)
            val timeFromMidnightToFajr = Duration.between(LocalTime.MIN, nextPrayer.time)
            val totalTimeUntilTomorrowFajr = timeUntilMidnight.plus(timeFromMidnightToFajr)
            
            // Calculate if we're still in Isha prayer window (until halfway to Fajr)
            val timeFromIshaToFajr = totalTimeUntilTomorrowFajr.plus(Duration.between(prayerTimes.isha, now))
            val halfwayPoint = prayerTimes.isha.plus(timeFromIshaToFajr.dividedBy(2))
            
            if (now.isBefore(halfwayPoint)) {
                // We're in Isha prayer window, show elapsed time since Isha
                val timeSinceIsha = Duration.between(prayerTimes.isha, now)
                val formattedTime = formatDuration(timeSinceIsha)
                
                // Determine prayer phase based on elapsed time
                val minutesSinceIsha = timeSinceIsha.toMinutes()
                val phase = when {
                    minutesSinceIsha <= 20 -> PrayerPhase.TRAVEL_TIME
                    else -> PrayerPhase.BEST_TIME
                }
                
                val timeText = "$formattedTime since Isha"
                val detailedMessage = buildPrayerPhaseMessage(phase, "Isha", Duration.between(now, halfwayPoint))
                
                return PrayerStatus(
                    currentPrayer = "Isha",
                    nextPrayer = nextPrayer.name,
                    nextPrayerTime = nextPrayer.time, // Keep original time, duration calculation handles the day boundary
                    isInPrayerWindow = true,
                    phase = phase,
                    timeText = timeText,
                    progressPercentage = (minutesSinceIsha * 100 / timeFromIshaToFajr.dividedBy(2).toMinutes()).toInt(),
                    detailedMessage = detailedMessage
                )
            } else {
                // Past Isha prayer window, show elapsed time since Isha + time until next prayer
                val timeSinceIsha = Duration.between(prayerTimes.isha, now)
                val formattedTimeSince = formatDuration(timeSinceIsha)
                val formattedTimeUntil = formatDuration(totalTimeUntilTomorrowFajr)
                val timeText = "$formattedTimeSince since Isha"
                val detailedMessage = "Next prayer: ${nextPrayer.name} in $formattedTimeUntil"
                
                return PrayerStatus(
                    currentPrayer = null,
                    nextPrayer = nextPrayer.name,
                    nextPrayerTime = nextPrayer.time, // Keep original time, duration calculation handles the day boundary
                    isInPrayerWindow = false,
                    phase = null,
                    timeText = timeText,
                    progressPercentage = 0,
                    detailedMessage = detailedMessage
                )
            }
        }
        
        val timeUntilNext = Duration.between(now, nextPrayer.time)
        
        return PrayerStatus(
            currentPrayer = null,
            nextPrayer = nextPrayer.name,
            nextPrayerTime = nextPrayer.time,
            isInPrayerWindow = false,
            phase = null,
            timeText = formatDuration(timeUntilNext) + " until ${nextPrayer.name}",
            progressPercentage = 0,
            detailedMessage = "Next prayer: ${nextPrayer.name} at ${formatTime(nextPrayer.time)}"
        )
    }
    
    /**
     * Build detailed message based on prayer phase
     */
    private fun buildPrayerPhaseMessage(
        phase: PrayerPhase,
        prayerName: String,
        timeUntilWindowEnd: Duration
    ): String {
        val timeUntilEnd = formatDuration(timeUntilWindowEnd)
        return when (phase) {
            PrayerPhase.TRAVEL_TIME -> 
                "Travel time to mosque • Prayer window ends in $timeUntilEnd"
            PrayerPhase.BEST_TIME -> 
                "Best time to pray • Prayer window ends in $timeUntilEnd"
            PrayerPhase.LAST_CHANCE -> 
                "Last chance to pray • Prayer window ends in $timeUntilEnd"
        }
    }
    
    /**
     * Find current prayer index (-1 if between prayers)
     */
    private fun findCurrentPrayerIndex(prayers: List<PrayerInfo>, now: LocalTime): Int {
        for (i in 0 until prayers.size - 1) {
            if (now.isAfter(prayers[i].time) && now.isBefore(prayers[i + 1].time)) {
                return i
            }
        }
        // When past last prayer of the day, return -1 to trigger waiting status
        return -1
    }
    
    /**
     * Find next prayer index
     */
    private fun findNextPrayerIndex(prayers: List<PrayerInfo>, now: LocalTime): Int {
        for (i in prayers.indices) {
            if (prayers[i].time.isAfter(now)) {
                return i
            }
        }
        // If no prayer found today, next is Fajr (tomorrow)
        return 0
    }
    
    /**
     * Convert DayPrayerTimes to list of PrayerInfo (excluding Sunrise)
     */
    private fun getPrayerList(prayerTimes: DayPrayerTimes): List<PrayerInfo> {
        return listOf(
            PrayerInfo("Fajr", prayerTimes.fajr),
            PrayerInfo("Dhuhr", prayerTimes.dhuhr),
            PrayerInfo("Asr", prayerTimes.asr),
            PrayerInfo("Maghrib", prayerTimes.maghrib),
            PrayerInfo("Isha", prayerTimes.isha)
        )
    }
    
    /**
     * Format duration as human-readable string
     */
    private fun formatDuration(duration: Duration): String {
        val totalMinutes = duration.toMinutes()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            minutes > 0 -> "${minutes}m"
            else -> "now"
        }
    }
    
    /**
     * Format time as HH:MM
     */
    private fun formatTime(time: LocalTime): String {
        return time.toString().substring(0, 5) // HH:MM format
    }
    
    /**
     * Simple data class for prayer information
     */
    private data class PrayerInfo(
        val name: String,
        val time: LocalTime
    )
}