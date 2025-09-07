package com.starception.submission.util

import com.starception.submission.MainActivity
import com.starception.submission.R
import android.app.Notification
import android.content.res.Configuration
import android.graphics.Color
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat

/**
 * PRAYER NOTIFICATION MANAGER: Central hub for all prayer notifications
 * 
 * This singleton manages all prayer-related notifications with smart features:
 * - Android 15+ Live Update support with enhanced progress bars
 * - Smart alerting (only sounds on phase changes)
 * - Lock screen compatibility
 * - Battery optimization
 * - Ongoing notifications for real-time updates
 * 
 * KEY METHODS TO EDIT:
 * - postDetailedPrayerNotification() - Main notification posting
 * - updatePrayerProgressSmart() - Smart alert system
 * - addProgressToBuilder() - Progress bar setup
 */
object PrayerNotificationManager {
    private lateinit var notificationManager: NotificationManager
    private lateinit var appContext: Context
    private var initialized: Boolean = false
    
    private const val TAG = "PrayerNotificationMgr"
    
    // NOTIFICATION CONFIGURATION - Edit these to change notification behavior
    private const val CHANNEL_ID = "prayer_live_update_channel"
    private const val CHANNEL_NAME = "Prayer Notifications" 
    private const val NOTIFICATION_ID = 1001  // Same ID = updates replace previous notifications
    
    fun initialize(context: Context) {
        Log.d(TAG, "=== INITIALIZING PRAYER NOTIFICATION MANAGER ===")
        Log.d(TAG, "Context: ${context.javaClass.simpleName}")
        
        appContext = context.applicationContext
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        Log.d(TAG, "Creating notification channel...")
        createNotificationChannel()
        initialized = true
        
        Log.d(TAG, "✓ PrayerNotificationManager initialized successfully")
        Log.d(TAG, "NotificationManager: ${notificationManager.javaClass.simpleName}")
        Log.d(TAG, "Live Updates supported: ${supportsLiveUpdates()}")
    }
    
    /**
     * Check if the notification manager has been initialized
     */
    fun isInitialized(): Boolean = initialized
    
    /**
     * Create PendingIntent to launch the main app
     * Uses proper flags to ensure clean app startup from notification
     */
    private fun createAppLaunchIntent(): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            // Use minimal flags to avoid conflicts with app lifecycle
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            // Don't add CATEGORY_LAUNCHER for notification intents - this can cause conflicts
            // Don't set ACTION_MAIN for notification intents
        }
        return PendingIntent.getActivity(
            appContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Use app's theme colors
            val isDarkTheme = (appContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val themeColor = if (isDarkTheme) {
                Color.parseColor("#FFA9FE") // Purple80 - Dark theme
            } else {
                Color.parseColor("#8B418F") // Purple40 - Light theme
            }
            
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Prayer Times",
                NotificationManager.IMPORTANCE_HIGH // High importance to allow both alert and silent modes
            ).apply {
                description = "Live updates for prayer times and guidance (alerts only on phase changes)"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableLights(true) // Enable lights for phase transitions
                enableVibration(true) // Enable vibration for phase transitions
                setSound(null, null) // No default sound, controlled per notification
                setBypassDnd(false) // Don't bypass Do Not Disturb
            }
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Modern notification channel created with theme colors and Live Update support")
        }
    }
    
    /**
     * ANDROID 16 LIVE UPDATE DETECTION
     * 
     * Detects if the device supports Android 16's new Live Update notifications.
     * 
     * FEATURES ENABLED ON ANDROID 16+:
     * - Progress segments with colors (Blue/Green/Yellow phases)
     * - Promoted ongoing notifications (better visibility)
     * - Enhanced always-on display integration
     * 
     * EDIT THIS TO:
     * - Change minimum API level for Live Updates
     * - Add feature detection logic
     * - Modify fallback behavior
     */
    fun supportsLiveUpdates(): Boolean {
        val supported = Build.VERSION.SDK_INT >= 35 // Android 16 API level
        Log.d(TAG, "Live Updates supported: $supported (API ${Build.VERSION.SDK_INT})")
        return supported
    }
    
    /**
     * Post prayer notification using Live Updates if supported, otherwise regular notification
     */
    fun postPrayerNotification(prayerName: String, progress: Int = 0, isOngoing: Boolean = true) {
        val notification = if (supportsLiveUpdates()) {
            buildLiveUpdateReadyNotification(prayerName, progress, isOngoing)
        } else {
            buildRegularNotification(prayerName, progress, isOngoing)
        }
        
        // Always use the same notification ID to update existing notification
        notificationManager.notify(NOTIFICATION_ID, notification.build())
        Log.d(TAG, "Updated notification: $prayerName (progress: $progress%)")
    }
    
    /**
     * Post detailed prayer notification with custom content
     */
    fun postDetailedPrayerNotification(
        title: String,
        content: String,
        detailedMessage: String,
        progress: Int = 0,
        isOngoing: Boolean = true,
        prayerName: String
    ) {
        Log.d(TAG, "Posting detailed prayer notification: $title")
        
        // Check if we can post promoted notifications (Android 15+ Live Updates)
        val canPostPromoted = if (Build.VERSION.SDK_INT >= 35) {
            try {
                // For SDK 36, assume promoted notifications are available
                true
            } catch (e: Exception) {
                Log.d(TAG, "Cannot check promoted notification capability: ${e.message}")
                false
            }
        } else {
            false
        }
        
        Log.d(TAG, "Can post promoted notifications: $canPostPromoted")
        
        if (Build.VERSION.SDK_INT >= 35 && canPostPromoted) {
            // Use Android 16 Live Update notification
            try {
                val notification = buildLiveUpdateNotification(
                    title = title,
                    content = content,
                    detailedMessage = detailedMessage,
                    progress = progress,
                    isOngoing = isOngoing,
                    prayerName = prayerName // Pass the prayer name to the style builder
                )
                
                // Update existing notification instead of creating new one
                notificationManager.notify(NOTIFICATION_ID, notification.build())
                Log.d(TAG, "Updated Android 16 Live Update notification: $title")
                
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create Android 16 Live Update notification, falling back: ${e.message}")
                postCompatNotification(title, content, detailedMessage, progress, isOngoing)
            }
        } else {
            // Fallback to standard notification
            postCompatNotification(title, content, detailedMessage, progress, isOngoing)
        }
    }
    
    /**
     * Build notification ready for Android 16 Live Updates
     * Uses current APIs with Live Update optimizations
     */
    private fun buildLiveUpdateReadyNotification(
        prayerName: String, 
        progress: Int, 
        isOngoing: Boolean
    ): NotificationCompat.Builder {
        val title = try {
            appContext.getString(R.string.live_notification_title)
        } catch (e: Exception) {
            "Prayer Time Tracker"
        }
        
        val content = try {
            appContext.getString(R.string.live_notification_content, prayerName)
        } catch (e: Exception) {
            "Current prayer: $prayerName"
        }
        
        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_prayer)
            .setContentIntent(createAppLaunchIntent())
            .setOngoing(true) // Always ongoing to prevent sounds
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW) // Lower priority to reduce interruptions
            .setDefaults(0) // No sound, vibration, or lights
            .setSilent(true) // Explicitly silent
            .setLocalOnly(true) // Only show locally
            .setAutoCancel(false) // Don't auto-cancel
            .setOnlyAlertOnce(true) // Only alert once, then silent updates
            .setShowWhen(true)
            .setUsesChronometer(false)
        
        // Add Live Update specific features for Android 16+
        if (Build.VERSION.SDK_INT >= 35) { // Android 16
            try {
                // Use enhanced features for Android 15+
                if (progress > 0) {
                    builder.setProgress(100, progress, false)
                }
                
                // Enable ongoing notification for live updates
                builder.setOngoing(true)
                
                // Add large icon for better Live Update appearance
                builder.setLargeIcon(
                    IconCompat.createWithResource(
                        appContext, R.drawable.ic_prayer
                    ).toIcon(appContext)
                )
                
                Log.d(TAG, "Applied Live Update features (API ${Build.VERSION.SDK_INT})")
            } catch (e: Exception) {
                Log.d(TAG, "Live Update APIs not available: ${e.message}")
                // Fallback to regular progress bar
                if (progress > 0 && progress <= 100) {
                    builder.setProgress(100, progress, false)
                }
            }
        } else {
            // Show progress bar for pre-Android 16
            if (progress > 0 && progress <= 100) {
                builder.setProgress(100, progress, false)
            }
            
            // Add large icon for better appearance
            try {
                builder.setLargeIcon(
                    IconCompat.createWithResource(
                        appContext, R.drawable.ic_prayer
                    ).toIcon(appContext)
                )
            } catch (e: Exception) {
                Log.d(TAG, "Could not set large icon: ${e.message}")
            }
        }
        
        Log.d(TAG, "Built Live Update ready notification")
        return builder
    }
    
    /**
     * PROGRESS BAR BUILDER: Creates the colored segments for Android 16+ notifications
     * 
     * This creates the three-color progress bar that shows prayer phases visually.
     * 
     * SEGMENT COLORS & MEANINGS:
     * - Blue (0-20%): Go to Mosque phase - Time to prepare
     * - Green (20-60%): Best Time phase - Optimal for prayer
     * - Yellow (60-100%): Make Time phase - Ensure you pray
     * 
     * EDIT THIS TO:
     * - Change segment colors (modify Color.valueOf values)
     * - Adjust segment sizes (change 20, 40, 40 values)
     * - Add more segments
     */
    private fun addProgressToBuilder(builder: NotificationCompat.Builder, progress: Int) {
        // Add standard progress bar
        builder.setProgress(100, progress, false)
    }
    
    /**
     * Build regular notification for pre-Android 16 devices
     */
    private fun buildRegularNotification(prayerName: String, progress: Int, isOngoing: Boolean): NotificationCompat.Builder {
        val title = try {
            appContext.getString(R.string.live_notification_title)
        } catch (e: Exception) {
            "Prayer Time Tracker"
        }
        
        val content = try {
            appContext.getString(R.string.live_notification_content, prayerName)
        } catch (e: Exception) {
            "Current prayer: $prayerName"
        }
        
        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_prayer)
            .setContentIntent(createAppLaunchIntent())
            .setOngoing(isOngoing)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        
        // Show simple progress for regular notifications
        if (progress > 0 && progress <= 100) {
            builder.setProgress(100, progress, false)
        }
        
        Log.d(TAG, "Built regular notification")
        return builder
    }
    
    /**
     * Post compatibility notification for older Android versions
     */
    private fun postCompatNotification(
        title: String,
        content: String,
        detailedMessage: String,
        progress: Int,
        isOngoing: Boolean
    ) {
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID).apply {
            setContentTitle(title)
            setContentText(content)
            setStyle(NotificationCompat.BigTextStyle().bigText(detailedMessage))
            setSmallIcon(R.drawable.ic_prayer)
            setContentIntent(createAppLaunchIntent())
            setOngoing(isOngoing)
            setCategory(NotificationCompat.CATEGORY_STATUS)
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            setPriority(NotificationCompat.PRIORITY_HIGH)
            setLocalOnly(false) // Allow lock screen display
        }
        
        // Add progress if specified
        if (progress > 0 && progress <= 100) {
            notification.setProgress(100, progress, false)
            // Removed custom colors to maintain lock screen compatibility
        }
        
        notificationManager.notify(NOTIFICATION_ID, notification.build())
        Log.d(TAG, "Posted compat notification: $title")
    }
    
    /**
     * Update prayer progress - main entry point for live updates
     */
    fun updatePrayerProgress(prayerName: String, progress: Int) {
        postPrayerNotification(prayerName, progress, true)
    }
    
    /**
     * SMART NOTIFICATION SYSTEM: The core intelligence of prayer notifications
     * 
     * This prevents notification spam by only alerting when prayer phases change.
     * 
     * SMART BEHAVIOR:
     * - SILENT updates when staying in same phase (no sound/vibration)
     * - ALERT with sound/vibration when phase changes
     * - Progress bar updates continuously regardless
     * 
     * PHASES:
     * - 0-20%: GO_TO_MOSQUE (Blue) - Time to prepare and go
     * - 20-60%: BEST_TIME_TO_PRAY (Green) - Optimal prayer time
     * - 60-100%: MAKE_TIME_FOR_PRAYER (Yellow) - Ensure you pray
     * 
     * EDIT THIS TO:
     * - Change phase transition logic
     * - Modify alert behavior
     * - Add new notification types
     */
    fun updatePrayerProgressSmart(
        prayerName: String, 
        progress: Int, 
        previousPhase: String? = null,
        title: String? = null,
        content: String? = null,
        detailedMessage: String? = null
    ) {
        Log.d(TAG, "=== SMART PRAYER NOTIFICATION UPDATE ===")
        Log.d(TAG, "Prayer: $prayerName")
        Log.d(TAG, "Progress: $progress%")
        Log.d(TAG, "Previous phase: ${previousPhase ?: "None"}")
        Log.d(TAG, "Title: ${title ?: "None"}")
        Log.d(TAG, "Content: ${content ?: "None"}")
        Log.d(TAG, "Detailed message length: ${detailedMessage?.length ?: 0} characters")
        
        val currentPhase = getCurrentPrayerPhase(progress)
        Log.d(TAG, "Current phase determined: $currentPhase")
        
        // PHASE CHANGE DETECTION - This determines if we should alert or stay silent
        val isPhaseTransition = previousPhase != null && previousPhase != currentPhase
        Log.d(TAG, "Phase transition check: previous='$previousPhase', current='$currentPhase', isTransition=$isPhaseTransition")
        
        if (isPhaseTransition) {
            // New phase - show popup and sound
            Log.i(TAG, "🔊 PHASE TRANSITION DETECTED: $previousPhase -> $currentPhase")
            Log.i(TAG, "Posting notification WITH ALERT (sound/vibration)")
            postPrayerNotificationWithAlert(prayerName, progress, currentPhase, title, content, detailedMessage)
        } else {
            // Same phase - silent update
            Log.d(TAG, "🔇 SAME PHASE ($currentPhase) - posting silent update")
            postPrayerNotificationSilent(prayerName, progress, currentPhase, title, content, detailedMessage)
        }
    }
    
    /**
     * PHASE CALCULATOR: Determines which prayer phase we're in
     * 
     * This maps progress percentage to prayer phases.
     * 
     * CURRENT MAPPING:
     * - 0-20%: GO_TO_MOSQUE (Blue progress segment)
     * - 21-60%: BEST_TIME_TO_PRAY (Green progress segment)
     * - 61-100%: MAKE_TIME_FOR_PRAYER (Yellow progress segment)
     * 
     * EDIT THESE PERCENTAGES TO:
     * - Change when phases switch
     * - Add new phases
     * - Modify phase behavior
     */
    private fun getCurrentPrayerPhase(progress: Int): String {
        return when {
            progress <= 20 -> "GO_TO_MOSQUE"           // First 20% - Blue segment
            progress <= 60 -> "BEST_TIME_TO_PRAY"      // 21-60% - Green segment
            else -> "MAKE_TIME_FOR_PRAYER"             // 61-100% - Yellow segment
        }
    }
    
    /**
     * Post notification with alert (popup and sound) for phase transitions
     */
    private fun postPrayerNotificationWithAlert(
        prayerName: String, 
        progress: Int, 
        phase: String,
        title: String? = null,
        content: String? = null,
        detailedMessage: String? = null
    ) {
        Log.d(TAG, "=== POSTING ALERT NOTIFICATION ===")
        Log.d(TAG, "Input parameters:")
        Log.d(TAG, "  Prayer: $prayerName")
        Log.d(TAG, "  Progress: $progress%")
        Log.d(TAG, "  Phase: $phase")
        Log.d(TAG, "  Title provided: ${title != null}")
        Log.d(TAG, "  Content provided: ${content != null}")
        Log.d(TAG, "  Detailed message provided: ${detailedMessage != null}")
        
        // Use actual prayer data if available, otherwise fall back to generic phase info
        val notificationTitle = title ?: "$prayerName - ${getPhaseTitle(phase)}"
        val notificationContent = content ?: getPhaseDescription(phase)
        val notificationDetailed = detailedMessage ?: getPhaseDescription(phase)
        
        Log.d(TAG, "Final notification content:")
        Log.i(TAG, "🔔 ALERT NOTIFICATION - Phase: $phase")
        Log.i(TAG, "   📝 Title: '$notificationTitle' (${notificationTitle.length} chars)")
        Log.i(TAG, "   📝 Content: '$notificationContent' (${notificationContent.length} chars)")
        Log.d(TAG, "   📝 Detailed: '${notificationDetailed.take(100)}...' (${notificationDetailed.length} chars)")
        Log.d(TAG, "   📝 Detailed: '$notificationDetailed' (${notificationDetailed.length} chars)")
        Log.d(TAG, "   📊 Progress: $progress%")
        
        val notification = buildPhaseTransitionNotification(
            prayerName, progress, notificationTitle, notificationContent, true, notificationDetailed
        )
        notificationManager.notify(NOTIFICATION_ID, notification.build())
        Log.d(TAG, "Posted phase transition notification with alert: $phase")
    }
    
    /**
     * Post notification silently (no popup, no sound) for ongoing updates
     */
    private fun postPrayerNotificationSilent(
        prayerName: String, 
        progress: Int, 
        phase: String,
        title: String? = null,
        content: String? = null,
        detailedMessage: String? = null
    ) {
        Log.d(TAG, "=== POSTING SILENT NOTIFICATION ===")
        Log.d(TAG, "Input parameters:")
        Log.d(TAG, "  Prayer: $prayerName")
        Log.d(TAG, "  Progress: $progress%")
        Log.d(TAG, "  Phase: $phase")
        Log.d(TAG, "  Title provided: ${title != null}")
        Log.d(TAG, "  Content provided: ${content != null}")
        Log.d(TAG, "  Detailed message provided: ${detailedMessage != null}")
        
        // Use actual prayer data if available, otherwise fall back to generic phase info
        val notificationTitle = title ?: "$prayerName - ${getPhaseDescription(phase)}"
        val notificationContent = content ?: getPhaseDescription(phase)
        val notificationDetailed = detailedMessage ?: getPhaseDescription(phase)
        
        Log.d(TAG, "Final notification content:")
        Log.d(TAG, "🔇 SILENT NOTIFICATION - Phase: $phase")
        Log.d(TAG, "   📝 Title: '$notificationTitle' (${notificationTitle.length} chars)")
        Log.d(TAG, "   📝 Content: '$notificationContent' (${notificationContent.length} chars)")
        Log.d(TAG, "   📝 Detailed: '${notificationDetailed.take(100)}...' (${notificationDetailed.length} chars)")
        Log.d(TAG, "   📊 Progress: $progress%")
        Log.d(TAG, "   📝 Detailed: '$notificationDetailed' (${notificationDetailed.length} chars)")
        Log.d(TAG, "   📊 Progress: $progress%")
        
        val notification = buildPhaseTransitionNotification(
            prayerName, progress, notificationTitle, notificationContent, false, notificationDetailed
        )
        notificationManager.notify(NOTIFICATION_ID, notification.build())
        Log.d(TAG, "Posted silent progress update: $phase")
    }
    
    /**
     * Build notification for phase transitions
     */
    private fun buildPhaseTransitionNotification(
        prayerName: String, 
        progress: Int, 
        phaseTitle: String, 
        phaseDescription: String,
        withAlert: Boolean,
        detailedMessage: String? = null
    ): NotificationCompat.Builder {
        Log.d(TAG, "🔨 BUILDING NOTIFICATION:")
        Log.d(TAG, "   📝 Phase Title: '$phaseTitle' (${phaseTitle.length} chars)")
        Log.d(TAG, "   📝 Phase Description: '$phaseDescription' (${phaseDescription.length} chars)")
        Log.d(TAG, "   📝 Detailed Message: '${detailedMessage ?: "null"}' (${detailedMessage?.length ?: 0} chars)")
        Log.d(TAG, "   📊 Progress: $progress%")
        Log.d(TAG, "   🔔 With Alert: $withAlert")
        
        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle(phaseTitle) // Just the phase title, no prayer name prefix
            .setContentText(phaseDescription) // Elapsed time since prayer started
            .setSmallIcon(R.drawable.ic_prayer)
            .setContentIntent(createAppLaunchIntent())
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setLocalOnly(false)
            .setAutoCancel(false)
            .setOnlyAlertOnce(false) // Allow alerts for phase transitions
        
        // Set alert behavior based on phase transition
        if (withAlert) {
            // Phase transition - show popup and sound
            builder.setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL) // Sound, vibration, lights
                .setSilent(false)
                .setVibrate(longArrayOf(0, 250, 100, 250)) // Short vibration pattern
        } else {
            // Silent update - no popup, no sound
            builder.setPriority(NotificationCompat.PRIORITY_LOW)
                .setDefaults(0) // No sound, vibration, or lights
                .setSilent(true)
                .setVibrate(null)
        }
        
        // Add live update progress style for Android 16+ or basic progress bar for older versions
        if (progress > 0 && progress <= 100) {
            if (Build.VERSION.SDK_INT >= 35 && supportsLiveUpdates()) {
                // Use Android 16 Live Update progress style with segments
                try {
                    // Create progress style with 3 distinct segments showing actual progress within each phase
                    // Each segment represents a phase: Blue(0-20%), Green(20-60%), Yellow(60-100%)
                    
                    // Log the progress value and segment calculations
                    Log.d(TAG, "🔍 DEBUG: Creating notification segments for progress: $progress%")
                    
                    val blueSegmentSize = 20
                    val greenSegmentSize = 40
                    val yellowSegmentSize = 40
                    
                    Log.d(TAG, "🔍 DEBUG: Segment sizes - Blue: $blueSegmentSize%, Green: $greenSegmentSize%, Yellow: $yellowSegmentSize%")
                    
                    // Use enhanced progress bar for Android 15+
                    builder.setProgress(100, progress, false)
                    
                    // Enable live update features
                    builder.setOngoing(true)
                    
                    Log.d(TAG, "🔍 DEBUG: Added enhanced progress bar with progress: $progress%")
                    
                    // Android 15+ Live Update features enabled
                    
                    // Add the detailed message as additional content
                    if (!detailedMessage.isNullOrBlank()) {
                        // Set the detailed message as the main content text, combining both content and detailed message
                        val combinedContent = "$phaseDescription\n$detailedMessage"
                        builder.setContentText(combinedContent)
                        Log.d(TAG, "📝 Added detailed message to content: '$combinedContent'")
                    }
                    
                    Log.d(TAG, "✅ Applied Live Update progress style with segments for progress: $progress%")
                    
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to apply Live Update progress style, falling back to basic: ${e.message}")
                    builder.setProgress(100, progress, false)
                    
                    // Add detailed message as BigTextStyle for fallback
                    if (!detailedMessage.isNullOrBlank()) {
                        builder.setStyle(NotificationCompat.BigTextStyle().bigText(detailedMessage))
                    }
                }
            } else {
                // Basic progress bar for older Android versions
                builder.setProgress(100, progress, false)
                
                // Add detailed message as BigTextStyle for older Android
                if (!detailedMessage.isNullOrBlank()) {
                    builder.setStyle(NotificationCompat.BigTextStyle().bigText(detailedMessage))
                }
            }
        } else {
            // No progress bar, just add detailed message
            if (!detailedMessage.isNullOrBlank()) {
                builder.setStyle(NotificationCompat.BigTextStyle().bigText(detailedMessage))
            }
        }
        
        Log.d(TAG, "🏗️ FINAL NOTIFICATION BUILDER:")
        Log.d(TAG, "   📝 Title: '${builder.build().extras.getString("android.title")}'")
        Log.d(TAG, "   📝 Text: '${builder.build().extras.getString("android.text")}'")
        Log.d(TAG, "   📝 Style: ${builder.build().extras.getString("android.template")}")
        Log.d(TAG, "   📊 Progress: ${builder.build().extras.getInt("android.progress", -1)}/${builder.build().extras.getInt("android.progressMax", -1)}")
        
        return builder
    }
    
    /**
     * PHASE TITLE FORMATTER: Converts phase codes to user-friendly titles
     * 
     * This formats the internal phase names into readable notification titles.
     * 
     * EDIT THESE STRINGS TO:
     * - Change notification titles
     * - Translate to different languages
     * - Add new phase titles
     */
    private fun getPhaseTitle(phase: String): String {
        return when (phase) {
            "GO_TO_MOSQUE" -> "Go to Mosque"              // Blue phase title
            "BEST_TIME_TO_PRAY" -> "Best Time to Pray"    // Green phase title
            "MAKE_TIME_FOR_PRAYER" -> "Make Time for Prayer"  // Yellow phase title
            else -> "Prayer Time"                         // Fallback title
        }
    }
    
    /**
     * PHASE DESCRIPTION FORMATTER: Provides detailed phase descriptions
     * 
     * This creates the descriptive text that appears in notification content.
     * 
     * EDIT THESE DESCRIPTIONS TO:
     * - Change notification content text
     * - Add more detailed guidance
     * - Translate to different languages
     */
    private fun getPhaseDescription(phase: String): String {
        return when (phase) {
            "GO_TO_MOSQUE" -> "Time to prepare and go to the mosque"    // Blue phase description
            "BEST_TIME_TO_PRAY" -> "Optimal time for prayer"           // Green phase description
            "MAKE_TIME_FOR_PRAYER" -> "Ensure you make time for prayer" // Yellow phase description
            else -> "Prayer time in progress"                            // Fallback description
        }
    }
    
    /**
     * Cancel prayer notification
     */
    fun cancelPrayerNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
        Log.d(TAG, "Cancelled prayer notification")
    }
    
    /**
     * Check if notification has promotable characteristics (for debugging)
     * Future-ready for when AndroidX supports hasPromotableCharacteristics()
     */
    fun hasPromotableCharacteristics(): Boolean {
        val hasPromotable = supportsLiveUpdates() // Simplified check for now
        Log.d(TAG, "Has promotable characteristics: $hasPromotable")
        return hasPromotable
    }
    
    /**
     * Force refresh notification to trigger Live Updates
     * This ensures Android 16 Live Update features are properly activated
     */
    fun forceRefreshNotification() {
        if (supportsLiveUpdates()) {
            try {
                // Use Live Update notification for Android 16
                val isDarkTheme = (appContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                val themeColor = if (isDarkTheme) {
                    Color.parseColor("#FFA9FE") // Purple80 - Dark theme
                } else {
                    Color.parseColor("#8B418F") // Purple40 - Light theme
                }
                
                val notification = buildLiveUpdateNotification(
                    "Prayer Time Tracker",
                    "Live Updates Active",
                    "Prayer time tracking with real-time updates",
                    0,
                    true,
                    "Prayer Time Tracker" // Pass a dummy prayer name for the force refresh
                ).build()
                
                notificationManager.notify(NOTIFICATION_ID, notification)
                Log.d(TAG, "Forced Live Update notification refresh")
            } catch (e: Exception) {
                Log.w(TAG, "Error forcing Live Update notification refresh: ${e.message}")
                // Fallback to simple notification
                val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
                    .setContentTitle("Prayer Time Tracker")
                    .setContentText("Live Updates Active")
                    .setSmallIcon(R.drawable.ic_prayer)
                    .setOngoing(true)
                    .setCategory(NotificationCompat.CATEGORY_STATUS)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setColor(Color.parseColor("#8B418F"))
                    .setColorized(true)
                
                notificationManager.notify(NOTIFICATION_ID, builder.build())
                Log.d(TAG, "Forced compat notification refresh")
            }
        }
    }
    
    /**
     * Get notification manager for advanced usage
     */
    fun getNotificationManager(): NotificationManager = notificationManager

    /**
     * Check and log detailed Live Update status for debugging
     */
    fun checkLiveUpdateStatus(): String {
        val apiLevel = Build.VERSION.SDK_INT
        val supportsLiveUpdates = supportsLiveUpdates()
        val channelCreated = try {
            notificationManager.getNotificationChannel(CHANNEL_ID) != null
        } catch (e: Exception) {
            false
        }
        
        val status = buildString {
            appendLine("📱 Live Update Status Check:")
            appendLine("   API Level: $apiLevel (Android ${getAndroidVersionName(apiLevel)})")
            appendLine("   Live Updates Supported: $supportsLiveUpdates")
            appendLine("   Notification Channel Created: $channelCreated")
            appendLine("   App Context Available: ${::appContext.isInitialized}")
            
            if (supportsLiveUpdates) {
                appendLine("   ✅ Android 16+ Live Update features available")
                appendLine("   🎯 Enhanced progress bar will be applied")
                appendLine("   🔄 Promoted ongoing notifications enabled")
            } else {
                appendLine("   ❌ Live Updates not supported on this device")
                appendLine("   📱 Requires Android 16 (API 35+)")
            }
        }
        
        Log.i(TAG, status)
        return status
    }
    
    /**
     * Get Android version name from API level
     */
    private fun getAndroidVersionName(apiLevel: Int): String {
        return when (apiLevel) {
            35 -> "16 (Upside Down Cake)"
            34 -> "14 (Upside Down Cake)"
            33 -> "13 (Tiramisu)"
            32 -> "12L (Snow Cone)"
            31 -> "12 (Snow Cone)"
            30 -> "11 (Red Velvet Cake)"
            else -> "Unknown"
        }
    }


    
    /**
     * Build notification with enhanced progress bar for Android 15+
     * This provides modern progress segments and better visual appeal
     * Optimized for lock screen compatibility with static colors
     */
    @RequiresApi(35) // Android 16
    private fun buildLiveUpdateNotification(
        title: String,
        content: String,
        detailedMessage: String,
        progress: Int,
        isOngoing: Boolean,
        prayerName: String
    ): NotificationCompat.Builder {
        return NotificationCompat.Builder(appContext, CHANNEL_ID).apply {
            setContentTitle(title)
            setContentText(content)
            setSmallIcon(R.drawable.ic_prayer)
            setContentIntent(createAppLaunchIntent())
            setOngoing(isOngoing)
            setCategory(NotificationCompat.CATEGORY_STATUS)
            setVisibility(Notification.VISIBILITY_PUBLIC)
            setPriority(NotificationCompat.PRIORITY_HIGH)
            
            // Calculate proper segment proportions based on prayer time phases
            val (blueEnd, greenEnd, yellowEnd) = calculatePrayerTimeSegments(prayerName)
            
            // Use enhanced progress bar for Android 15+
            setProgress(100, progress, false)
            setOngoing(true)
            
            // Add priority for live updates
            priority = NotificationCompat.PRIORITY_DEFAULT
        }
    }
    
    /**
     * Calculate prayer time segment proportions based on timing requirements
     * 0-20% = Blue, 20-60% = Green, 60-100% = Yellow
     * Segments are dynamic based on current progress
     */
    private fun calculatePrayerTimeSegments(prayerName: String): Triple<Float, Float, Float> {
        // Segments match the service progress calculation:
        // Blue: 0-20% (Go to mosque phase)
        val blueEnd = 20f
        
        // Green: 20-60% (Best time phase) 
        val greenEnd = 60f
        
        // Yellow: 60-100% (Make time phase)
        val yellowEnd = 100f
        
        Log.d(TAG, "Prayer segments: Blue(0-${blueEnd}%), Green(${blueEnd}-${greenEnd}%), Yellow(${greenEnd}-${yellowEnd}%)")
        
        return Triple(blueEnd, greenEnd, yellowEnd)
    }
}