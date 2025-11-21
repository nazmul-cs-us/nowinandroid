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

object GoogleSampleNotificationManager {
    private lateinit var notificationManager: NotificationManager
    private lateinit var appContext: Context
    const val CHANNEL_ID = "google_live_updates_channel_id"
    private const val CHANNEL_NAME = "Google Live Updates Test"
    private const val NOTIFICATION_ID = 9999
    
    // Track current prayer phase to detect phase changes
    private var currentPhase: Int = -1 // -1 = not set, 0 = Go to Mosque, 1 = Best Time, 2 = Make Time
    
    // Track current prayer information for action buttons
    private var currentPrayerName: String = ""
    private var currentPrayerTime: String = ""

    @RequiresApi(Build.VERSION_CODES.O)
    fun initialize(context: Context, notifManager: NotificationManager) {
        notificationManager = notifManager
        appContext = context

        // Create audio attributes for notification sound
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .build()

        // Get the URI for the adhan sound
        val adhanSoundUri = Uri.parse("android.resource://${context.packageName}/${R.raw.short_adhan}")

        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, IMPORTANCE_DEFAULT).apply {
            setSound(adhanSoundUri, audioAttributes)
        }
        notificationManager.createNotificationChannel(channel)
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
    
    @RequiresApi(35) // Android 16+ (BAKLAVA = 35)
    fun postPrayerNotification(title: String, content: String, detailedMessage: String = "", progress: Int, prayerPhase: String = "", prayerName: String = "", prayerTime: String = "") {
        android.util.Log.d("GoogleSampleNotificationManager", "Posting Progress-Centric prayer notification...")
        
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
        // Tracker will automatically position at the exact progress point (no icon)
        val progressStyle = NotificationCompat.ProgressStyle()
            .setProgressPoints(
                listOf(
                    ProgressStyle.Point(20).setColor(mosquePhaseColor),   // Go to Mosque milestone (0-20%)
                    ProgressStyle.Point(60).setColor(bestTimeColor),     // Best Time milestone (20-60%)
                    ProgressStyle.Point(100).setColor(makeTimeColor)     // Make Time milestone (60-100%)
                )
            )
            .setProgressSegments(segments)  // Dynamic segment coloring
            .setProgress(progress)  // Tracker positioned at exact progress point (no icon)
        
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
        
        // Android 16 Progress-Centric: Create concise, clear status text for status chip
        val shortCriticalText = when {
            progress <= 20 -> "🕌 Go to Mosque"
            progress <= 60 -> "🧎 Best Time"
            else -> "⏰ Make Time"
        }
        
        // Android 16 Progress-Centric: Combine content for clear journey communication
        val fullContent = if (detailedMessage.isNotEmpty()) {
            "$content\n$detailedMessage"
        } else {
            content
        }
        
        // Create "Mark as Prayed" action button
        val markAsPrayedAction = createMarkAsPrayedAction()
        
        // Android 16 Progress-Centric: Build notification with recommended practices
        val notificationBuilder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_prayer)
            .setContentTitle(title)  // Clear state of journey
            .setContentText(fullContent)  // Time information and next step
            .setStyle(progressStyle)  // Progress-centric style
            .setOngoing(true)
            .setRequestPromotedOngoing(true)  // Enable Live Updates
            .setShortCriticalText(shortCriticalText)  // Status chip text for status bar
            .setUsesChronometer(false)  // Don't use chronometer for prayer tracking
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)  // Appropriate category
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)  // Public visibility for lock screen
            .addAction(markAsPrayedAction)  // Add "Mark as Prayed" action button
        
        // Apply intelligent alert behavior: Only alert on phase changes, silent for progress updates
        if (shouldAlert) {
            // Phase change or first notification: Allow normal alert behavior (sound/vibration)
            android.util.Log.d("GoogleSampleNotificationManager", "🔔 Phase change detected - allowing alerts (sound/vibration)")

            // Play Adhan sound when entering "Go to Mosque" phase (prayer time has arrived)
            if (newPhase == 0 && (isPhaseChange || isFirstNotification)) {
                playAdhanSound()
            }

            // Set custom sound URI for the notification
            val adhanSoundUri = Uri.parse("android.resource://${appContext.packageName}/${R.raw.short_adhan}")
            notificationBuilder.setSound(adhanSoundUri)
        } else {
            // Progress update within same phase: Make completely silent
            android.util.Log.d("GoogleSampleNotificationManager", "🔕 Progress update - setting notification as SILENT (no sound/vibration)")
            notificationBuilder.setSilent(true)  // Suppress sound and vibration for progress updates
        }
        
        val notification = notificationBuilder.build()
        
        notificationManager.notify(NOTIFICATION_ID, notification)
        android.util.Log.d("GoogleSampleNotificationManager", "Posted Progress-Centric prayer notification: $title - $shortCriticalText ($progress%)")
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