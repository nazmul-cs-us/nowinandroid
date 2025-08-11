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
        // Check if next prayer is Fajr and we're past Isha (we're in Isha prayer window)
        if (nextPrayer.name == "Fajr" && now.isAfter(prayerTimes.isha)) {
            // We're in the Isha prayer window, calculate remaining time until next Fajr
            val timeUntilFajr = Duration.between(now, nextPrayer.time)
            val timeText = formatDuration(timeUntilFajr) + " remaining"
            val detailedMessage = "Isha time • ${formatDuration(timeUntilFajr)} remaining • Last chance to pray"
            
            return PrayerStatus(
                currentPrayer = "Isha",
                nextPrayer = nextPrayer.name,
                nextPrayerTime = nextPrayer.time,
                isInPrayerWindow = true,
                phase = PrayerPhase.LAST_CHANCE,
                timeText = timeText,
                progressPercentage = 75, // Assuming we're in the last phase
                detailedMessage = detailedMessage
            )
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
        timeRemaining: Duration
    ): String {
        return when (phase) {
            PrayerPhase.TRAVEL_TIME -> 
                "$prayerName time started • ${formatDuration(timeRemaining)} remaining • Travel time to mosque"
            PrayerPhase.BEST_TIME -> 
                "$prayerName time • ${formatDuration(timeRemaining)} remaining • Best time to pray"
            PrayerPhase.LAST_CHANCE -> 
                "$prayerName time • ${formatDuration(timeRemaining)} remaining • Last chance to pray"
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
        // Check if after last prayer of the day
        if (now.isAfter(prayers.last().time)) {
            return prayers.size - 1
        }
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