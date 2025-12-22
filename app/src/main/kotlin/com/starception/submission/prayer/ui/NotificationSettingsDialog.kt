package com.starception.submission.prayer.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import android.util.Log
import com.starception.submission.prayer.model.PrayerNotificationPreferences
import com.starception.submission.prayer.service.PrayerNotificationServiceManager

/**
 * Notification Settings Dialog that wraps NotificationSettingsScreen as full-screen modal
 */
@Composable
fun NotificationSettingsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // Animation states
    var isVisible by remember { mutableStateOf(true) }

    // Get repository from Hilt singleton
    val repository = remember {
        val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
            context.applicationContext,
            com.starception.submission.feature.prayertimes.data.PrayerTimeCalculatorEntryPoint::class.java
        )
        entryPoint.prayerSettingsRepository()
    }

    // Load notification preferences
    var preferences by remember { mutableStateOf(PrayerNotificationPreferences()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            Log.i("NotificationSettingsDialog", "Loading notification preferences...")
            preferences = repository.getNotificationPreferences()
            Log.i("NotificationSettingsDialog", "Loaded preferences: notificationsEnabled=${preferences.notificationsEnabled}")
            isLoading = false
        } catch (e: Exception) {
            Log.e("NotificationSettingsDialog", "Failed to load preferences", e)
            isLoading = false
        }
    }

    // Animated entrance scale and fade
    val surfaceScale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.92f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "surface_scale"
    )

    val surfaceAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(
            durationMillis = 400,
            easing = FastOutSlowInEasing
        ),
        label = "surface_alpha"
    )

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = surfaceScale
                scaleY = surfaceScale
                alpha = surfaceAlpha
            },
        color = MaterialTheme.colorScheme.background
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            NotificationSettingsScreen(
                preferences = preferences,
                onPreferencesChanged = { newPreferences ->
                    preferences = newPreferences
                    // Save to repository
                    repository.updateNotificationPreferences(newPreferences, forceCommit = true)
                    Log.i("NotificationSettingsDialog", "Updated notification preferences")

                    // Trigger notification reschedule so changes take effect immediately
                    PrayerNotificationServiceManager.rescheduleNotificationsWithNewSettings(context)
                    Log.i("NotificationSettingsDialog", "Triggered notification reschedule")
                },
                onBackClick = onDismiss
            )
        }
    }
}
