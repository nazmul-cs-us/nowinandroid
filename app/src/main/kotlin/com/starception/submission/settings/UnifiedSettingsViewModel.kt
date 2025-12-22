package com.starception.submission.settings

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.starception.submission.core.data.repository.UserDataRepository
import com.starception.submission.core.model.data.DarkThemeConfig
import com.starception.submission.core.model.data.ThemeBrand
import com.starception.submission.prayer.model.PrayerNotificationPreferences
import com.starception.submission.prayer.model.PrayerSettings
import com.starception.submission.prayer.repository.PrayerSettingsRepository
import com.starception.submission.prayer.service.PrayerNotificationServiceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Unified Settings ViewModel
 *
 * Combines theme/appearance settings and prayer time settings into a single ViewModel
 * for the unified settings screen.
 */
@HiltViewModel
class UnifiedSettingsViewModel @Inject constructor(
    private val userDataRepository: UserDataRepository,
    private val prayerSettingsRepository: PrayerSettingsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "UnifiedSettingsVM"
    }

    // Section expansion states
    private val _expandedSections = MutableStateFlow(setOf("appearance"))
    val expandedSections: StateFlow<Set<String>> = _expandedSections.asStateFlow()

    // Theme/Appearance settings
    val themeSettings: StateFlow<ThemeSettingsState> = userDataRepository.userData
        .map { userData ->
            ThemeSettingsState(
                brand = userData.themeBrand,
                useDynamicColor = userData.useDynamicColor,
                darkThemeConfig = userData.darkThemeConfig
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ThemeSettingsState()
        )

    // Prayer settings
    private val _prayerSettings = MutableStateFlow(PrayerSettings())
    val prayerSettings: StateFlow<PrayerSettings> = _prayerSettings.asStateFlow()

    // Loading state
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Show restore button
    private val _showRestoreOption = MutableStateFlow(false)
    val showRestoreOption: StateFlow<Boolean> = _showRestoreOption.asStateFlow()

    // Auto-detected country name
    private val _autoDetectedCountryName = MutableStateFlow<String?>(null)
    val autoDetectedCountryName: StateFlow<String?> = _autoDetectedCountryName.asStateFlow()

    // Notification preferences
    private val _notificationPreferences = MutableStateFlow(PrayerNotificationPreferences())
    val notificationPreferences: StateFlow<PrayerNotificationPreferences> = _notificationPreferences.asStateFlow()

    init {
        loadPrayerSettings()
        loadNotificationPreferences()
    }

    private fun loadPrayerSettings() {
        viewModelScope.launch {
            try {
                Log.i(TAG, "Loading prayer settings...")

                // Get cached country
                val cachedCountry = prayerSettingsRepository.getCachedCountry()
                Log.i(TAG, "Cached country: $cachedCountry")

                // Get auto-detected settings for that country
                val autoDetectedSettings = cachedCountry?.let {
                    prayerSettingsRepository.getAutoDetectedSettingsForCountry(it)
                }

                if (autoDetectedSettings != null) {
                    _autoDetectedCountryName.value = autoDetectedSettings.autoDetectedCountryName
                }

                // Load current calculation settings
                val currentSettings = prayerSettingsRepository.getLoadedCalculationSettings()

                // Create PrayerSettings from current calculation settings
                val settings = PrayerSettings(
                    calculationMethod = currentSettings.calculationMethod,
                    asrMadhhab = currentSettings.asrMadhhab,
                    highLatitudeAdjustment = currentSettings.highLatitudeAdjustment,
                    customFajrAngle = currentSettings.customFajrAngle,
                    customIshaAngle = currentSettings.customIshaAngle,
                    customIshaDelay = currentSettings.customIshaDelay,
                    customMaghribOffset = currentSettings.customMaghribOffset,
                    timeOffsets = currentSettings.timeOffsets,
                    autoDetectedCountryName = autoDetectedSettings?.autoDetectedCountryName,
                    autoDetectedCountryCode = autoDetectedSettings?.autoDetectedCountryCode,
                    isMethodAutoDetected = autoDetectedSettings?.isMethodAutoDetected ?: false,
                    isMadhhabAutoDetected = autoDetectedSettings?.isMadhhabAutoDetected ?: false,
                    areCustomAnglesAutoDetected = autoDetectedSettings?.areCustomAnglesAutoDetected ?: false
                )

                _prayerSettings.value = settings
                _showRestoreOption.value = prayerSettingsRepository.shouldShowRestoreOption()
                _isLoading.value = false

                Log.i(TAG, "Prayer settings loaded successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading prayer settings", e)
                _isLoading.value = false
            }
        }
    }

    // Section management - accordion behavior (only one section expanded at a time)
    fun toggleSection(sectionId: String) {
        _expandedSections.value = if (_expandedSections.value.contains(sectionId)) {
            // Collapse if already expanded
            emptySet()
        } else {
            // Expand only this section (collapse all others)
            setOf(sectionId)
        }
    }

    fun isSectionExpanded(sectionId: String): Boolean {
        return _expandedSections.value.contains(sectionId)
    }

    // Theme settings updates
    fun updateThemeBrand(themeBrand: ThemeBrand) {
        Log.d(TAG, "Updating theme brand to: $themeBrand")
        viewModelScope.launch {
            userDataRepository.setThemeBrand(themeBrand)
        }
    }

    fun updateDarkThemeConfig(darkThemeConfig: DarkThemeConfig) {
        Log.d(TAG, "Updating dark theme config to: $darkThemeConfig")
        viewModelScope.launch {
            userDataRepository.setDarkThemeConfig(darkThemeConfig)
        }
    }

    fun updateDynamicColorPreference(useDynamicColor: Boolean) {
        Log.d(TAG, "Updating dynamic color preference to: $useDynamicColor")
        viewModelScope.launch {
            userDataRepository.setDynamicColorPreference(useDynamicColor)
        }
    }

    // Prayer settings updates
    fun updatePrayerSettings(settings: PrayerSettings) {
        viewModelScope.launch {
            _prayerSettings.value = settings
            prayerSettingsRepository.updateSettings(settings)
            _showRestoreOption.value = prayerSettingsRepository.shouldShowRestoreOption()
        }
    }

    fun restoreAutoDetectedSettings() {
        viewModelScope.launch {
            prayerSettingsRepository.restoreToAutoDetected()
            loadPrayerSettings() // Reload after restore
        }
    }

    // Notification preferences
    private fun loadNotificationPreferences() {
        viewModelScope.launch {
            try {
                Log.i(TAG, "Loading notification preferences...")
                val prefs = prayerSettingsRepository.getNotificationPreferences()
                _notificationPreferences.value = prefs
                Log.i(TAG, "Notification preferences loaded: enabled=${prefs.notificationsEnabled}")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading notification preferences", e)
            }
        }
    }

    fun updateNotificationPreferences(preferences: PrayerNotificationPreferences) {
        viewModelScope.launch {
            _notificationPreferences.value = preferences
            prayerSettingsRepository.updateNotificationPreferences(preferences, forceCommit = true)
            Log.i(TAG, "Notification preferences updated")

            // Trigger notification reschedule
            PrayerNotificationServiceManager.rescheduleNotificationsWithNewSettings(context)
            Log.i(TAG, "Triggered notification reschedule")
        }
    }
}

/**
 * Theme settings state
 */
data class ThemeSettingsState(
    val brand: ThemeBrand = ThemeBrand.DEFAULT,
    val useDynamicColor: Boolean = false,
    val darkThemeConfig: DarkThemeConfig = DarkThemeConfig.FOLLOW_SYSTEM
)
