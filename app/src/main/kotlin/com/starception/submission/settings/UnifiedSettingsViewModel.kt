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
import com.starception.submission.core.contentdatabase.NewsDatabase
import com.starception.submission.core.topicsdatabase.TopicsDatabase
import com.starception.submission.core.contentdatabase.TopicsDatabase as ContentTopicsDatabase
import com.starception.submission.config.TravelDuaSettings
import com.starception.submission.prayer.model.PrayerNotificationPreferences
import com.starception.submission.prayer.model.PrayerSettings
import com.starception.submission.prayer.repository.PrayerSettingsRepository
import com.starception.submission.util.ActivityTracker
import com.starception.submission.prayer.service.PrayerNotificationServiceManager
import com.starception.submission.settings.components.DatabaseDisplayInfo
import com.starception.submission.settings.components.DeveloperSettingsState
import com.starception.submission.settings.components.RefreshResult
import com.starception.submission.settings.components.VoiceRecognitionEngine
import com.starception.submission.settings.components.VoiceSettingsState
import com.starception.submission.settings.components.VoiceTestState
import com.starception.submission.settings.components.TtsSettingsState
import com.starception.submission.settings.components.TtsTestState
import com.starception.submission.settings.components.TtsVoice
import com.starception.submission.settings.components.TTS_VOICE_SAMPLE_TEXT
import com.starception.submission.download.AssetDownloadManager
import com.starception.submission.download.AssetDownloadViewModel
import com.starception.submission.download.CategoryDownloadState
import com.starception.submission.voice.SherpaOnnxKwsService
import com.starception.submission.voice.WhisperVoiceService
import com.starception.submission.voice.SherpaOnnxTtsService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private val whisperVoiceService: WhisperVoiceService,
    private val sherpaOnnxKwsService: SherpaOnnxKwsService,
    private val ttsService: SherpaOnnxTtsService,
    private val downloadManager: AssetDownloadManager,
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
                darkThemeConfig = userData.darkThemeConfig,
                customColor = userData.customThemeColor,
                customSecondaryColor = userData.customSecondaryColor,
                customTertiaryColor = userData.customTertiaryColor,
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

    // Travel Dua settings
    private val _travelDuaSettings = MutableStateFlow(TravelDuaSettings())
    val travelDuaSettings: StateFlow<TravelDuaSettings> = _travelDuaSettings.asStateFlow()

    // Audio chain playing state
    private val _isAudioChainPlaying = MutableStateFlow(false)
    val isAudioChainPlaying: StateFlow<Boolean> = _isAudioChainPlaying.asStateFlow()

    private var contentRefreshJob: Job? = null

    // Developer settings state
    private val _developerSettings = MutableStateFlow(DeveloperSettingsState())
    val developerSettings: StateFlow<DeveloperSettingsState> = _developerSettings.asStateFlow()

    // Voice settings state
    private val _voiceSettings = MutableStateFlow(VoiceSettingsState())
    val voiceSettings: StateFlow<VoiceSettingsState> = _voiceSettings.asStateFlow()
    private var voiceTestSessionId = 0L

    // TTS settings
    private val _ttsSettings = MutableStateFlow(TtsSettingsState())
    val ttsSettings: StateFlow<TtsSettingsState> = _ttsSettings.asStateFlow()

    // Content management
    private val _contentCategories = MutableStateFlow<List<CategoryDownloadState>>(emptyList())
    val contentCategories: StateFlow<List<CategoryDownloadState>> = _contentCategories.asStateFlow()

    private val _totalDownloadedSize = MutableStateFlow(0L)
    val totalDownloadedSize: StateFlow<Long> = _totalDownloadedSize.asStateFlow()

    init {
        loadPrayerSettings()
        loadNotificationPreferences()
        loadTravelDuaSettings()
        loadDatabaseInfo()
        loadVoiceSettings()
        loadTtsSettings()
        loadContentManagement()
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

    fun expandSection(sectionId: String) {
        _expandedSections.value = setOf(sectionId)
    }

    fun consumePendingSectionRequest(): String? {
        val prefs = context.getSharedPreferences("content_prompt_prefs", Context.MODE_PRIVATE)
        val section = prefs.getString("pending_settings_section", null)
        if (section != null) {
            prefs.edit().remove("pending_settings_section").apply()
        }
        return section
    }

    // Theme settings updates
    fun updateThemeBrand(themeBrand: ThemeBrand) {
        Log.d(TAG, "Updating theme brand to: $themeBrand")
        viewModelScope.launch {
            userDataRepository.setThemeBrand(themeBrand)
        }
    }

    /** Saves all 3 HSV-picked accents and switches the active brand to CUSTOM. */
    fun updateCustomThemeColors(primary: Int, secondary: Int, tertiary: Int) {
        viewModelScope.launch {
            userDataRepository.setCustomThemeColors(primary, secondary, tertiary)
            userDataRepository.setThemeBrand(ThemeBrand.CUSTOM)
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

    // ============= Travel Dua Settings =============

    private fun loadTravelDuaSettings() {
        viewModelScope.launch {
            try {
                Log.i(TAG, "Loading travel dua settings...")
                val prefs = context.getSharedPreferences(TravelDuaSettings.PREFS_NAME, Context.MODE_PRIVATE)
                val settings = TravelDuaSettings(
                    enabled = prefs.getBoolean(TravelDuaSettings.KEY_ENABLED, true),
                    cooldownMinutes = prefs.getInt(TravelDuaSettings.KEY_COOLDOWN_MINUTES, TravelDuaSettings.DEFAULT_COOLDOWN_MINUTES),
                    playbackDelaySeconds = prefs.getInt(TravelDuaSettings.KEY_PLAYBACK_DELAY_SECONDS, TravelDuaSettings.DEFAULT_PLAYBACK_DELAY_SECONDS),
                    gapToleranceMinutes = prefs.getInt(TravelDuaSettings.KEY_GAP_TOLERANCE_MINUTES, TravelDuaSettings.DEFAULT_GAP_TOLERANCE_MINUTES),
                    drivingSpeedThresholdKmh = prefs.getInt(TravelDuaSettings.KEY_DRIVING_SPEED_THRESHOLD_KMH, TravelDuaSettings.DEFAULT_DRIVING_SPEED_THRESHOLD_KMH)
                )
                _travelDuaSettings.value = settings
                Log.i(TAG, "Travel dua settings loaded: enabled=${settings.enabled}, cooldown=${settings.cooldownMinutes}min, delay=${settings.playbackDelaySeconds}s, gap=${settings.gapToleranceMinutes}min, speedThreshold=${settings.drivingSpeedThresholdKmh}km/h")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading travel dua settings", e)
            }
        }
    }

    fun updateTravelDuaSettings(settings: TravelDuaSettings) {
        viewModelScope.launch {
            try {
                _travelDuaSettings.value = settings

                // Save to SharedPreferences
                val prefs = context.getSharedPreferences(TravelDuaSettings.PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit()
                    .putBoolean(TravelDuaSettings.KEY_ENABLED, settings.enabled)
                    .putInt(TravelDuaSettings.KEY_COOLDOWN_MINUTES, settings.cooldownMinutes)
                    .putInt(TravelDuaSettings.KEY_PLAYBACK_DELAY_SECONDS, settings.playbackDelaySeconds)
                    .putInt(TravelDuaSettings.KEY_GAP_TOLERANCE_MINUTES, settings.gapToleranceMinutes)
                    .putInt(TravelDuaSettings.KEY_DRIVING_SPEED_THRESHOLD_KMH, settings.drivingSpeedThresholdKmh)
                    .apply()

                Log.i(TAG, "Travel dua settings saved: enabled=${settings.enabled}, cooldown=${settings.cooldownMinutes}min, delay=${settings.playbackDelaySeconds}s, gap=${settings.gapToleranceMinutes}min, speedThreshold=${settings.drivingSpeedThresholdKmh}km/h")

                // Update ActivityTracker with new settings
                ActivityTracker.updateTravelDuaSettings(settings)
                Log.i(TAG, "ActivityTracker updated with new travel dua settings")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving travel dua settings", e)
            }
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
     * Refresh the News database by regenerating from source databases
     * (quran.db, fortress_of_the_muslim_v2.db, quranic_duas.db)
     */
    fun refreshNewsDatabase() {
        viewModelScope.launch {
            try {
                _developerSettings.value = _developerSettings.value.copy(
                    isRefreshing = true,
                    refreshingDatabase = "news",
                    lastRefreshResult = null
                )

                val result = withContext(Dispatchers.IO) {
                    NewsDatabase.regenerateFromSources(context)
                }

                _developerSettings.value = _developerSettings.value.copy(
                    isRefreshing = false,
                    refreshingDatabase = null,
                    lastRefreshResult = RefreshResult(
                        databaseName = "News",
                        success = result.success,
                        message = if (result.success) {
                            "Regenerated: ${result.surahCount} Surahs, ${result.quranicDuaCount} Quranic Duas, ${result.fortressDuaCount} Fortress Duas (${result.durationMs}ms)"
                        } else {
                            "Failed: ${result.error}"
                        }
                    )
                )

                // Reload database info
                loadDatabaseInfo()

                // Clear result after delay
                delay(5000)
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
                    val result = TopicsDatabase.refreshFromAssets(context)
                    // Also refresh the contentdatabase TopicsDatabase singleton
                    ContentTopicsDatabase.refreshFromAssets(context)
                    result
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
     * Refresh all databases - regenerates news.db from source databases
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
                    val topicsSuccess: Boolean,
                    val duasSuccess: Boolean,
                    val quranicDuasSuccess: Boolean,
                    val newsRegenResult: com.starception.submission.core.contentdatabase.RegenerationResult
                )

                val results = withContext(Dispatchers.IO) {
                    // First refresh source databases from assets using Room DAO operations
                    // This keeps database connections alive so Flows continue to work
                    val topicsSuccess = TopicsDatabase.refreshFromAssets(context)
                    // Also refresh the contentdatabase TopicsDatabase singleton
                    val contentTopicsSuccess = ContentTopicsDatabase.refreshFromAssets(context)
                    val duasSuccess = DuaDatabase.refreshFromAssets(context)
                    val quranicDuasSuccess = QuranicDuaDatabase.refreshFromAssets(context)

                    // Then regenerate news.db from all source databases
                    // This creates Surahs, Quranic Duas, and Fortress Duas
                    val newsRegenResult = NewsDatabase.regenerateFromSources(context)

                    Log.d(TAG, "Database refresh results: topics=$topicsSuccess, contentTopics=$contentTopicsSuccess, duas=$duasSuccess, quranic=$quranicDuasSuccess, news=${newsRegenResult.success}")

                    RefreshResults(topicsSuccess && contentTopicsSuccess, duasSuccess, quranicDuasSuccess, newsRegenResult)
                }

                val allSuccess = results.topicsSuccess && results.duasSuccess &&
                    results.quranicDuasSuccess && results.newsRegenResult.success

                val message = if (allSuccess) {
                    "All refreshed: ${results.newsRegenResult.surahCount} Surahs, " +
                    "${results.newsRegenResult.quranicDuaCount} Quranic Duas, " +
                    "${results.newsRegenResult.fortressDuaCount} Fortress Duas"
                } else {
                    buildString {
                        append("Topics: ${if (results.topicsSuccess) "OK" else "FAILED"}")
                        append(" | Duas: ${if (results.duasSuccess) "OK" else "FAILED"}")
                        append(" | Quranic: ${if (results.quranicDuasSuccess) "OK" else "FAILED"}")
                        append(" | News: ${if (results.newsRegenResult.success) "OK" else "FAILED"}")
                    }
                }

                _developerSettings.value = _developerSettings.value.copy(
                    isRefreshing = false,
                    refreshingDatabase = null,
                    lastRefreshResult = RefreshResult(
                        databaseName = "All",
                        success = allSuccess,
                        message = message
                    )
                )

                // Reload database info
                loadDatabaseInfo()

                // Clear result after delay
                delay(5000)
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

    /**
     * Test fast keyword spotting (Sherpa-ONNX KWS ~100ms)
     * Say "YES" or "NO" when prompted!
     */
    fun testFastKws() {
        Log.i(TAG, "🧪 Testing Fast KWS from Settings...")
        ActivityTracker.testFastKws()
    }

    /**
     * Test Whisper voice completion (~26 seconds)
     * Say "YES" or "NO" when prompted!
     */
    fun testVoiceCompletion() {
        Log.i(TAG, "🧪 Testing Voice Completion (Whisper) from Settings...")
        ActivityTracker.testVoiceCompletion()
    }

    /**
     * Manually trigger the full audio chain: Travel Dua → Hadith → Quran
     * This plays the same sequence that would play during driving.
     */
    fun triggerFullAudioChain() {
        Log.i(TAG, "🎵 Triggering full audio chain from Settings...")
        _isAudioChainPlaying.value = true
        ActivityTracker.triggerFullAudioChain()
    }

    /**
     * Stop the currently playing audio chain.
     */
    fun stopAudioChain() {
        Log.i(TAG, "⏹️ Stopping audio chain from Settings...")
        _isAudioChainPlaying.value = false

        // Stop DrivingAudioService
        try {
            val stopIntent = android.content.Intent(context, com.starception.submission.services.DrivingAudioService::class.java).apply {
                action = com.starception.submission.services.DrivingAudioService.ACTION_STOP
            }
            context.startService(stopIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio chain", e)
        }
    }

    /**
     * Called when audio chain playback completes naturally.
     */
    fun onAudioChainComplete() {
        _isAudioChainPlaying.value = false
    }

    // ============= Voice Settings =============

    private fun loadVoiceSettings() {
        viewModelScope.launch {
            try {
                Log.i(TAG, "Loading voice settings...")
                val prefs = context.getSharedPreferences("voice_settings", Context.MODE_PRIVATE)
                val engineName = prefs.getString("voice_engine", VoiceRecognitionEngine.SHERPA_KWS.name)
                val engine = try {
                    VoiceRecognitionEngine.valueOf(engineName ?: VoiceRecognitionEngine.SHERPA_KWS.name)
                } catch (e: Exception) {
                    VoiceRecognitionEngine.SHERPA_KWS
                }
                val needsDownload = !checkVoiceModelAvailable(engine)
                val downloadCategory = if (needsDownload) voiceEngineDownloadCategory(engine) else null
                _voiceSettings.value = VoiceSettingsState(
                    selectedEngine = engine,
                    needsDownload = needsDownload,
                    downloadCategory = downloadCategory,
                )
                Log.i(TAG, "Voice settings loaded: engine=${engine.name}, needsDownload=$needsDownload")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading voice settings", e)
            }
        }
    }

    fun updateVoiceSettings(engine: VoiceRecognitionEngine) {
        viewModelScope.launch {
            try {
                val needsDownload = !checkVoiceModelAvailable(engine)
                val downloadCategory = if (needsDownload) voiceEngineDownloadCategory(engine) else null
                _voiceSettings.value = _voiceSettings.value.copy(
                    selectedEngine = engine,
                    needsDownload = needsDownload,
                    downloadCategory = downloadCategory,
                )

                // Save to SharedPreferences
                val prefs = context.getSharedPreferences("voice_settings", Context.MODE_PRIVATE)
                prefs.edit()
                    .putString("voice_engine", engine.name)
                    .apply()

                Log.i(TAG, "Voice settings saved: engine=${engine.name}, needsDownload=$needsDownload")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving voice settings", e)
            }
        }
    }

    // ============= TTS Settings =============

    /**
     * Load TTS settings from SharedPreferences
     */
    private fun loadTtsSettings() {
        viewModelScope.launch {
            try {
                Log.i(TAG, "Loading TTS settings...")
                val prefs = context.getSharedPreferences("tts_settings", Context.MODE_PRIVATE)
                val voiceName = prefs.getString("selected_voice", TtsVoice.KOKORO_EN.name)
                val speakerId = prefs.getInt("selected_speaker_id", 0)

                val voice = try {
                    TtsVoice.valueOf(voiceName ?: TtsVoice.KOKORO_EN.name)
                } catch (e: Exception) {
                    TtsVoice.KOKORO_EN
                }

                val needsDownload = !checkTtsModelAvailable(voice)
                val downloadCategory = if (needsDownload) ttsVoiceDownloadCategory(voice) else null
                _ttsSettings.value = _ttsSettings.value.copy(
                    selectedVoice = voice,
                    selectedSpeakerId = speakerId,
                    needsDownload = needsDownload,
                    downloadCategory = downloadCategory,
                )
                // Keep application-level consumers (including Dua announcements) aligned
                // with the persisted choice even when Settings has only just been opened.
                ttsService.setVoice(voice)
                Log.i(TAG, "TTS settings loaded: voice=${voice.name}, speakerId=$speakerId, needsDownload=$needsDownload")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading TTS settings", e)
            }
        }
    }

    /**
     * Start interactive voice recognition test
     * Uses the currently selected recognition engine (Sherpa KWS or Whisper)
     */
    fun startVoiceTest() {
        viewModelScope.launch {
            Log.i(TAG, "🎤 Starting voice test...")
            val sessionId = ++voiceTestSessionId

            // Update state to listening
            _voiceSettings.value = _voiceSettings.value.copy(
                testState = VoiceTestState.LISTENING,
                testResult = null,
                testError = null
            )

            when (_voiceSettings.value.selectedEngine) {
                VoiceRecognitionEngine.SHERPA_KWS -> {
                    Log.i(TAG, "🎤 Voice test engine: SHERPA_KWS")
                    sherpaOnnxKwsService.startListening(
                        durationMs = 5000L,
                        callback = object : SherpaOnnxKwsService.VoiceRecognitionCallback {
                            override fun onResult(result: SherpaOnnxKwsService.VoiceResult) {
                                viewModelScope.launch {
                                    if (sessionId != voiceTestSessionId) return@launch
                                    when (result) {
                                        is SherpaOnnxKwsService.VoiceResult.Yes -> {
                                            Log.i(TAG, "🎤 Voice test result: YES")
                                            _voiceSettings.value = _voiceSettings.value.copy(
                                                testState = VoiceTestState.SUCCESS,
                                                testResult = "Yes"
                                            )
                                        }
                                        is SherpaOnnxKwsService.VoiceResult.No -> {
                                            Log.i(TAG, "🎤 Voice test result: NO")
                                            _voiceSettings.value = _voiceSettings.value.copy(
                                                testState = VoiceTestState.SUCCESS,
                                                testResult = "No"
                                            )
                                        }
                                        is SherpaOnnxKwsService.VoiceResult.Unrecognized -> {
                                            Log.i(TAG, "🎤 Voice test result: ${result.text}")
                                            _voiceSettings.value = _voiceSettings.value.copy(
                                                testState = VoiceTestState.SUCCESS,
                                                testResult = result.text
                                            )
                                        }
                                        is SherpaOnnxKwsService.VoiceResult.Timeout -> {
                                            Log.w(TAG, "🎤 Voice test timeout")
                                            _voiceSettings.value = _voiceSettings.value.copy(
                                                testState = VoiceTestState.ERROR,
                                                testError = "No speech detected"
                                            )
                                        }
                                        is SherpaOnnxKwsService.VoiceResult.Error -> {
                                            Log.e(TAG, "🎤 Voice test error: ${result.message}")
                                            _voiceSettings.value = _voiceSettings.value.copy(
                                                testState = VoiceTestState.ERROR,
                                                testError = result.message
                                            )
                                        }
                                    }

                                    delay(3000)
                                    if (sessionId == voiceTestSessionId) {
                                        _voiceSettings.value = _voiceSettings.value.copy(
                                            testState = VoiceTestState.IDLE
                                        )
                                    }
                                }
                            }

                            override fun onListeningStarted() {
                                Log.i(TAG, "🎤 Voice test listening started")
                            }

                            override fun onListeningStopped() {
                                Log.i(TAG, "🎤 Voice test listening stopped")
                                viewModelScope.launch {
                                    if (
                                        sessionId == voiceTestSessionId &&
                                        _voiceSettings.value.testState == VoiceTestState.LISTENING
                                    ) {
                                        _voiceSettings.value = _voiceSettings.value.copy(
                                            testState = VoiceTestState.PROCESSING
                                        )
                                    }
                                }
                            }

                            override fun onStatusUpdate(message: String) {
                                Log.i(TAG, "🎤 Voice test status: $message")
                            }
                        }
                    )
                }

                VoiceRecognitionEngine.WHISPER -> {
                    Log.i(TAG, "🎤 Voice test engine: WHISPER")
                    whisperVoiceService.startListening(
                        durationMs = 5000L,
                        callback = object : WhisperVoiceService.VoiceRecognitionCallback {
                            override fun onResult(result: WhisperVoiceService.VoiceResult) {
                                viewModelScope.launch {
                                    if (sessionId != voiceTestSessionId) return@launch
                                    when (result) {
                                        is WhisperVoiceService.VoiceResult.Yes -> {
                                            Log.i(TAG, "🎤 Voice test result: YES")
                                            _voiceSettings.value = _voiceSettings.value.copy(
                                                testState = VoiceTestState.SUCCESS,
                                                testResult = "Yes"
                                            )
                                        }
                                        is WhisperVoiceService.VoiceResult.No -> {
                                            Log.i(TAG, "🎤 Voice test result: NO")
                                            _voiceSettings.value = _voiceSettings.value.copy(
                                                testState = VoiceTestState.SUCCESS,
                                                testResult = "No"
                                            )
                                        }
                                        is WhisperVoiceService.VoiceResult.Unrecognized -> {
                                            Log.i(TAG, "🎤 Voice test result: ${result.text}")
                                            _voiceSettings.value = _voiceSettings.value.copy(
                                                testState = VoiceTestState.SUCCESS,
                                                testResult = result.text
                                            )
                                        }
                                        is WhisperVoiceService.VoiceResult.Timeout -> {
                                            Log.w(TAG, "🎤 Voice test timeout")
                                            _voiceSettings.value = _voiceSettings.value.copy(
                                                testState = VoiceTestState.ERROR,
                                                testError = "No speech detected"
                                            )
                                        }
                                        is WhisperVoiceService.VoiceResult.Error -> {
                                            Log.e(TAG, "🎤 Voice test error: ${result.message}")
                                            _voiceSettings.value = _voiceSettings.value.copy(
                                                testState = VoiceTestState.ERROR,
                                                testError = result.message
                                            )
                                        }
                                    }

                                    delay(3000)
                                    if (sessionId == voiceTestSessionId) {
                                        _voiceSettings.value = _voiceSettings.value.copy(
                                            testState = VoiceTestState.IDLE
                                        )
                                    }
                                }
                            }

                            override fun onListeningStarted() {
                                Log.i(TAG, "🎤 Voice test listening started")
                            }

                            override fun onListeningStopped() {
                                Log.i(TAG, "🎤 Voice test listening stopped")
                                viewModelScope.launch {
                                    if (
                                        sessionId == voiceTestSessionId &&
                                        _voiceSettings.value.testState == VoiceTestState.LISTENING
                                    ) {
                                        _voiceSettings.value = _voiceSettings.value.copy(
                                            testState = VoiceTestState.PROCESSING
                                        )
                                    }
                                }
                            }

                            override fun onStatusUpdate(message: String) {
                                Log.i(TAG, "🎤 Voice test status: $message")
                            }

                            override fun onAmplitudeUpdate(amplitude: Float) {
                                _voiceSettings.value = _voiceSettings.value.copy(amplitude = amplitude)
                            }
                        }
                    )
                }
            }
        }
    }

    /**
     * Stop the voice test
     */
    fun stopVoiceTest() {
        Log.i(TAG, "🎤 Stopping voice test...")
        voiceTestSessionId += 1
        sherpaOnnxKwsService.stopListening()
        whisperVoiceService.stopListening()
        _voiceSettings.value = _voiceSettings.value.copy(
            testState = VoiceTestState.IDLE,
            testResult = null,
            testError = null,
            amplitude = 0f
        )
    }

    // ==================== TTS Settings ====================

    /**
     * Start TTS test - speaks a test phrase
     */
    fun startTtsTest() {
        viewModelScope.launch {
            val currentState = _ttsSettings.value
            Log.i(TAG, "🔊 Starting TTS test with ${currentState.selectedVoice.displayName}, speaker ${currentState.selectedSpeakerId}...")

            // Update state to initializing
            _ttsSettings.value = currentState.copy(
                testState = TtsTestState.INITIALIZING,
                testError = null
            )

            try {
                // Set voice before initializing
                ttsService.setVoice(currentState.selectedVoice)

                // Initialize TTS if needed
                val initialized = ttsService.initialize()
                if (!initialized) {
                    Log.e(TAG, "Failed to initialize TTS")
                    _ttsSettings.value = _ttsSettings.value.copy(
                        testState = TtsTestState.ERROR,
                        testError = "Failed to initialize TTS engine"
                    )
                    return@launch
                }

                // Speak test phrase with selected speaker ID
                val success = ttsService.speak(
                    text = TTS_VOICE_SAMPLE_TEXT,
                    speakerId = currentState.selectedSpeakerId,
                    onPlaybackStart = {
                        _ttsSettings.value = _ttsSettings.value.copy(
                            testState = TtsTestState.SPEAKING,
                        )
                    },
                )

                if (success) {
                    Log.i(TAG, "TTS test completed successfully")
                    _ttsSettings.value = _ttsSettings.value.copy(
                        testState = TtsTestState.SUCCESS
                    )
                } else {
                    Log.e(TAG, "TTS test failed")
                    _ttsSettings.value = _ttsSettings.value.copy(
                        testState = TtsTestState.ERROR,
                        testError = "Failed to generate speech"
                    )
                }

                // Reset to idle after a delay
                delay(2000)
                _ttsSettings.value = _ttsSettings.value.copy(
                    testState = TtsTestState.IDLE
                )

            } catch (e: Exception) {
                Log.e(TAG, "TTS test error", e)
                _ttsSettings.value = _ttsSettings.value.copy(
                    testState = TtsTestState.ERROR,
                    testError = e.message ?: "Unknown error"
                )
            }
        }
    }

    /**
     * Stop TTS playback
     */
    fun stopTts() {
        Log.i(TAG, "🔊 Stopping TTS...")
        ttsService.stopSpeaking()
        _ttsSettings.value = _ttsSettings.value.copy(
            testState = TtsTestState.IDLE,
            testError = null
        )
    }

    /**
     * Update selected TTS voice
     */
    fun updateTtsVoice(voice: TtsVoice) {
        viewModelScope.launch {
            Log.i(TAG, "🔊 Updating TTS voice to: ${voice.displayName}")
            val needsDownload = !checkTtsModelAvailable(voice)
            val downloadCategory = if (needsDownload) ttsVoiceDownloadCategory(voice) else null
            _ttsSettings.value = _ttsSettings.value.copy(
                selectedVoice = voice,
                selectedSpeakerId = 0,  // Reset speaker ID when changing voice
                needsDownload = needsDownload,
                downloadCategory = downloadCategory,
            )

            // Save preference
            val prefs = context.getSharedPreferences("tts_settings", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("selected_voice", voice.name)
                .putInt("selected_speaker_id", 0)
                .apply()

            // Update service with new voice (this will release and prepare for re-init)
            // Cached speech was generated by the previous model/speaker and must not be
            // replayed after the selection changes.
            ttsService.clearCache()
            ttsService.setVoice(voice)
            Log.i(TAG, "TTS voice updated: ${voice.name}, needsDownload=$needsDownload")
        }
    }

    /**
     * Update selected speaker ID (for multi-speaker models)
     */
    fun updateTtsSpeakerId(speakerId: Int) {
        Log.i(TAG, "🔊 Updating TTS speaker ID to: $speakerId")
        _ttsSettings.value = _ttsSettings.value.copy(
            selectedSpeakerId = speakerId
        )

        // Save preference
        val prefs = context.getSharedPreferences("tts_settings", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("selected_speaker_id", speakerId)
            .apply()

        // The pre-generated cache is text-based, so discard clips spoken by the old
        // speaker before other features request the same text again.
        ttsService.clearCache()
    }

    // ==================== Content Management ====================

    private fun loadContentManagement() {
        viewModelScope.launch {
            try {
                val manifest = downloadManager.loadManifest() ?: return@launch
                refreshContentCategories(manifest)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading content management", e)
            }
        }
    }

    private fun refreshContentCategories(manifest: com.starception.submission.download.AssetManifest? = null) {
        contentRefreshJob?.cancel()
        contentRefreshJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val m = manifest ?: downloadManager.loadManifest() ?: return@launch
                val categories = m.categories
                    .filterNot { (key, _) ->
                        // Hide categories that are fully bundled in the APK
                        // since users cannot delete or manage them
                        downloadManager.isCategoryFullyBundled(key, m)
                    }
                    .map { (key, info) ->
                        val downloadedSize = downloadManager.getCategoryDownloadedSize(key, m)
                        val isComplete = downloadManager.isCategoryComplete(key, m)
                        CategoryDownloadState(
                            categoryKey = key,
                            displayName = AssetDownloadViewModel.formatCategoryName(key),
                            description = AssetDownloadViewModel.categoryDescription(key),
                            totalSize = info.totalSize,
                            downloadedSize = downloadedSize,
                            fileCount = info.fileCount,
                            required = info.required,
                            isComplete = isComplete,
                            progress = if (info.totalSize > 0) downloadedSize.toFloat() / info.totalSize else 0f,
                        )
                    }.sortedWith(
                        compareByDescending<CategoryDownloadState> { it.required }
                            .thenBy { it.displayName }
                    )

                val totalDownloadedSize = downloadManager.getTotalDownloadedSize()

                _contentCategories.value = categories
                _totalDownloadedSize.value = totalDownloadedSize
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing content categories", e)
            }
        }
    }

    fun downloadContent(categoryKey: String) {
        viewModelScope.launch {
            try {
                val manifest = downloadManager.loadManifest() ?: return@launch
                downloadManager.launchCategoryDownload(
                    category = categoryKey,
                    manifest = manifest,
                    onProgress = { _, _, _ ->
                        refreshContentCategories(manifest)
                    },
                    onFinished = {
                        refreshContentCategories(manifest)
                    },
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading category $categoryKey", e)
            }
        }
    }

    fun deleteContent(categoryKey: String) {
        viewModelScope.launch {
            try {
                val manifest = downloadManager.loadManifest() ?: return@launch
                downloadManager.deleteCategory(categoryKey, manifest)
                // TTS voices also leave an extracted copy + cached audio that
                // deleteCategory doesn't touch — purge them so the voice really
                // becomes unavailable (play then shows the download prompt).
                val voice = when (categoryKey) {
                    "model_tts_kokoro" -> TtsVoice.KOKORO_EN
                    "model_tts_vits" -> TtsVoice.VITS_VCTK
                    else -> null
                }
                if (voice != null) {
                    withContext(Dispatchers.IO) { ttsService.purgeVoice(voice) }
                }
                refreshContentCategories(manifest)
                // Reflect the new (unavailable) state in the TTS settings card.
                loadTtsSettings()
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting category $categoryKey", e)
            }
        }
    }

    // ==================== Model Availability Checks ====================

    fun getDownloadManager(): AssetDownloadManager = downloadManager

    private fun checkVoiceModelAvailable(engine: VoiceRecognitionEngine): Boolean {
        return try {
            when (engine) {
                VoiceRecognitionEngine.WHISPER -> {
                    // 1. Check CDN download location
                    val cdnFile = java.io.File(java.io.File(context.filesDir, "cdn_assets"), "models/ggml-tiny.en.bin")
                    if (cdnFile.exists() && cdnFile.length() > 1024) return true
                    // 2. Check bundled assets
                    context.assets.open("models/ggml-tiny.en.bin").use { it.available() > 0 }
                }
                VoiceRecognitionEngine.SHERPA_KWS -> {
                    // 1. Check extracted model cache (SherpaOnnxKwsService extracts to kws_model/)
                    val extractedFile = java.io.File(java.io.File(context.filesDir, "kws_model"), "encoder.int8.onnx")
                    if (extractedFile.exists() && extractedFile.length() > 1024) return true
                    // 2. Check CDN download location (CDN key: models/kws/encoder.int8.onnx)
                    val cdnFile = java.io.File(java.io.File(context.filesDir, "cdn_assets"), "models/kws/encoder.int8.onnx")
                    if (cdnFile.exists() && cdnFile.length() > 1024) return true
                    // 3. Check bundled assets
                    context.assets.open("kws/encoder.int8.onnx").use { it.available() > 0 }
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun checkTtsModelAvailable(voice: TtsVoice): Boolean {
        val modelFile = voice.modelFile // e.g. "kokoro-int8-en-v0_19/model.int8.onnx"
        return try {
            // 1. Check previously extracted model cache (same location SherpaOnnxTtsService uses)
            val extractedFile = java.io.File(java.io.File(context.filesDir, "tts_model"), modelFile)
            if (extractedFile.exists() && extractedFile.length() > 1024) {
                return true
            }

            // 2. Check CDN download location
            val cdnFile = java.io.File(java.io.File(context.filesDir, "cdn_assets"), "models/tts/$modelFile")
            if (cdnFile.exists() && cdnFile.length() > 1024) {
                return true
            }

            // 3. Check bundled assets
            context.assets.open("tts/$modelFile").use { it.available() > 0 }
        } catch (e: Exception) {
            false
        }
    }

    private fun voiceEngineDownloadCategory(engine: VoiceRecognitionEngine): String = when (engine) {
        VoiceRecognitionEngine.WHISPER -> "model_whisper"
        VoiceRecognitionEngine.SHERPA_KWS -> "model_kws"
    }

    private fun ttsVoiceDownloadCategory(voice: TtsVoice): String = when (voice) {
        TtsVoice.KOKORO_EN -> "model_tts_kokoro"
        TtsVoice.VITS_VCTK -> "model_tts_vits"
    }

    fun refreshAfterModelDownload() {
        Log.i(TAG, "Refreshing settings after model download...")
        loadVoiceSettings()
        loadTtsSettings()
        refreshContentCategories()
    }
}

/**
 * Theme settings state
 */
data class ThemeSettingsState(
    val brand: ThemeBrand = ThemeBrand.COASTAL,
    val useDynamicColor: Boolean = false,
    val darkThemeConfig: DarkThemeConfig = DarkThemeConfig.FOLLOW_SYSTEM,
    /** ARGB ints of the three custom accents (primary/secondary/tertiary) when [brand] == ThemeBrand.CUSTOM. 0 = none yet. */
    val customColor: Int = 0,
    val customSecondaryColor: Int = 0,
    val customTertiaryColor: Int = 0,
)
