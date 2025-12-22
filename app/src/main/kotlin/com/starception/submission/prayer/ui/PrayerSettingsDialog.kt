package com.starception.submission.prayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.starception.submission.prayer.viewmodel.PrayerTimesViewModel
import com.starception.submission.prayer.model.PrayerSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log

/**
 * Prayer Settings Dialog that wraps PrayerSettingsScreen as full-screen modal
 */
@Composable
fun PrayerSettingsDialog(
    onDismiss: () -> Unit
) {
    // Get context outside of coroutines
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    
    // Animation states - start visible to prevent white flash
    var isVisible by remember { mutableStateOf(true) }
    var animateContent by remember { mutableStateOf(false) }
    
    // ALGORITHM IMPLEMENTATION: Initialization
    var settings by remember { mutableStateOf(PrayerSettings()) }
    var isLoading by remember { mutableStateOf(true) }

    // Notification settings dialog state
    var showNotificationSettings by remember { mutableStateOf(false) }

    // IMPORTANT: Get repository from Hilt singleton (not by direct instantiation)
    val repository = remember {
        val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
            context.applicationContext,
            com.starception.submission.feature.prayertimes.data.PrayerTimeCalculatorEntryPoint::class.java
        )
        val repo = entryPoint.prayerSettingsRepository()
        val instanceId = System.identityHashCode(repo).toString(16)
        android.util.Log.d("PrayerSettingsDialog", "📍 REPOSITORY INSTANCE OBTAINED: $instanceId")
        repo
    }
    
    // Staggered content animation trigger
    LaunchedEffect(Unit) {
        delay(100) // Shorter delay for faster content appearance
        animateContent = true
    }
    
    // Initialization: Load settings following the algorithm
    LaunchedEffect(Unit) {
        try {
            Log.i("PrayerSettingsDialog", "🔄 ALGORITHM: Starting initialization...")
            
            // 1. Detect cached country
            val cachedCountry = repository.getCachedCountry()
            Log.i("PrayerSettingsDialog", "📍 Cached country: $cachedCountry")
            
            // 2. Load auto-detected settings for that country (if available)
            val autoDetectedSettings = cachedCountry?.let { repository.getAutoDetectedSettingsForCountry(it) }
            Log.i("PrayerSettingsDialog", "🤖 Auto-detected settings: ${autoDetectedSettings != null}")
            
            // 3. Load CURRENT settings from reactive flow (SINGLE SOURCE OF TRUTH)
            val currentCalculationSettings = repository.getLoadedCalculationSettings()
            Log.i("PrayerSettingsDialog", "💾 Current calculation settings loaded from flow")
            Log.i("PrayerSettingsDialog", "   🌇 Asr offset: ${currentCalculationSettings.timeOffsets.asr}")
            Log.i("PrayerSettingsDialog", "   🌞 Dhuhr offset: ${currentCalculationSettings.timeOffsets.dhuhr}")

            // 4. Populate UI fields - use current calculation settings as the source of truth
            val initialSettings = when {
                autoDetectedSettings != null && cachedCountry != null -> {
                    Log.i("PrayerSettingsDialog", "✅ Merging calculation settings with auto-detected country info")
                    Log.i("PrayerSettingsDialog", "   🌍 Country: ${autoDetectedSettings.autoDetectedCountryName} ($cachedCountry)")

                    // Convert CalculationSettings to PrayerSettings with country info
                    PrayerSettings(
                        calculationMethod = currentCalculationSettings.calculationMethod,
                        asrMadhhab = currentCalculationSettings.asrMadhhab,
                        highLatitudeAdjustment = currentCalculationSettings.highLatitudeAdjustment,
                        customFajrAngle = currentCalculationSettings.customFajrAngle,
                        customIshaAngle = currentCalculationSettings.customIshaAngle,
                        customIshaDelay = currentCalculationSettings.customIshaDelay,
                        customMaghribOffset = currentCalculationSettings.customMaghribOffset,
                        timeOffsets = currentCalculationSettings.timeOffsets,
                        autoDetectedCountryName = autoDetectedSettings.autoDetectedCountryName,
                        autoDetectedCountryCode = autoDetectedSettings.autoDetectedCountryCode,
                        isMethodAutoDetected = autoDetectedSettings.isMethodAutoDetected,
                        isMadhhabAutoDetected = autoDetectedSettings.isMadhhabAutoDetected,
                        areCustomAnglesAutoDetected = autoDetectedSettings.areCustomAnglesAutoDetected
                    )
                }
                autoDetectedSettings != null -> {
                    Log.i("PrayerSettingsDialog", "✅ Using auto-detected settings for $cachedCountry")
                    autoDetectedSettings
                }
                else -> {
                    Log.i("PrayerSettingsDialog", "✅ Using current calculation settings as base")
                    // Convert CalculationSettings to PrayerSettings
                    PrayerSettings(
                        calculationMethod = currentCalculationSettings.calculationMethod,
                        asrMadhhab = currentCalculationSettings.asrMadhhab,
                        highLatitudeAdjustment = currentCalculationSettings.highLatitudeAdjustment,
                        customFajrAngle = currentCalculationSettings.customFajrAngle,
                        customIshaAngle = currentCalculationSettings.customIshaAngle,
                        customIshaDelay = currentCalculationSettings.customIshaDelay,
                        customMaghribOffset = currentCalculationSettings.customMaghribOffset,
                        timeOffsets = currentCalculationSettings.timeOffsets
                    )
                }
            }

            settings = initialSettings
            Log.i("PrayerSettingsDialog", "📊 Initial UI offsets set:")
            Log.i("PrayerSettingsDialog", "   🌇 Asr: ${initialSettings.timeOffsets.asr}")
            Log.i("PrayerSettingsDialog", "   🌞 Dhuhr: ${initialSettings.timeOffsets.dhuhr}")
            isLoading = false
            Log.i("PrayerSettingsDialog", "✅ Initialization complete")
        } catch (e: Exception) {
            Log.e("PrayerSettingsDialog", "❌ Initialization failed", e)
            isLoading = false
        }
    }
    
    // ALGORITHM: Restore Option Logic
    // Compare cached_prayer_settings with auto-detected settings to show/hide restore option
    // Use mutableStateOf to reactively update when settings change
    var hasSettingsChanged by remember { mutableStateOf(false) }

    // LaunchedEffect to re-evaluate restore option when settings change
    LaunchedEffect(settings, isLoading) {
        if (!isLoading) {
            Log.i("PrayerSettingsDialog", "🔄 RE-EVALUATING RESTORE OPTION")
            Log.i("PrayerSettingsDialog", "   Current settings: ${settings.calculationMethod.name}, ${settings.asrMadhhab.name}")
            Log.i("PrayerSettingsDialog", "   autoDetectedCountryName: '${settings.autoDetectedCountryName}'")
            Log.i("PrayerSettingsDialog", "   autoDetectedCountryCode: '${settings.autoDetectedCountryCode}'")

            // Call repository to check if restore option should be shown
            val shouldShow = repository.shouldShowRestoreOption()

            Log.i("PrayerSettingsDialog", "   Restore option result: ${if (shouldShow) "SHOW" else "HIDE"}")
            Log.i("PrayerSettingsDialog", "   hasSettingsChanged will be set to: $shouldShow")
            Log.i("PrayerSettingsDialog", "   UI condition check: hasSettingsChanged=$shouldShow && autoDetectedCountryName='${settings.autoDetectedCountryName}' (isNotEmpty=${settings.autoDetectedCountryName?.isNotEmpty()})")
            hasSettingsChanged = shouldShow
        } else {
            hasSettingsChanged = false
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
    
    // Direct background coverage to completely prevent white flash
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = surfaceScale
                scaleY = surfaceScale
                alpha = surfaceAlpha
            },
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    ) {
        // Content container with animations
            // Content animation wrapper
            AnimatedVisibility(
                visible = animateContent,
                enter = fadeIn(
                    animationSpec = tween(
                        durationMillis = 200, // Faster content fade
                        delayMillis = 50, // Shorter delay
                        easing = FastOutSlowInEasing
                    )
                ) + scaleIn(
                    initialScale = 0.97f, // Less dramatic scale
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessHigh
                    )
                )
            ) {
                if (isLoading) {
                    // Enhanced loading state with animation
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        val loadingRotation by rememberInfiniteTransition(label = "loading_rotation").animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(
                                    durationMillis = 1000,
                                    easing = LinearEasing
                                )
                            ),
                            label = "loading_rotation"
                        )
                        
                        CircularProgressIndicator(
                            modifier = Modifier.graphicsLayer {
                                rotationZ = loadingRotation
                            },
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp
                        )
                    }
                } else {
                    PrayerSettingsScreen(
                        settings = settings,
                        onSettingsChanged = { newSettings ->
                            Log.i("PrayerSettingsDialog", "")
                            Log.i("PrayerSettingsDialog", "📝 USER SETTINGS CHANGE DETECTED")
                            Log.i("PrayerSettingsDialog", "=".repeat(70))
                            Log.i("PrayerSettingsDialog", "🔄 Processing user settings modification...")
                            
                            // Log the specific changes
                            if (settings.calculationMethod != newSettings.calculationMethod) {
                                Log.i("PrayerSettingsDialog", "⚙️ Calculation method changed: ${settings.calculationMethod.name} → ${newSettings.calculationMethod.name}")
                            }
                            if (settings.asrMadhhab != newSettings.asrMadhhab) {
                                Log.i("PrayerSettingsDialog", "⚖️ Asr madhhab changed: ${settings.asrMadhhab.name} → ${newSettings.asrMadhhab.name}")
                            }
                            if (settings.customIshaAngle != newSettings.customIshaAngle) {
                                Log.i("PrayerSettingsDialog", "📐 Custom Isha angle changed: ${settings.customIshaAngle}° → ${newSettings.customIshaAngle}°")
                            }
                            if (settings.customFajrAngle != newSettings.customFajrAngle) {
                                Log.i("PrayerSettingsDialog", "📐 Custom Fajr angle changed: ${settings.customFajrAngle}° → ${newSettings.customFajrAngle}°")
                            }
                            
                            // Haptic feedback for settings change
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            Log.i("PrayerSettingsDialog", "📳 Haptic feedback triggered")
                            
                            // ALGORITHM: Update cached_prayer_settings and recalculate
                            GlobalScope.launch(Dispatchers.IO) {
                                val saveStartTime = System.currentTimeMillis()
                                Log.i("PrayerSettingsDialog", "💾 Starting background save operation...")
                                
                                try {
                                    // The repository.updateSettings follows the algorithm:
                                    // 1. Update cached_prayer_settings (JSON in preferences)
                                    // 2. Immediately recalculate prayer times
                                    Log.i("PrayerSettingsDialog", "🔄 Calling repository.updateSettings()...")
                                    repository.updateSettings(newSettings)
                                    Log.i("PrayerSettingsDialog", "✅ Repository update completed successfully")
                                    
                                    // Update local UI state
                                    withContext(Dispatchers.Main) {
                                        settings = newSettings
                                        val totalTime = System.currentTimeMillis() - saveStartTime
                                        Log.i("PrayerSettingsDialog", "✅ Local UI state updated")
                                        Log.i("PrayerSettingsDialog", "⏱️ Complete save operation took ${totalTime}ms")
                                        Log.i("PrayerSettingsDialog", "🎯 Settings change processing completed successfully")
                                        Log.i("PrayerSettingsDialog", "")
                                    }
                                } catch (e: Exception) {
                                    val totalTime = System.currentTimeMillis() - saveStartTime
                                    Log.e("PrayerSettingsDialog", "❌ Settings save failed after ${totalTime}ms", e)
                                    Log.e("PrayerSettingsDialog", "💥 Error details: ${e.message}")
                                    Log.i("PrayerSettingsDialog", "")
                                }
                            }
                        },
                        onBackClick = {
                            Log.i("PrayerSettingsDialog", "🔙 Prayer Settings Dialog CLOSED - changes should now take effect")
                            
                            // Haptic feedback for dismissal
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            
                            // Direct dismissal for instant response
                            onDismiss()
                        },
                showAsDialog = false,
                hasSettingsChanged = hasSettingsChanged,
                        onRestoreClick = {
                            Log.i("PrayerSettingsDialog", "🔄 ALGORITHM: Restore to auto-detected clicked")

                            // Enhanced haptic feedback for restore action
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)

                            // ALGORITHM: Use repository's restore method
                            GlobalScope.launch(Dispatchers.IO) {
                                try {
                                    val success = repository.restoreToAutoDetected()

                                    if (success) {
                                        // Get the restored settings and update UI
                                        val restoredSettings = repository.getCachedPrayerSettings()
                                        if (restoredSettings != null) {
                                            withContext(Dispatchers.Main) {
                                                // Additional haptic feedback for successful restore
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                settings = restoredSettings
                                                Log.i("PrayerSettingsDialog", "✅ Restored to auto-detected settings")
                                            }
                                        }
                                    } else {
                                        Log.w("PrayerSettingsDialog", "⚠️ Restore failed - no auto-detected settings available")
                                    }
                                } catch (e: Exception) {
                                    Log.e("PrayerSettingsDialog", "❌ Failed to restore settings", e)
                                }
                            }
                        },
                        onNotificationSettingsClick = {
                            Log.i("PrayerSettingsDialog", "🔔 Opening notification settings")
                            showNotificationSettings = true
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

    // Show notification settings dialog when requested
    if (showNotificationSettings) {
        NotificationSettingsDialog(
            onDismiss = {
                showNotificationSettings = false
            }
        )
    }
}

// ALGORITHM IMPLEMENTATION COMPLETE
// The Prayer Settings now follow the specified algorithm:
// 1. Initialization: Detect cached country → Load auto-detected → Load cached → Populate UI
// 2. User changes: Update cached_prayer_settings → Recalculate times
// 3. Restore logic: Compare cached vs auto-detected JSON → Show/hide restore option