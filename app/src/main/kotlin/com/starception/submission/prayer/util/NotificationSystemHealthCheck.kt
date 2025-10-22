package com.starception.submission.prayer.util

import android.app.AlarmManager
import android.content.Context
import android.util.Log
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.starception.submission.prayer.scheduler.PrayerNotificationScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Notification System Health Check
 * 
 * Provides diagnostic information about the prayer notification system
 * to help verify everything is working correctly.
 */
@Singleton
class NotificationSystemHealthCheck @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val TAG = "NotificationHealthCheck"
    }
    
    /**
     * Performs a comprehensive health check of the notification system
     * and logs the results.
     */
    suspend fun performHealthCheck(): HealthCheckResult {
        Log.i(TAG, "")
        Log.i(TAG, "🏥 NOTIFICATION SYSTEM HEALTH CHECK")
        Log.i(TAG, "=" .repeat(60))
        
        val result = HealthCheckResult()
        
        // Check 1: WorkManager Status
        checkWorkManagerStatus(result)
        
        // Check 2: AlarmManager Permissions
        checkAlarmManagerPermissions(result)
        
        // Check 3: Notification Permissions
        checkNotificationPermissions(result)
        
        // Check 4: Receivers Status
        checkReceiversStatus(result)
        
        // Print Summary
        printHealthCheckSummary(result)
        
        return result
    }
    
    private suspend fun checkWorkManagerStatus(result: HealthCheckResult) {
        try {
            val workManager = WorkManager.getInstance(context)
            val workInfos = workManager.getWorkInfosByTag("PrayerNotificationWork_").first()
            
            result.workManagerAvailable = true
            result.scheduledWorkCount = workInfos.size
            result.runningWorkCount = workInfos.count { it.state == WorkInfo.State.RUNNING }
            result.enqueuedWorkCount = workInfos.count { it.state == WorkInfo.State.ENQUEUED }
            
            Log.i(TAG, "✅ WorkManager: Available")
            Log.i(TAG, "   📊 Scheduled Work Items: ${result.scheduledWorkCount}")
            Log.i(TAG, "   🏃 Running: ${result.runningWorkCount}")
            Log.i(TAG, "   ⏳ Enqueued: ${result.enqueuedWorkCount}")
        } catch (e: Exception) {
            result.workManagerAvailable = false
            result.workManagerError = e.message
            Log.e(TAG, "❌ WorkManager: Error - ${e.message}")
        }
    }
    
    private fun checkAlarmManagerPermissions(result: HealthCheckResult) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            
            if (alarmManager != null) {
                result.alarmManagerAvailable = true
                
                // Check if we can schedule exact alarms
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    result.canScheduleExactAlarms = alarmManager.canScheduleExactAlarms()
                    
                    if (result.canScheduleExactAlarms) {
                        Log.i(TAG, "✅ AlarmManager: Available with exact alarm permission")
                    } else {
                        Log.w(TAG, "⚠️ AlarmManager: Available but exact alarm permission not granted")
                    }
                } else {
                    result.canScheduleExactAlarms = true
                    Log.i(TAG, "✅ AlarmManager: Available (exact alarms not required on this Android version)")
                }
            } else {
                result.alarmManagerAvailable = false
                Log.e(TAG, "❌ AlarmManager: Not available")
            }
        } catch (e: Exception) {
            result.alarmManagerAvailable = false
            result.alarmManagerError = e.message
            Log.e(TAG, "❌ AlarmManager: Error - ${e.message}")
        }
    }
    
    private fun checkNotificationPermissions(result: HealthCheckResult) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                val hasPermission = context.checkSelfPermission(
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                
                result.notificationPermissionGranted = hasPermission
                
                if (hasPermission) {
                    Log.i(TAG, "✅ Notifications: Permission granted")
                } else {
                    Log.w(TAG, "⚠️ Notifications: Permission not granted")
                }
            } else {
                result.notificationPermissionGranted = true
                Log.i(TAG, "✅ Notifications: No runtime permission required on this Android version")
            }
        } catch (e: Exception) {
            result.notificationPermissionError = e.message
            Log.e(TAG, "❌ Notifications: Error checking permission - ${e.message}")
        }
    }
    
    private fun checkReceiversStatus(result: HealthCheckResult) {
        try {
            val pm = context.packageManager
            val packageName = context.packageName
            
            // Check PrayerBootReceiver
            val bootReceiverComponent = android.content.ComponentName(
                packageName,
                "$packageName.prayer.receiver.PrayerBootReceiver"
            )
            val bootReceiverEnabled = pm.getComponentEnabledSetting(bootReceiverComponent) != 
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            
            result.bootReceiverEnabled = bootReceiverEnabled
            
            if (bootReceiverEnabled) {
                Log.i(TAG, "✅ Boot Receiver: Enabled")
            } else {
                Log.w(TAG, "⚠️ Boot Receiver: Disabled")
            }
            
            // Check PrayerNotificationReceiver
            val notificationReceiverComponent = android.content.ComponentName(
                packageName,
                "$packageName.prayer.receiver.PrayerNotificationReceiver"
            )
            val notificationReceiverEnabled = pm.getComponentEnabledSetting(notificationReceiverComponent) != 
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            
            result.notificationReceiverEnabled = notificationReceiverEnabled
            
            if (notificationReceiverEnabled) {
                Log.i(TAG, "✅ Notification Receiver: Enabled")
            } else {
                Log.w(TAG, "⚠️ Notification Receiver: Disabled")
            }
        } catch (e: Exception) {
            result.receiversError = e.message
            Log.e(TAG, "❌ Receivers: Error checking status - ${e.message}")
        }
    }
    
    private fun printHealthCheckSummary(result: HealthCheckResult) {
        Log.i(TAG, "")
        Log.i(TAG, "📊 HEALTH CHECK SUMMARY")
        Log.i(TAG, "-".repeat(60))
        
        val issues = mutableListOf<String>()
        
        if (!result.workManagerAvailable) issues.add("WorkManager unavailable")
        if (!result.alarmManagerAvailable) issues.add("AlarmManager unavailable")
        if (!result.canScheduleExactAlarms) issues.add("Exact alarms not permitted")
        if (!result.notificationPermissionGranted) issues.add("Notification permission not granted")
        if (!result.bootReceiverEnabled) issues.add("Boot receiver disabled")
        if (!result.notificationReceiverEnabled) issues.add("Notification receiver disabled")
        
        if (issues.isEmpty()) {
            Log.i(TAG, "✅ System Status: ALL SYSTEMS OPERATIONAL")
            Log.i(TAG, "   🎉 The notification system is fully functional!")
        } else {
            Log.w(TAG, "⚠️ System Status: ISSUES DETECTED")
            Log.w(TAG, "   Issues found:")
            issues.forEach { issue ->
                Log.w(TAG, "   - $issue")
            }
        }
        
        Log.i(TAG, "")
        Log.i(TAG, "📈 Statistics:")
        Log.i(TAG, "   - WorkManager Tasks: ${result.scheduledWorkCount}")
        Log.i(TAG, "   - Running Tasks: ${result.runningWorkCount}")
        Log.i(TAG, "   - Enqueued Tasks: ${result.enqueuedWorkCount}")
        Log.i(TAG, "=" .repeat(60))
        Log.i(TAG, "")
    }
}

/**
 * Data class containing health check results
 */
data class HealthCheckResult(
    var workManagerAvailable: Boolean = false,
    var workManagerError: String? = null,
    var scheduledWorkCount: Int = 0,
    var runningWorkCount: Int = 0,
    var enqueuedWorkCount: Int = 0,
    
    var alarmManagerAvailable: Boolean = false,
    var alarmManagerError: String? = null,
    var canScheduleExactAlarms: Boolean = false,
    
    var notificationPermissionGranted: Boolean = false,
    var notificationPermissionError: String? = null,
    
    var bootReceiverEnabled: Boolean = false,
    var notificationReceiverEnabled: Boolean = false,
    var receiversError: String? = null
) {
    /**
     * Returns true if all critical systems are operational
     */
    fun isFullyOperational(): Boolean {
        return workManagerAvailable &&
               alarmManagerAvailable &&
               notificationPermissionGranted &&
               bootReceiverEnabled &&
               notificationReceiverEnabled
    }
    
    /**
     * Returns true if the system can deliver notifications (may not be optimal)
     */
    fun canDeliverNotifications(): Boolean {
        return (workManagerAvailable || alarmManagerAvailable) &&
               notificationPermissionGranted
    }
}

