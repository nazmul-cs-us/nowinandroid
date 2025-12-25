package com.starception.submission.settings

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.starception.submission.core.data.repository.UserDataRepository
import com.starception.submission.core.duadatabase.DuaDatabase
import com.starception.submission.core.quranicduas.QuranicDuaDatabase
import com.starception.submission.core.sync.DatabaseSyncHelper
import com.starception.submission.core.model.data.DarkThemeConfig
import com.starception.submission.core.model.data.ThemeBrand
import com.starception.submission.core.newsdatabase.NewsDatabase
import com.starception.submission.core.topicsdatabase.TopicsDatabase
import com.starception.submission.prayer.model.PrayerNotificationPreferences
import com.starception.submission.prayer.model.PrayerSettings
import com.starception.submission.prayer.repository.PrayerSettingsRepository
import com.starception.submission.prayer.service.PrayerNotificationServiceManager
import com.starception.submission.settings.components.DatabaseDisplayInfo
import com.starception.submission.settings.components.DeveloperSettingsState
import com.starception.submission.settings.components.RefreshResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    // Developer settings state
    private val _developerSettings = MutableStateFlow(DeveloperSettingsState())
    val developerSettings: StateFlow<DeveloperSettingsState> = _developerSettings.asStateFlow()

    init {
        loadPrayerSettings()
        loadNotificationPreferences()
        loadDatabaseInfo()
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

    // ============= Developer Settings =============

    /**
     * Load database info for developer display
     */
    private fun loadDatabaseInfo() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Loading database info...")
                withContext(Dispatchers.IO) {
                    val newsInfo = NewsDatabase.getDatabaseInfo(context)
                    val topicsInfo = TopicsDatabase.getDatabaseInfo(context)
                    val duasInfo = DuaDatabase.getDatabaseInfo(context)
                    val quranicDuasInfo = QuranicDuaDatabase.getDatabaseInfo(context)

                    _developerSettings.value = _developerSettings.value.copy(
                        newsInfo = DatabaseDisplayInfo(
                            name = newsInfo.name,
                            itemCount = newsInfo.itemCount,
                            itemLabel = "news items",
                            lastModified = newsInfo.lastModified,
                            sizeBytes = newsInfo.sizeBytes
                        ),
                        topicsInfo = DatabaseDisplayInfo(
                            name = topicsInfo.name,
                            itemCount = topicsInfo.itemCount,
                            itemLabel = "topics",
                            lastModified = topicsInfo.lastModified,
                            sizeBytes = topicsInfo.sizeBytes
                        ),
                        duasInfo = DatabaseDisplayInfo(
                            name = duasInfo.name,
                            itemCount = duasInfo.duaCount,
                            itemLabel = "duas",
                            lastModified = duasInfo.lastModified,
                            sizeBytes = duasInfo.sizeBytes
                        ),
                        quranicDuasInfo = DatabaseDisplayInfo(
                            name = quranicDuasInfo.name,
                            itemCount = quranicDuasInfo.itemCount,
                            itemLabel = "duas",
                            lastModified = quranicDuasInfo.lastModified,
                            sizeBytes = quranicDuasInfo.sizeBytes
                        )
                    )
                }
                Log.d(TAG, "Database info loaded successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading database info", e)
            }
        }
    }

    /**
     * Refresh the News database from assets
     */
    fun refreshNewsDatabase() {
        viewModelScope.launch {
            try {
                _developerSettings.value = _developerSettings.value.copy(
                    isRefreshing = true,
                    refreshingDatabase = "news",
                    lastRefreshResult = null
                )

                val success = withContext(Dispatchers.IO) {
                    NewsDatabase.refreshFromAssets(context)
                }

                _developerSettings.value = _developerSettings.value.copy(
                    isRefreshing = false,
                    refreshingDatabase = null,
                    lastRefreshResult = RefreshResult(
                        databaseName = "News",
                        success = success,
                        message = if (success) "News database refreshed successfully" else "Failed to refresh News database"
                    )
                )

                // Reload database info
                loadDatabaseInfo()

                // Clear result after delay
                delay(3000)
                _developerSettings.value = _developerSettings.value.copy(lastRefreshResult = null)

            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing news database", e)
                _developerSettings.value = _developerSettings.value.copy(
                    isRefreshing = false,
                    refreshingDatabase = null,
                    lastRefreshResult = RefreshResult(
                        databaseName = "News",
                        success = false,
                        message = "Error: ${e.message}"
                    )
                )
            }
        }
    }

    /**
     * Refresh the Topics database from assets
     */
    fun refreshTopicsDatabase() {
        viewModelScope.launch {
            try {
                _developerSettings.value = _developerSettings.value.copy(
                    isRefreshing = true,
                    refreshingDatabase = "topics",
                    lastRefreshResult = null
                )

                val success = withContext(Dispatchers.IO) {
                    TopicsDatabase.refreshFromAssets(context)
                }

                _developerSettings.value = _developerSettings.value.copy(
                    isRefreshing = false,
                    refreshingDatabase = null,
                    lastRefreshResult = RefreshResult(
                        databaseName = "Topics",
                        success = success,
                        message = if (success) "Topics database refreshed successfully" else "Failed to refresh Topics database"
                    )
                )

                // Reload database info
                loadDatabaseInfo()

                // Clear result after delay
                delay(3000)
                _developerSettings.value = _developerSettings.value.copy(lastRefreshResult = null)

            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing topics database", e)
                _developerSettings.value = _developerSettings.value.copy(
                    isRefreshing = false,
                    refreshingDatabase = null,
                    lastRefreshResult = RefreshResult(
                        databaseName = "Topics",
                        success = false,
                        message = "Error: ${e.message}"
                    )
                )
            }
        }
    }

    /**
     * Refresh the Duas database from assets
     */
    fun refreshDuasDatabase() {
        viewModelScope.launch {
            try {
                _developerSettings.value = _developerSettings.value.copy(
                    isRefreshing = true,
                    refreshingDatabase = "duas",
                    lastRefreshResult = null
                )

                val success = withContext(Dispatchers.IO) {
                    DuaDatabase.refreshFromAssets(context)
                }

                _developerSettings.value = _developerSettings.value.copy(
                    isRefreshing = false,
                    refreshingDatabase = null,
                    lastRefreshResult = RefreshResult(
                        databaseName = "Duas",
                        success = success,
                        message = if (success) "Duas database refreshed successfully" else "Failed to refresh Duas database"
                    )
                )

                // Reload database info
                loadDatabaseInfo()

                // Clear result after delay
                delay(3000)
                _developerSettings.value = _developerSettings.value.copy(lastRefreshResult = null)

            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing duas database", e)
                _developerSettings.value = _developerSettings.value.copy(
                    isRefreshing = false,
                    refreshingDatabase = null,
                    lastRefreshResult = RefreshResult(
                        databaseName = "Duas",
                        success = false,
                        message = "Error: ${e.message}"
                    )
                )
            }
        }
    }

    /**
     * Refresh the Quranic Duas database from assets
     */
    fun refreshQuranicDuasDatabase() {
        viewModelScope.launch {
            try {
                _developerSettings.value = _developerSettings.value.copy(
                    isRefreshing = true,
                    refreshingDatabase = "quranic_duas",
                    lastRefreshResult = null
                )

                val success = withContext(Dispatchers.IO) {
                    QuranicDuaDatabase.refreshFromAssets(context)
                }

                _developerSettings.value = _developerSettings.value.copy(
                    isRefreshing = false,
                    refreshingDatabase = null,
                    lastRefreshResult = RefreshResult(
                        databaseName = "Quranic Duas",
                        success = success,
                        message = if (success) "Quranic Duas database refreshed successfully" else "Failed to refresh Quranic Duas database"
                    )
                )

                // Reload database info
                loadDatabaseInfo()

                // Clear result after delay
                delay(3000)
                _developerSettings.value = _developerSettings.value.copy(lastRefreshResult = null)

            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing Quranic Duas database", e)
                _developerSettings.value = _developerSettings.value.copy(
                    isRefreshing = false,
                    refreshingDatabase = null,
                    lastRefreshResult = RefreshResult(
                        databaseName = "Quranic Duas",
                        success = false,
                        message = "Error: ${e.message}"
                    )
                )
            }
        }
    }

    /**
     * Refresh all databases from assets
     */
    fun refreshAllDatabases() {
        viewModelScope.launch {
            try {
                _developerSettings.value = _developerSettings.value.copy(
                    isRefreshing = true,
                    refreshingDatabase = "all",
                    lastRefreshResult = null
                )

                data class RefreshResults(
                    val news: Boolean,
                    val topics: Boolean,
                    val duas: Boolean,
                    val quranicDuas: Boolean,
                    val syncResult: com.starception.submission.core.sync.SyncResult?
                )

                val results = withContext(Dispatchers.IO) {
                    // First refresh all databases from assets
                    val newsSuccess = NewsDatabase.refreshFromAssets(context)
                    val topicsSuccess = TopicsDatabase.refreshFromAssets(context)
                    val duasSuccess = DuaDatabase.refreshFromAssets(context)
                    val quranicDuasSuccess = QuranicDuaDatabase.refreshFromAssets(context)

                    // Then sync duas to news database
                    val syncResult = if (duasSuccess && quranicDuasSuccess && newsSuccess) {
                        DatabaseSyncHelper.syncDuasToNews(context)
                    } else {
                        null
                    }

                    RefreshResults(newsSuccess, topicsSuccess, duasSuccess, quranicDuasSuccess, syncResult)
                }

                val allSuccess = results.news && results.topics && results.duas && results.quranicDuas
                val syncInfo = results.syncResult?.let { " | Synced: ${it.quranicDuasSynced + it.fortressDuasSynced} duas" } ?: ""
                val message = buildString {
                    append("News: ${if (results.news) "OK" else "FAILED"}")
                    append(" | Topics: ${if (results.topics) "OK" else "FAILED"}")
                    append(" | Duas: ${if (results.duas) "OK" else "FAILED"}")
                    append(" | Quranic: ${if (results.quranicDuas) "OK" else "FAILED"}")
                    append(syncInfo)
                }

                _developerSettings.value = _developerSettings.value.copy(
                    isRefreshing = false,
                    refreshingDatabase = null,
                    lastRefreshResult = RefreshResult(
                        databaseName = "All",
                        success = allSuccess,
                        message = if (allSuccess) "All databases refreshed successfully" else message
                    )
                )

                // Reload database info
                loadDatabaseInfo()

                // Clear result after delay
                delay(3000)
                _developerSettings.value = _developerSettings.value.copy(lastRefreshResult = null)

            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing all databases", e)
                _developerSettings.value = _developerSettings.value.copy(
                    isRefreshing = false,
                    refreshingDatabase = null,
                    lastRefreshResult = RefreshResult(
                        databaseName = "All",
                        success = false,
                        message = "Error: ${e.message}"
                    )
                )
            }
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
