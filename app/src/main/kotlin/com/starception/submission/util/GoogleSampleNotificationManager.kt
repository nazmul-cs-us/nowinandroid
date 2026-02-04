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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_DEFAULT
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat.ProgressStyle
import android.graphics.Color
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.starception.submission.R
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.logging.Level
import java.util.logging.Logger
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap

object GoogleSampleNotificationManager {
    private lateinit var notificationManager: NotificationManager
    private lateinit var appContext: Context
    private lateinit var prefs: SharedPreferences

    const val CHANNEL_ID = "google_live_updates_channel_id"
    private const val CHANNEL_NAME = "Google Live Updates Test"
    const val NOTIFICATION_ID = 1001 // Must match PrayerNotificationService.NOTIFICATION_ID for foreground service

    /**
     * Get the notification ID used for Live Update notifications.
     * This should be used with startForeground() in the service.
     */
    fun getNotificationId(): Int = NOTIFICATION_ID

    // SharedPreferences keys for persistent storage
    private const val PREFS_NAME = "adhan_tracker_prefs"
    private const val KEY_LAST_ADHAN_PRAYER_NAME = "last_adhan_prayer_name"
    private const val KEY_LAST_ADHAN_PRAYER_TIME = "last_adhan_prayer_time"
    private const val KEY_LAST_ADHAN_PLAYED_TIME = "last_adhan_played_time"

    // Track current prayer phase to detect phase changes (memory only - ok to reset)
    private var currentPhase: Int = -1 // -1 = not set, 0 = Go to Mosque, 1 = Best Time, 2 = Make Time

    // Track current prayer information for action buttons (memory only - ok to reset)
    private var currentPrayerName: String = ""
    private var currentPrayerTime: String = ""

    // Track app startup time to prevent Adhan during first few minutes after app launch
    private var appInitializedTime: Long = 0L

    @RequiresApi(Build.VERSION_CODES.O)
    fun initialize(context: Context, notifManager: NotificationManager) {
        notificationManager = notifManager
        appContext = context

        // Initialize SharedPreferences for persistent storage
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Record app initialization time
        appInitializedTime = System.currentTimeMillis()

        // Log the last Adhan info from persistent storage
        val lastPrayerName = prefs.getString(KEY_LAST_ADHAN_PRAYER_NAME, "") ?: ""
        val lastPrayerTime = prefs.getString(KEY_LAST_ADHAN_PRAYER_TIME, "") ?: ""
        val lastPlayedTime = prefs.getLong(KEY_LAST_ADHAN_PLAYED_TIME, 0L)

        android.util.Log.d("GoogleSampleNotificationManager",
            "🚀 App initialized - Adhan disabled for 30 seconds")
        android.util.Log.d("GoogleSampleNotificationManager",
            "📱 Last Adhan from storage: Prayer='$lastPrayerName' Time='$lastPrayerTime' PlayedAt=${if (lastPlayedTime > 0) java.time.Instant.ofEpochMilli(lastPlayedTime) else "Never"}")

        // CRITICAL FIX: Delete old channel and recreate without sound
        // Notification channels are cached, so we must delete the old one first
        notificationManager.deleteNotificationChannel(CHANNEL_ID)
        android.util.Log.d("GoogleSampleNotificationManager", "🗑️ Deleted old notification channel")

        // Create new channel WITHOUT sound - only use MediaPlayer for Adhan
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, IMPORTANCE_DEFAULT).apply {
            setSound(null, null)  // No default sound - we control adhan via MediaPlayer
        }
        notificationManager.createNotificationChannel(channel)
        android.util.Log.d("GoogleSampleNotificationManager", "📢 Notification channel created WITHOUT sound - adhan controlled via MediaPlayer only")
    }

    private enum class OrderState(val delay: Long) {
        INITIALIZING(1000) {
            @RequiresApi(35) // Android 16+ (BAKLAVA = 35)
            override fun buildNotification(): NotificationCompat.Builder {
                return buildBaseNotification(appContext, INITIALIZING)
                    .setSmallIcon(R.drawable.ic_prayer)
                    .setContentTitle("Prayer order is being placed")
                    .setContentText("Confirming with mosque...")
                    .setStyle(buildBaseProgressStyle(INITIALIZING).setProgressIndeterminate(true))
            }
        },
        FOOD_PREPARATION(5000) {
            @RequiresApi(35) // Android 16+ (BAKLAVA = 35)
            override fun buildNotification(): NotificationCompat.Builder {
                return buildBaseNotification(appContext, FOOD_PREPARATION)
                    .setContentTitle("Prayer time is being prepared")
                    .setContentText("Next step will be prayer call")
                    .setLargeIcon(
                        IconCompat.createWithResource(
                            appContext, R.drawable.ic_prayer
                        ).toIcon(appContext)
                    )
                    .setStyle(buildBaseProgressStyle(FOOD_PREPARATION).setProgress(25))
            }
        },
        FOOD_ENROUTE(9000) {
            @RequiresApi(35) // Android 16+ (BAKLAVA = 35)
            override fun buildNotification(): NotificationCompat.Builder {
                return buildBaseNotification(appContext, FOOD_ENROUTE)
                    .setContentTitle("Prayer time is approaching")
                    .setContentText("Get ready for prayer")
                    .setStyle(
                        buildBaseProgressStyle(FOOD_ENROUTE)
                            .setProgressTrackerIcon(
                                IconCompat.createWithResource(
                                    appContext, R.drawable.ic_prayer
                                )
                            )
                            .setProgress(50)
                    )
                    .setLargeIcon(
                        IconCompat.createWithResource(
                            appContext, R.drawable.ic_prayer
                        ).toIcon(appContext)
                    )
            }
        },
        FOOD_ARRIVING(13000) {
            @RequiresApi(35) // Android 16+ (BAKLAVA = 35)
            override fun buildNotification(): NotificationCompat.Builder {
                return buildBaseNotification(appContext, FOOD_ARRIVING)
                    .setContentTitle("Prayer time has arrived")
                    .setContentText("Time for prayer. Please proceed to prayer area.")
                    .setStyle(
                        buildBaseProgressStyle(FOOD_ARRIVING)
                            .setProgressTrackerIcon(
                                IconCompat.createWithResource(
                                    appContext, R.drawable.ic_prayer
                                )
                            )
                            .setProgress(75)
                    )
                    .setLargeIcon(
                        IconCompat.createWithResource(
                            appContext, R.drawable.ic_prayer
                        ).toIcon(appContext)
                    )
            }
        },
        ORDER_COMPLETE(17000) {
            @RequiresApi(35) // Android 16+ (BAKLAVA = 35)
            override fun buildNotification(): NotificationCompat.Builder {
                return buildBaseNotification(appContext, ORDER_COMPLETE)
                    .setContentTitle("Prayer completed.")
                    .setContentText("Thank you for using Prayer Tracker.")
                    .setStyle(
                        buildBaseProgressStyle(ORDER_COMPLETE)
                            .setProgressTrackerIcon(
                                IconCompat.createWithResource(
                                    appContext, R.drawable.ic_prayer
                                )
                            )
                            .setProgress(100)
                    )
                    .setLargeIcon(
                        IconCompat.createWithResource(
                            appContext, R.drawable.ic_prayer
                        ).toIcon(appContext)
                    )
            }
        };

        @RequiresApi(35) // Android 16+ (BAKLAVA = 35)
        fun buildBaseProgressStyle(orderState: OrderState): ProgressStyle {
            val pointColor = Color.valueOf(
                236f / 255f, // Normalize red value to be between 0.0 and 1.0
                183f / 255f, // Normalize green value to be between 0.0 and 1.0
                255f / 255f, // Normalize blue value to be between 0.0 and 1.0
                1f,
            ).toArgb()
            val segmentColor = Color.valueOf(
                134f / 255f, // Normalize red value to be between 0.0 and 1.0
                247f / 255f, // Normalize green value to be between 0.0 and 1.0
                250f / 255f, // Normalize blue value to be between 0.0 and 1.0
                1f,
            ).toArgb()
            var progressStyle = NotificationCompat.ProgressStyle()
                .setProgressPoints(
                    listOf(
                        ProgressStyle.Point(25).setColor(pointColor),
                        ProgressStyle.Point(50).setColor(pointColor),
                        ProgressStyle.Point(75).setColor(pointColor),
                        ProgressStyle.Point(100).setColor(pointColor)
                    )
                ).setProgressSegments(
                    listOf(
                        ProgressStyle.Segment(25).setColor(segmentColor),
                        ProgressStyle.Segment(25).setColor(segmentColor),
                        ProgressStyle.Segment(25).setColor(segmentColor),
                        ProgressStyle.Segment(25).setColor(segmentColor)
                    )
                )
            when (orderState) {
                INITIALIZING -> {}
                FOOD_PREPARATION -> {}
                FOOD_ENROUTE -> progressStyle.setProgressPoints(
                    listOf(
                        ProgressStyle.Point(25).setColor(pointColor)
                    )
                )

                FOOD_ARRIVING -> progressStyle.setProgressPoints(
                    listOf(
                        ProgressStyle.Point(25).setColor(pointColor),
                        ProgressStyle.Point(50).setColor(pointColor)
                    )
                )

                ORDER_COMPLETE -> progressStyle.setProgressPoints(
                    listOf(
                        ProgressStyle.Point(25).setColor(pointColor),
                        ProgressStyle.Point(50).setColor(pointColor),
                        ProgressStyle.Point(75).setColor(pointColor)
                    )
                )
            }
            return progressStyle
        }

        @RequiresApi(Build.VERSION_CODES.O)
        fun buildBaseNotification(appContext: Context, orderState: OrderState): NotificationCompat.Builder {
            val notificationBuilder = NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_prayer)
                .setOngoing(true)
                .setRequestPromotedOngoing(true)

            when (orderState) {
                INITIALIZING -> {}
                FOOD_PREPARATION -> {}
                FOOD_ENROUTE -> {}
                FOOD_ARRIVING ->
                    notificationBuilder
                        .addAction(
                            NotificationCompat.Action.Builder(null, "Got it", null).build()
                        )
                        .addAction(
                            NotificationCompat.Action.Builder(null, "Snooze", null).build()
                        )
                ORDER_COMPLETE ->
                    notificationBuilder
                        .addAction(
                            NotificationCompat.Action.Builder(
                                null, "Rate prayer", null).build()
                        )
            }
            return notificationBuilder
        }

        abstract fun buildNotification(): NotificationCompat.Builder
    }

    @RequiresApi(35) // Android 16+ (BAKLAVA = 35)
    fun start() {
        android.util.Log.d("GoogleSampleNotificationManager", "Starting Google Sample Live Update notifications...")
        
        for (state in OrderState.entries) {
            val notification = state.buildNotification().build()

            Logger.getLogger("canPostPromotedNotifications")
                .log(
                    Level.INFO,
                    notificationManager.canPostPromotedNotifications().toString())
            Logger.getLogger("hasPromotableCharacteristics")
                .log(
                    Level.INFO,
                    notification.hasPromotableCharacteristics().toString())

            Handler(Looper.getMainLooper()).postDelayed({
                android.util.Log.d("GoogleSampleNotificationManager", "Posting notification for state: $state")
                notificationManager.notify(NOTIFICATION_ID, notification)
            }, state.delay)
        }
    }
    
    @RequiresApi(35) // Android 16+ (BAKLAVA = 35)
    fun startSingleTest() {
        android.util.Log.d("GoogleSampleNotificationManager", "Starting single Google Sample Live Update test...")
        
        val notification = OrderState.FOOD_ENROUTE.buildNotification().build()
        
        android.util.Log.d("GoogleSampleNotificationManager", "canPostPromotedNotifications: ${notificationManager.canPostPromotedNotifications()}")
        android.util.Log.d("GoogleSampleNotificationManager", "hasPromotableCharacteristics: ${notification.hasPromotableCharacteristics()}")
        
        notificationManager.notify(NOTIFICATION_ID, notification)
        android.util.Log.d("GoogleSampleNotificationManager", "Posted single test notification")
    }
    
    /**
     * Builds and returns a prayer notification WITHOUT posting it.
     * The caller (PrayerNotificationService) should use startForeground() with this notification
     * to maintain foreground service priority.
     *
     * @return The built Notification object ready for startForeground()
     */
    @RequiresApi(35) // Android 16+ (BAKLAVA = 35)
    fun buildPrayerNotification(title: String, content: String, detailedMessage: String = "", progress: Int, prayerPhase: String = "", prayerName: String = "", prayerTime: String = "", nextPrayerCountdown: String = ""): android.app.Notification {
        android.util.Log.d("GoogleSampleNotificationManager", "Building Progress-Centric prayer notification (for startForeground)...")
        
        // Store current prayer information for action buttons
        if (prayerName.isNotEmpty()) {
            currentPrayerName = prayerName
        }
        if (prayerTime.isNotEmpty()) {
            currentPrayerTime = prayerTime
        }
        
        // Determine current prayer phase - use actual phase from prayer service if provided
        val newPhase = if (prayerPhase.isNotEmpty()) {
            when (prayerPhase) {
                "GO_TO_MOSQUE" -> 0
                "BEST_TIME" -> 1 
                "MAKE_TIME" -> 2
                else -> when {
                    progress <= 20 -> 0  // Fallback to progress-based if unknown phase
                    progress <= 60 -> 1
                    else -> 2
                }
            }
        } else {
            // Fallback to old logic if no phase provided
            when {
                progress <= 20 -> 0  // Go to Mosque (0-20%)
                progress <= 60 -> 1  // Best Time to Pray (20-60%)
                else -> 2            // Make Time for Prayer (60-100%)
            }
        }
        
        // Check if this is a new prayer cycle (progress jumped backwards significantly)
        // This happens when we move from one prayer to the next
        val isNewPrayerCycle = currentPhase != -1 && newPhase < currentPhase
        if (isNewPrayerCycle) {
            android.util.Log.d("GoogleSampleNotificationManager", 
                "🔄 NEW PRAYER CYCLE: Phase reset from ${currentPhase} to ${newPhase}")
            currentPhase = -1  // Reset to allow first phase to alert
        }
        
        // Check if this is a phase change (should alert) or silent update
        val isPhaseChange = currentPhase != -1 && currentPhase != newPhase
        val isFirstNotification = currentPhase == -1
        val shouldAlert = isPhaseChange || isFirstNotification
        
        // Log phase change detection
        when {
            isFirstNotification -> {
                val phaseNames = arrayOf("Go to Mosque", "Best Time", "Make Time")
                android.util.Log.d("GoogleSampleNotificationManager", 
                    "🔔 FIRST NOTIFICATION: Starting with ${phaseNames[newPhase]} phase (will alert)")
            }
            isPhaseChange -> {
                val phaseNames = arrayOf("Go to Mosque", "Best Time", "Make Time")
                android.util.Log.d("GoogleSampleNotificationManager", 
                    "🔔 PHASE CHANGE DETECTED: ${phaseNames[currentPhase]} → ${phaseNames[newPhase]} (will alert)")
            }
            else -> {
                android.util.Log.d("GoogleSampleNotificationManager", 
                    "🔕 Silent update: Phase ${newPhase}, Progress ${progress}% (no alert)")
            }
        }
        
        // Update tracked phase
        currentPhase = newPhase
        
        // Android 16 Progress-Centric: Define segment colors for prayer urgency progression
        // Green (Go to Mosque) → Yellow (Best Time) → Red (Make Time)
        val greenSegmentColor = Color.valueOf(76f / 255f, 175f / 255f, 80f / 255f, 1f).toArgb()     // Green - Go to Mosque
        val yellowSegmentColor = Color.valueOf(255f / 255f, 193f / 255f, 7f / 255f, 1f).toArgb()    // Yellow - Best Time
        val redSegmentColor = Color.valueOf(244f / 255f, 67f / 255f, 54f / 255f, 1f).toArgb()       // Red - Make Time
        val graySegmentColor = Color.valueOf(189f / 255f, 189f / 255f, 189f / 255f, 1f).toArgb()    // Gray - Pending/Inactive
        
        // Define distinct milestone colors for clear visual hierarchy
        val mosquePhaseColor = Color.valueOf(46f / 255f, 125f / 255f, 50f / 255f, 1f).toArgb()    // Green for Go to Mosque
        val bestTimeColor = Color.valueOf(255f / 255f, 193f / 255f, 7f / 255f, 1f).toArgb()        // Amber for Best Time
        val makeTimeColor = Color.valueOf(244f / 255f, 67f / 255f, 54f / 255f, 1f).toArgb()        // Red for Make Time
        
        // Android 16 Progress-Centric: Fixed segment colors for prayer urgency progression
        // Each segment always has its designated color: Green → Yellow → Red
        val segments = listOf(
            ProgressStyle.Segment(20).setColor(greenSegmentColor),    // Segment 1: Go to Mosque (Green)
            ProgressStyle.Segment(40).setColor(yellowSegmentColor),   // Segment 2: Best Time (Yellow)
            ProgressStyle.Segment(40).setColor(redSegmentColor)       // Segment 3: Make Time (Red)
        )

        // Android 16 Progress-Centric: Create meaningful progress points with proper milestone colors
        // Tracker positioned at exact progress point (no icon on tracker)
        val progressStyle = NotificationCompat.ProgressStyle()
            .setProgressPoints(
                listOf(
                    ProgressStyle.Point(20).setColor(mosquePhaseColor),   // Go to Mosque milestone (0-20%)
                    ProgressStyle.Point(60).setColor(bestTimeColor),     // Best Time milestone (20-60%)
                    ProgressStyle.Point(100).setColor(makeTimeColor)     // Make Time milestone (60-100%)
                )
            )
            .setProgressSegments(segments)  // Dynamic segment coloring
            .setProgress(progress)  // Tracker positioned at exact progress point
        
        val phaseName = when (newPhase) {
            0 -> "Go to Mosque"
            1 -> "Best Time"
            2 -> "Make Time"
            else -> "Unknown"
        }
        android.util.Log.d("GoogleSampleNotificationManager", 
            "📊 Progress Details: ${progress}% actual | Phase: ${newPhase} ($phaseName) | Prayer Phase: '$prayerPhase'")
        android.util.Log.d("GoogleSampleNotificationManager", 
            "🎯 Tracker Position: Positioned at exact progress point (${progress}%)")
        android.util.Log.d("GoogleSampleNotificationManager", 
            "🎨 Segment Colors: Green (Go to Mosque) → Yellow (Best Time) → Red (Make Time)")
        
        // Get activity emoji for AOD status chip
        val currentActivity = ActivityTracker.getCurrentActivity()
        val activityEmoji = when {
            currentActivity.contains("Driving", ignoreCase = true) -> "🚗"
            currentActivity.contains("Walking", ignoreCase = true) -> "🚶"
            currentActivity.contains("Running", ignoreCase = true) -> "🏃"
            currentActivity.contains("Phone", ignoreCase = true) -> "📱"
            currentActivity.contains("Still", ignoreCase = true) -> "🧍"
            else -> "🧍"
        }

        // Android 16 Status Chip: Text must be under 7 characters for full display
        // Per documentation: < 7 chars = full text, < 50% fits = icon only
        // Max chip width is 96dp
        //
        // Extract actual countdown from nextPrayerCountdown or content
        // Format: "Next • Asr in 2h 56m" or "Dhuhr in 45m"
        // Priority: Show hours if exists (e.g., "Asr2h"), otherwise minutes (e.g., "Dhr45m")
        val textToSearch = if (nextPrayerCountdown.isNotBlank()) nextPrayerCountdown else content

        // Extract hours if present: "2h" from "2h 56m"
        val hoursRegex = Regex("""(\d+)\s*h""")
        val hoursMatch = hoursRegex.find(textToSearch)
        val hours = hoursMatch?.groupValues?.get(1)?.toIntOrNull()

        // Extract minutes: "56m" or "45m"
        val minutesRegex = Regex("""(\d+)\s*m""")
        val minutesMatch = minutesRegex.find(textToSearch)
        val minutes = minutesMatch?.groupValues?.get(1)?.toIntOrNull()

        // Get short prayer name for chip (3 chars max)
        // Look for NEXT prayer in the countdown text
        val nextPrayerShort = when {
            textToSearch.contains("Fajr", ignoreCase = true) -> "Fjr"
            textToSearch.contains("Dhuhr", ignoreCase = true) -> "Dhr"
            textToSearch.contains("Asr", ignoreCase = true) -> "Asr"
            textToSearch.contains("Maghrib", ignoreCase = true) -> "Mgb"
            textToSearch.contains("Isha", ignoreCase = true) -> "Ish"
            else -> "Nxt"
        }

        // Show time in most accurate format under 7 char limit
        // - Hours with minutes >= 30: round UP (e.g., 1h 50m → "Mgb2h")
        // - Hours with minutes < 30: round DOWN (e.g., 1h 20m → "Mgb1h")
        // - Minutes only: show "Xm" (e.g., "Dhr45m")
        val shortCriticalText = when {
            hours != null && hours > 0 -> {
                // Round to nearest hour: >= 30 mins rounds up
                val roundedHours = if (minutes != null && minutes >= 30) hours + 1 else hours
                "$nextPrayerShort${roundedHours}h"  // e.g., "Mgb2h" for 1h50m
            }
            minutes != null && minutes > 0 -> "$nextPrayerShort${minutes}m"  // e.g., "Dhr45m"
            else -> {
                // Fallback to phase-based display if no countdown available
                when {
                    progress <= 20 -> "Mo${20 - progress}m"
                    progress <= 60 -> "Bst${60 - progress}m"
                    else -> "Pry${100 - progress}m"
                }
            }
        }

        android.util.Log.d("GoogleSampleNotificationManager",
            "📊 Status Chip: '$shortCriticalText' (${shortCriticalText.length} chars) | Hours: $hours | Minutes: $minutes | From: '$textToSearch'")
        
        // Android 16 Progress-Centric: Combine content for clear journey communication
        val fullContent = if (detailedMessage.isNotEmpty()) {
            "$content\n$detailedMessage"
        } else {
            content
        }
        
        // Check if this prayer is already marked as prayed
        val isPrayerAlreadyMarked = if (currentPrayerName.isNotEmpty()) {
            PrayerTracker.isPrayerMarkedToday(currentPrayerName)
        } else {
            false
        }

        // Create "Mark as Prayed" action button only if prayer is NOT already marked
        val markAsPrayedAction = if (!isPrayerAlreadyMarked) {
            createMarkAsPrayedAction()
        } else {
            android.util.Log.d("GoogleSampleNotificationManager",
                "✅ Prayer '$currentPrayerName' already marked - hiding 'Mark as Prayed' button")
            null
        }

        // Get activity icon for notification large icon (top right corner)
        val activityIconBitmap = getActivityIconBitmap()

        // Android 16 Progress-Centric: Build notification with recommended practices
        val notificationBuilder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_prayer)
            .setContentTitle(title)  // Clear state of journey
            .setContentText(fullContent)  // Time information and next step
            .setStyle(progressStyle)  // Progress-centric style
            .setOngoing(true)
            .setRequestPromotedOngoing(true)  // Enable Live Updates
            .setShortCriticalText(shortCriticalText)  // Status chip text for status bar (< 7 chars)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)  // Appropriate category
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)  // Public visibility for lock screen
            .setShowWhen(true)  // Show timestamp on notification

        // No countdown timer - just show current time
        // Status chip will display shortCriticalText (e.g., "Asr2h") without live countdown
        notificationBuilder.setWhen(System.currentTimeMillis())
        notificationBuilder.setUsesChronometer(false)

        // Add "Mark as Prayed" action button only if prayer is NOT already marked
        if (markAsPrayedAction != null) {
            notificationBuilder.addAction(markAsPrayedAction)
        }

        // Add activity icon as large icon (shows in top right corner of notification)
        if (activityIconBitmap != null) {
            notificationBuilder.setLargeIcon(activityIconBitmap)
            android.util.Log.d("GoogleSampleNotificationManager", "🎨 Added large activity icon: ${ActivityTracker.getCurrentActivity()}")
        }
        
        // Apply intelligent alert behavior: Only alert on phase changes, silent for progress updates
        if (shouldAlert) {
            // Phase change or first notification: Allow normal alert behavior (sound/vibration)
            android.util.Log.d("GoogleSampleNotificationManager", "🔔 Phase change detected - allowing alerts (sound/vibration)")

            // Play Adhan sound ONLY when prayer time has just arrived (not when service starts)
            // We need to check:
            // 1. We're at the beginning of prayer time (progress 0-5%)
            // 2. This is a NEW prayer (not the same one we already played Adhan for)
            // 3. We're entering phase 0 (Go to Mosque)

            val isNearPrayerTime = progress in 0..5  // Within first 5% of prayer window

            // Read last Adhan info from persistent storage
            val lastAdhanPrayerName = prefs.getString(KEY_LAST_ADHAN_PRAYER_NAME, "") ?: ""
            val lastAdhanPrayerTime = prefs.getString(KEY_LAST_ADHAN_PRAYER_TIME, "") ?: ""
            val lastAdhanPlayedTime = prefs.getLong(KEY_LAST_ADHAN_PLAYED_TIME, 0L)

            val isNewPrayer = (currentPrayerName != lastAdhanPrayerName || currentPrayerTime != lastAdhanPrayerTime)
            val timeSinceLastAdhan = System.currentTimeMillis() - lastAdhanPlayedTime
            val isEnoughTimePassedSinceLastAdhan = timeSinceLastAdhan > 60_000  // At least 1 minute since last Adhan

            // CRITICAL: Don't play Adhan within 2 minutes of app startup - PERIOD!
            val timeSinceAppStart = System.currentTimeMillis() - appInitializedTime
            val isAppRunningLongEnough = timeSinceAppStart > 120_000  // App must be running for at least 2 minutes

            // ABSOLUTE BLOCK: Never play Adhan in the first 2 minutes, no matter what
            val isStartupProtectionActive = timeSinceAppStart < 120_000
            if (isStartupProtectionActive) {
                android.util.Log.d("GoogleSampleNotificationManager",
                    "🚫 ADHAN BLOCKED: App startup protection active (${timeSinceAppStart/1000}s < 120s)")
            }

            // IMPORTANT: Never play Adhan on the very first notification after app start
            // This prevents Adhan when opening the app during prayer time
            val isVeryFirstNotification = isFirstNotification && timeSinceAppStart < 5000  // First notification within 5 seconds

            // Only play Adhan if ALL conditions are met:
            // - NOT in startup protection period (first 2 minutes)
            // - NOT the first notification (prevents startup Adhan)
            // - App has been running for at least 2 minutes (prevents startup Adhan)
            // - We're near the actual prayer time (0-5% progress means prayer just started)
            // - This is a new prayer (different from last one we played Adhan for)
            // - We're in phase 0 (Go to Mosque phase)
            // - It's been at least 1 minute since last Adhan (prevents spam)
            val shouldPlayAdhan = !isStartupProtectionActive &&
                                  !isVeryFirstNotification &&
                                  isAppRunningLongEnough &&
                                  isNearPrayerTime &&
                                  isNewPrayer &&
                                  newPhase == 0 &&
                                  isEnoughTimePassedSinceLastAdhan

            if (shouldPlayAdhan) {
                android.util.Log.d("GoogleSampleNotificationManager",
                    "📢 ADHAN TIME: New prayer '${currentPrayerName}' at ${currentPrayerTime} (progress: ${progress}%) - playing Adhan")
                playAdhanSound()

                // Save this prayer to persistent storage so we don't play Adhan again for it even after app restart
                val currentTime = System.currentTimeMillis()
                prefs.edit().apply {
                    putString(KEY_LAST_ADHAN_PRAYER_NAME, currentPrayerName)
                    putString(KEY_LAST_ADHAN_PRAYER_TIME, currentPrayerTime)
                    putLong(KEY_LAST_ADHAN_PLAYED_TIME, currentTime)
                    apply()
                }
                android.util.Log.d("GoogleSampleNotificationManager",
                    "💾 Saved to persistent storage: Prayer='${currentPrayerName}' Time='${currentPrayerTime}'")
            } else if (newPhase == 0) {
                // Log why we're not playing Adhan
                val reason = when {
                    isStartupProtectionActive -> "🚫 STARTUP PROTECTION: App running for ${timeSinceAppStart/1000}s (need 120s)"
                    isVeryFirstNotification -> "First notification after app start - NEVER play Adhan on startup"
                    !isAppRunningLongEnough -> "App just started (${timeSinceAppStart/1000}s ago) - waiting 120s before allowing Adhan"
                    !isNearPrayerTime -> "Already past prayer time (progress: ${progress}%)"
                    !isNewPrayer -> "Already played Adhan for ${currentPrayerName} at ${currentPrayerTime}"
                    !isEnoughTimePassedSinceLastAdhan -> "Too soon since last Adhan (${timeSinceLastAdhan/1000}s ago)"
                    else -> "Unknown reason"
                }
                android.util.Log.d("GoogleSampleNotificationManager",
                    "⏭️ Skipping Adhan: $reason")
            }

            // CRITICAL FIX: Always silence notification channel sound to prevent unwanted adhan playback
            // We only use MediaPlayer (playAdhanSound()) for adhan - never the notification channel sound
            // This prevents the channel's default sound from playing during startup or when adhan shouldn't play
            notificationBuilder.setSilent(true)
            android.util.Log.d("GoogleSampleNotificationManager", "🔇 Notification channel sound silenced - only MediaPlayer used for Adhan")

            // DISABLED: Notification sound to prevent double Adhan
            // The playAdhanSound() method above already plays the Adhan when conditions are met
            // Having both causes double playback
            // val adhanSoundUri = Uri.parse("android.resource://${appContext.packageName}/${R.raw.short_adhan}")
            // notificationBuilder.setSound(adhanSoundUri)
        } else {
            // Progress update within same phase: Make completely silent
            android.util.Log.d("GoogleSampleNotificationManager", "🔕 Progress update - setting notification as SILENT (no sound/vibration)")
            notificationBuilder.setSilent(true)  // Suppress sound and vibration for progress updates
        }
        
        val notification = notificationBuilder.build()

        android.util.Log.d("GoogleSampleNotificationManager", "Built Progress-Centric prayer notification: $title - $shortCriticalText ($progress%)")
        return notification
    }

    /**
     * Legacy method that posts notification directly via notificationManager.notify()
     * WARNING: This method removes FOREGROUND_SERVICE flag and should NOT be used
     * for foreground service notifications. Use buildPrayerNotification() instead.
     *
     * @deprecated Use buildPrayerNotification() and startForeground() to maintain foreground service priority
     */
    @Deprecated("Use buildPrayerNotification() with startForeground() to maintain foreground service priority")
    @RequiresApi(35)
    fun postPrayerNotification(title: String, content: String, detailedMessage: String = "", progress: Int, prayerPhase: String = "", prayerName: String = "", prayerTime: String = "") {
        android.util.Log.w("GoogleSampleNotificationManager", "⚠️ Using deprecated postPrayerNotification - this removes FOREGROUND_SERVICE flag!")
        val notification = buildPrayerNotification(title, content, detailedMessage, progress, prayerPhase, prayerName, prayerTime)
        notificationManager.notify(NOTIFICATION_ID, notification)
        android.util.Log.d("GoogleSampleNotificationManager", "Posted notification via notify() - foreground priority may be lost!")
    }
    
    /**
     * Creates the "Mark as Prayed" action button for the notification
     * 
     * @return NotificationCompat.Action configured for prayer marking
     */
    private fun createMarkAsPrayedAction(): NotificationCompat.Action {
        // Create intent for the PrayerActionReceiver
        val markAsPrayedIntent = Intent(appContext, PrayerActionReceiver::class.java).apply {
            action = PrayerActionReceiver.ACTION_MARK_AS_PRAYED
            // Pass current prayer information to the receiver
            putExtra(PrayerActionReceiver.EXTRA_PRAYER_NAME, currentPrayerName)
            putExtra(PrayerActionReceiver.EXTRA_PRAYER_TIME, currentPrayerTime)
            putExtra(PrayerActionReceiver.EXTRA_TIMESTAMP, System.currentTimeMillis())
        }
        
        // Create PendingIntent with unique request code to avoid conflicts
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            NOTIFICATION_ID, // Use notification ID as request code for uniqueness
            markAsPrayedIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Create the action with prayer icon and appropriate text
        val action = NotificationCompat.Action.Builder(
            R.drawable.ic_prayer, // Use prayer icon for the action
            "Mark as Prayed", // Action button text
            pendingIntent
        ).build()
        
        android.util.Log.d("GoogleSampleNotificationManager", 
            "📿 Created 'Mark as Prayed' action for prayer: $currentPrayerName at $currentPrayerTime")
        
        return action
    }
    
    /**
     * Updates notification to reflect prayer completion (optional feature)
     * You can call this from PrayerActionReceiver to update the notification after marking prayer as completed
     */
    @RequiresApi(35)
    fun updateNotificationForCompletedPrayer(prayerName: String) {
        // 🚧 TODO: CUSTOMIZE THIS FOR YOUR NEEDS 🚧
        
        android.util.Log.d("GoogleSampleNotificationManager", "🕌 Updating notification for completed prayer: $prayerName")
        
        // Option 1: Dismiss the notification entirely
        // dismissNotification()
        
        // Option 2: Update notification to show completion status
        val completionNotification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_prayer)
            .setContentTitle("Prayer Completed ✅")
            .setContentText("$prayerName has been marked as completed")
            .setAutoCancel(true) // Allow user to dismiss by tapping
            .setOngoing(false) // Not ongoing anymore
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
            
        notificationManager.notify(NOTIFICATION_ID, completionNotification)
        
        android.util.Log.d("GoogleSampleNotificationManager", "✅ Notification updated for completed prayer: $prayerName")
    }
    
    /**
     * Dismisses the current prayer notification
     * You can call this from PrayerActionReceiver to remove the notification after prayer completion
     */
    fun dismissNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
        android.util.Log.d("GoogleSampleNotificationManager", "🗑️ Prayer notification dismissed")
    }

    /**
     * Clear the stored Adhan history (useful for testing or resetting)
     * This allows the Adhan to play again for the current prayer
     */
    fun clearAdhanHistory() {
        if (::prefs.isInitialized) {
            prefs.edit().apply {
                remove(KEY_LAST_ADHAN_PRAYER_NAME)
                remove(KEY_LAST_ADHAN_PRAYER_TIME)
                remove(KEY_LAST_ADHAN_PLAYED_TIME)
                apply()
            }
            android.util.Log.d("GoogleSampleNotificationManager",
                "🗑️ Cleared Adhan history from persistent storage")
        }
    }

    /**
     * Get info about the last played Adhan for debugging
     */
    fun getLastAdhanInfo(): String {
        if (!::prefs.isInitialized) return "Preferences not initialized"

        val lastPrayerName = prefs.getString(KEY_LAST_ADHAN_PRAYER_NAME, "None") ?: "None"
        val lastPrayerTime = prefs.getString(KEY_LAST_ADHAN_PRAYER_TIME, "Never") ?: "Never"
        val lastPlayedTime = prefs.getLong(KEY_LAST_ADHAN_PLAYED_TIME, 0L)

        return if (lastPlayedTime > 0) {
            val timeAgo = (System.currentTimeMillis() - lastPlayedTime) / 1000 / 60  // minutes
            "Last Adhan: $lastPrayerName at $lastPrayerTime (${timeAgo}m ago)"
        } else {
            "No Adhan played yet"
        }
    }

    /**
     * Get resource ID for current detected activity icon (colored, for large icon)
     * Returns appropriate icon resource ID based on: Still, Walking, Running, Driving, On Phone
     */
    private fun getActivityIconResId(): Int {
        val currentActivity = ActivityTracker.getCurrentActivity()
        return when {
            currentActivity.contains("Driving", ignoreCase = true) -> R.drawable.ic_activity_driving
            currentActivity.contains("Walking", ignoreCase = true) -> R.drawable.ic_activity_walking
            currentActivity.contains("Running", ignoreCase = true) -> R.drawable.ic_activity_running
            currentActivity.contains("Phone", ignoreCase = true) -> R.drawable.ic_activity_phone
            currentActivity.contains("Still", ignoreCase = true) -> R.drawable.ic_activity_still
            else -> R.drawable.ic_activity_still // Default to still
        }
    }

    /**
     * Get resource ID for current detected activity SMALL icon (monochrome, for AOD)
     * Returns appropriate monochrome icon for notification small icon
     */
    private fun getActivitySmallIconResId(): Int {
        val currentActivity = ActivityTracker.getCurrentActivity()
        return when {
            currentActivity.contains("Driving", ignoreCase = true) -> R.drawable.ic_notif_driving
            currentActivity.contains("Walking", ignoreCase = true) -> R.drawable.ic_notif_walking
            currentActivity.contains("Running", ignoreCase = true) -> R.drawable.ic_notif_running
            currentActivity.contains("Phone", ignoreCase = true) -> R.drawable.ic_notif_phone
            currentActivity.contains("Still", ignoreCase = true) -> R.drawable.ic_notif_still
            else -> R.drawable.ic_notif_still // Default to still
        }
    }

    /**
     * Get bitmap icon for current detected activity
     * Returns appropriate icon based on: Still, Walking, Running, Driving, On Phone
     */
    private fun getActivityIconBitmap(): Bitmap? {
        return try {
            val iconResId = getActivityIconResId()

            // Convert vector drawable to bitmap
            val drawable = ResourcesCompat.getDrawable(appContext.resources, iconResId, null)
            drawable?.let { vectorToBitmap(it) }
        } catch (e: Exception) {
            android.util.Log.e("GoogleSampleNotificationManager", "Error getting activity icon: ${e.message}")
            null
        }
    }

    /**
     * Convert vector drawable to bitmap for notification large icon
     * Icon is drawn at 1/2 size centered in the canvas with padding around it
     * This makes the icon appear smaller since Android scales the entire bitmap to fit
     */
    private fun vectorToBitmap(drawable: Drawable): Bitmap {
        // Keep full canvas size but draw icon at 1/2 size centered
        val canvasSize = drawable.intrinsicWidth.coerceAtLeast(drawable.intrinsicHeight).coerceAtLeast(1)
        val iconSize = (canvasSize / 2)
        val padding = (canvasSize - iconSize) / 2

        val bitmap = Bitmap.createBitmap(
            canvasSize,
            canvasSize,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        // Draw icon centered with padding
        drawable.setBounds(padding, padding, padding + iconSize, padding + iconSize)
        drawable.draw(canvas)
        return bitmap
    }

    /**
     * Plays the Adhan sound using MediaPlayer
     * This is called when prayer time arrives (entering "Go to Mosque" phase)
     */
    private fun playAdhanSound() {
        try {
            val adhanUri = Uri.parse("android.resource://${appContext.packageName}/${R.raw.short_adhan}")
            val mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                )
                setDataSource(appContext, adhanUri)
                prepareAsync()
                setOnPreparedListener { mp ->
                    mp.start()
                    android.util.Log.d("GoogleSampleNotificationManager", "🔊 Playing Adhan sound for prayer notification")
                }
                setOnCompletionListener { mp ->
                    mp.release()
                    android.util.Log.d("GoogleSampleNotificationManager", "✅ Adhan sound playback completed")
                }
                setOnErrorListener { mp, what, extra ->
                    android.util.Log.e("GoogleSampleNotificationManager", "❌ Error playing Adhan: what=$what, extra=$extra")
                    mp.release()
                    false
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("GoogleSampleNotificationManager", "❌ Failed to play Adhan sound", e)
        }
    }
}