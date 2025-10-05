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
import android.content.Context
import androidx.core.app.NotificationCompat.ProgressStyle
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.starception.submission.R
import java.util.logging.Level
import java.util.logging.Logger

object GoogleSampleNotificationManager {
    private lateinit var notificationManager: NotificationManager
    private lateinit var appContext: Context
    const val CHANNEL_ID = "google_live_updates_channel_id"
    private const val CHANNEL_NAME = "Google Live Updates Test"
    private const val NOTIFICATION_ID = 9999

    @RequiresApi(Build.VERSION_CODES.O)
    fun initialize(context: Context, notifManager: NotificationManager) {
        notificationManager = notifManager
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, IMPORTANCE_DEFAULT)
        appContext = context
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
    fun postPrayerNotification(title: String, content: String, detailedMessage: String = "", progress: Int) {
        android.util.Log.d("GoogleSampleNotificationManager", "Posting prayer notification with Google's Live Update system...")
        
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
        
        // Define distinct colors for each prayer phase milestone
        val mosquePhaseColor = Color.valueOf(46f / 255f, 125f / 255f, 50f / 255f, 1f).toArgb()    // Green for Go to Mosque
        val bestTimeColor = Color.valueOf(255f / 255f, 193f / 255f, 7f / 255f, 1f).toArgb()        // Amber for Best Time
        val makeTimeColor = Color.valueOf(244f / 255f, 67f / 255f, 54f / 255f, 1f).toArgb()        // Red for Make Time
        
        // Choose appropriate tracker icon based on current progress phase
        val trackerIcon = when {
            progress <= 20 -> IconCompat.createWithResource(appContext, R.drawable.ic_mosque_milestone)      // Go to Mosque
            progress <= 60 -> IconCompat.createWithResource(appContext, R.drawable.ic_prayer_mat_milestone)  // Best Time to Pray
            else -> IconCompat.createWithResource(appContext, R.drawable.ic_clock_milestone)                 // Make Time for Prayer
        }
        
        // 3-segment prayer phases with colored milestone points and meaningful tracker icon
        val progressStyle = NotificationCompat.ProgressStyle()
            .setProgressPoints(
                listOf(
                    ProgressStyle.Point(20).setColor(mosquePhaseColor),   // Go to Mosque - Green milestone
                    ProgressStyle.Point(60).setColor(bestTimeColor),     // Best Time - Amber milestone  
                    ProgressStyle.Point(100).setColor(makeTimeColor)     // Make Time - Red milestone
                )
            ).setProgressSegments(
                listOf(
                    ProgressStyle.Segment(20).setColor(segmentColor),  // Go to Mosque (0-20%)
                    ProgressStyle.Segment(40).setColor(segmentColor),  // Best Time to Pray (20-60%)
                    ProgressStyle.Segment(40).setColor(segmentColor)   // Make Time for Prayer (60-100%)
                )
            ).setProgressTrackerIcon(trackerIcon)  // Dynamic tracker icon that moves along the progress bar
            .setProgress(progress)
        
        // Combine content and detailed message for complete prayer information
        val fullContent = if (detailedMessage.isNotEmpty()) {
            "$content\n$detailedMessage"
        } else {
            content
        }
        
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_prayer)
            .setContentTitle(title)
            .setContentText(fullContent)
            .setStyle(progressStyle) // Keep progress style for Live Updates
            .setOngoing(true)
            .setRequestPromotedOngoing(true) // CRITICAL: This enables Live Updates!
            .build()
        
        notificationManager.notify(NOTIFICATION_ID, notification)
        android.util.Log.d("GoogleSampleNotificationManager", "Posted prayer notification: $title - $fullContent ($progress%)")
    }
}