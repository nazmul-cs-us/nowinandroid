/*
 * Copyright 2022 The Android Open Source Project
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

package com.starception.submission

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.starception.submission.MainActivityUiState.Loading
import com.starception.submission.MainActivityUiState.Success
import com.starception.submission.core.data.repository.UserDataRepository
import com.starception.submission.core.model.data.DarkThemeConfig
import com.starception.submission.core.model.data.ThemeBrand
import com.starception.submission.core.model.data.UserData
import com.starception.submission.core.data.util.SyncManager
import com.starception.submission.core.hadithdatabase.BukhariLocalTranslationRepository
import com.starception.submission.core.hadithdatabase.HadithDatabase
import com.starception.submission.download.AssetDownloadManager
import com.starception.submission.feature.prayertimes.wobble.PrayerAlertState
import com.starception.submission.feature.prayertimes.wobble.ForbiddenPrayerTimeState
import com.starception.submission.media.GlobalMediaViewModel
import com.starception.submission.settings.components.TtsVoice
import com.starception.submission.voice.SherpaOnnxTtsService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    private val userDataRepository: UserDataRepository,
    private val sherpaOnnxTts: SherpaOnnxTtsService,
    private val downloadManager: AssetDownloadManager,
    private val syncManager: SyncManager,
    private val islamicEventStateProvider: com.starception.submission.islamic.shared.IslamicEventStateProvider,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val islamicEventState = islamicEventStateProvider.state

    companion object {
        private const val THEME_CACHE_PREFS = "theme_cache_prefs"
        private const val KEY_THEME_BRAND = "cached_theme_brand"
        private const val KEY_DARK_THEME_CONFIG = "cached_dark_theme_config"
        private const val KEY_USE_DYNAMIC_COLOR = "cached_use_dynamic_color"
        private const val CONTENT_PROMPT_PREFS = "content_prompt_prefs"
        private const val KEY_MISSING_BUKHARI_PROMPT = "missing_bukhari_prompt"
        private const val KEY_PENDING_SETTINGS_SECTION = "pending_settings_section"
        const val SETTINGS_SECTION_CONTENT = "content"
    }

    // Load cached theme SYNCHRONOUSLY to prevent flash
    private val cachedTheme: UserData = loadCachedTheme()

    private val _uiState = MutableStateFlow<MainActivityUiState>(Success(cachedTheme))
    val uiState: StateFlow<MainActivityUiState> = _uiState.asStateFlow()

    // Global content download progress (from singleton AssetDownloadManager)
    val isContentDownloading: StateFlow<Boolean> = downloadManager.isGloballyDownloading
    val contentDownloadProgress: StateFlow<Float> = downloadManager.globalDownloadProgress
    val contentDownloadLabel: StateFlow<String> = downloadManager.globalDownloadLabel

    // TTS is generating audio for playback (hadith reading etc.) — shows a
    // "Preparing audio" banner in the pull-to-sync container.
    val isTtsPreparing: StateFlow<Boolean> = sherpaOnnxTts.isPreparingAudio

    // Global media controller — persistent across all screens
    val globalMedia = GlobalMediaViewModel(context, viewModelScope)

    init {
        // Hadith TTS keeps reading after HadithDetailScreen is disposed; the
        // mini-bar's pause must still be able to stop it (fallback path when the
        // screen's own callbacks are unregistered).
        GlobalMediaViewModel.onHadithFallbackStop = { sherpaOnnxTts.stopSpeaking() }

        // Orphan guard: if a previous activity session died mid-read, the
        // singleton TTS keeps speaking with no media session or controls in
        // this new session — only a bare "Preparing audio" banner. Stop it.
        // Delayed so foreground sources (driving mode, recitation service) can
        // re-publish their state first and are never touched.
        viewModelScope.launch {
            kotlinx.coroutines.delay(1500)
            val source = globalMedia.controllerState.value.playback.source
            if (sherpaOnnxTts.isSpeaking() &&
                source is com.starception.submission.media.MediaSource.None &&
                !com.starception.submission.services.DrivingAudioService.isRunning
            ) {
                android.util.Log.i("MainActivityVM", "Stopping orphaned TTS from previous session")
                sherpaOnnxTts.stopSpeaking()
            }
        }
    }

    // Triggers a background sync via WorkManager — used by app-level pull-to-sync
    fun requestSync() = syncManager.requestSync()

    // Prayer alert state shared from PrayerTimesScreen so all pages can show the banner.
    private val _prayerAlertState = MutableStateFlow(PrayerAlertState())
    val prayerAlertState: StateFlow<PrayerAlertState> = _prayerAlertState.asStateFlow()

    private val _forbiddenPrayerTimeState = MutableStateFlow(ForbiddenPrayerTimeState())
    val forbiddenPrayerTimeState: StateFlow<ForbiddenPrayerTimeState> =
        _forbiddenPrayerTimeState.asStateFlow()

    // Timestamp (elapsedRealtime) of the last push from the Home screen. Home holds the
    // freshest, live-recalculated prayer times, so while it's active (pushing every minute)
    // we let it win. When Home leaves composition the pushes stop, and the app-wide ticker
    // When a debug/test alert is injected (DebugPrayerAlertReceiver) the ticker must not
    // overwrite it. Any real value clears this flag.
    private var debugAlertActive: Boolean = false

    /**
     * SINGLE SOURCE OF TRUTH for the prayer-alert banner. Every screen — Home included —
     * renders this one value, so Home and all other pages can never drift out of sync.
     * Home no longer computes/pushes its own banner; the app-wide ticker below is the sole
     * producer (except debug injection). This retains the old public entry point so a debug
     * injection path can still force a value.
     */
    fun updatePrayerAlert(state: PrayerAlertState) {
        _prayerAlertState.value = state
    }

    init {
        startPrayerAlertTicker()
    }

    /**
     * App-wide minute ticker — the ONLY producer of the live prayer-alert countdown. Runs
     * regardless of which screen is showing, so the banner stays identical and live on Home,
     * the Surah/Dua detail pages, and every tab. Recomputes from the shared cached prayer times
     * (which Home's calculator writes to, so it's equally fresh) via the shared calculator.
     */
    private fun startPrayerAlertTicker() {
        viewModelScope.launch {
            // Fire immediately so the banner is correct on first paint, then every 15s so the
            // "Xm left" text is never more than a few seconds stale on any screen.
            while (true) {
                try {
                    computeAndPublishPrayerStatus(publishPrayerAlert = !debugAlertActive)
                } catch (e: Exception) {
                    Log.w("MainActivityViewModel", "Prayer alert ticker failed", e)
                }
                delay(15_000L)
            }
        }
    }

    private fun computeAndPublishPrayerStatus(publishPrayerAlert: Boolean) {
        val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
            context.applicationContext,
            com.starception.submission.feature.prayertimes.data.PrayerTimeCalculatorEntryPoint::class.java,
        )
        // Prefer the in-memory location cache (freshest), then the persisted settings cache.
        val cachedPrayerTimes = entryPoint.locationCache().getCachedPrayerTimes()?.first
            ?: entryPoint.prayerSettingsRepository().getCachedPrayerTimes()
            ?: return
        val repo = entryPoint.prayerSettingsRepository()
        val settings = repo.calculationSettingsFlow.value
        val notifPrefs = repo.notificationPreferencesFlow.value
        val currentTime = java.time.LocalTime.now()
        val hasPrayedAsr = com.starception.submission.util.PrayerTracker
            .getPrayedPrayersToday()
            .any { it.equals("Asr", ignoreCase = true) }
        if (publishPrayerAlert) {
            _prayerAlertState.value =
                com.starception.submission.feature.prayertimes.wobble.calculatePrayerAlertState(
                    currentTime = currentTime,
                    prayerTimes = cachedPrayerTimes,
                    notificationPrefs = notifPrefs,
                    timeOffsets = settings.timeOffsets,
                )
        }
        _forbiddenPrayerTimeState.value =
            com.starception.submission.feature.prayertimes.wobble.calculateForbiddenPrayerTimeState(
                currentTime = currentTime,
                prayerTimes = cachedPrayerTimes,
                timeOffsets = settings.timeOffsets,
                hasPrayedAsr = hasPrayedAsr,
            )
    }

    // Pull-to-sync state shared across pages. Hoisting it here means a sync that
    // started on Home stays visible after navigating to another tab (and vice
    // versa) — the per-screen PullToSyncContainers all read this one source.
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    fun setSyncing(syncing: Boolean) {
        _isSyncing.value = syncing
    }


    private val _showMissingBukhariPrompt = MutableStateFlow(false)
    val showMissingBukhariPrompt: StateFlow<Boolean> = _showMissingBukhariPrompt.asStateFlow()

    /**
     * Load theme from SharedPreferences cache (SYNCHRONOUS - no flash!)
     */
    private fun loadCachedTheme(): UserData {
        val prefs = context.getSharedPreferences(THEME_CACHE_PREFS, Context.MODE_PRIVATE)

        val themeBrandOrdinal = prefs.getInt(KEY_THEME_BRAND, ThemeBrand.COASTAL.ordinal)
        val darkThemeConfigOrdinal = prefs.getInt(KEY_DARK_THEME_CONFIG, DarkThemeConfig.FOLLOW_SYSTEM.ordinal)
        val useDynamicColor = prefs.getBoolean(KEY_USE_DYNAMIC_COLOR, true)

        val themeBrand = ThemeBrand.entries.getOrElse(themeBrandOrdinal) { ThemeBrand.COASTAL }
        val darkThemeConfig = DarkThemeConfig.entries.getOrElse(darkThemeConfigOrdinal) { DarkThemeConfig.FOLLOW_SYSTEM }

        Log.d("MainActivityViewModel", "📦 Loaded cached theme: brand=$themeBrand, darkConfig=$darkThemeConfig, dynamic=$useDynamicColor")

        return UserData(
            bookmarkedNewsResources = emptySet(),
            viewedNewsResources = emptySet(),
            followedTopics = emptySet(),
            themeBrand = themeBrand,
            darkThemeConfig = darkThemeConfig,
            useDynamicColor = useDynamicColor,
            shouldHideOnboarding = false,
        )
    }

    /**
     * Save theme to SharedPreferences cache for instant load on next startup
     */
    private fun cacheTheme(userData: UserData) {
        val prefs = context.getSharedPreferences(THEME_CACHE_PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_THEME_BRAND, userData.themeBrand.ordinal)
            .putInt(KEY_DARK_THEME_CONFIG, userData.darkThemeConfig.ordinal)
            .putBoolean(KEY_USE_DYNAMIC_COLOR, userData.useDynamicColor)
            .apply()
        Log.d("MainActivityViewModel", "💾 Cached theme: brand=${userData.themeBrand}, darkConfig=${userData.darkThemeConfig}")
    }

    init {
        // Load user preferences in background and cache for next startup
        viewModelScope.launch {
            try {
                Log.d("MainActivityViewModel", "Loading user data for theme preferences")
                userDataRepository.userData.collect { userData ->
                    _uiState.value = Success(userData)
                    // Cache theme for instant load on next app startup
                    cacheTheme(userData)
                    Log.d("MainActivityViewModel", "Theme updated: ${userData.darkThemeConfig}")
                }
            } catch (e: Exception) {
                Log.e("MainActivityViewModel", "Error loading user data, keeping defaults", e)
                // Keep default data on error
            }
        }

        refreshMissingBukhariPrompt()

        // Pre-generate TTS for next 3 hadiths if enrolled in daily_bukhari
        // This ensures cache is ready for driving mode or manual playback
        viewModelScope.launch(Dispatchers.IO) {
            preGenerateHadithTtsOnStartup()
        }

        // Forward debug-injected prayer alerts (DebugPrayerAlertReceiver) through
        // the same flow real ones use, so the stacked layout in PullToSyncContainer
        // can be exercised without waiting for an actual prayer window.
        viewModelScope.launch {
            com.starception.submission.util.DebugPrayerAlertBus.state.collect { injected ->
                if (injected != null) {
                    // Latch so the app-wide ticker doesn't overwrite the simulated alert.
                    // A cleared/inactive injection releases the latch back to real data.
                    debugAlertActive = injected.isActive
                    _prayerAlertState.value = injected
                } else {
                    debugAlertActive = false
                }
            }
        }
    }

    fun refreshMissingBukhariPrompt() {
        val prefs = context.getSharedPreferences(CONTENT_PROMPT_PREFS, Context.MODE_PRIVATE)
        _showMissingBukhariPrompt.value = prefs.getBoolean(KEY_MISSING_BUKHARI_PROMPT, false)
    }

    fun dismissMissingBukhariPrompt() {
        val prefs = context.getSharedPreferences(CONTENT_PROMPT_PREFS, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_MISSING_BUKHARI_PROMPT, false).apply()
        _showMissingBukhariPrompt.value = false
    }

    fun prepareContentSectionNavigation() {
        val prefs = context.getSharedPreferences(CONTENT_PROMPT_PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_MISSING_BUKHARI_PROMPT, false)
            .putString(KEY_PENDING_SETTINGS_SECTION, SETTINGS_SECTION_CONTENT)
            .apply()
        _showMissingBukhariPrompt.value = false
    }

    /**
     * Pre-generate TTS audio for next 3 hadiths on app startup.
     * Only runs if user is enrolled in daily_bukhari and has Sherpa-ONNX TTS configured.
     */
    private suspend fun preGenerateHadithTtsOnStartup() {
        try {
            // Check if enrolled in daily_bukhari
            val coursePrefs = context.getSharedPreferences("course_progress", Context.MODE_PRIVATE)
            val enrolledCourses = coursePrefs.getStringSet("enrolled_courses", emptySet()) ?: emptySet()

            if ("daily_bukhari" !in enrolledCourses) {
                Log.d("MainActivityViewModel", "📚 Not enrolled in daily_bukhari, skipping TTS pre-generation")
                return
            }

            if (!HadithDatabase.isDatabaseAvailable(context, "sahih_bukhari.db")) {
                Log.w("MainActivityViewModel", "📚 Bukhari database missing, skipping TTS pre-generation")
                return
            }

            // Check TTS settings
            val ttsPrefs = context.getSharedPreferences("tts_settings", Context.MODE_PRIVATE)
            val selectedVoiceName = ttsPrefs.getString("selected_voice", null)
            val selectedSpeakerId = ttsPrefs.getInt("selected_speaker_id", 0)

            if (selectedVoiceName == null) {
                Log.d("MainActivityViewModel", "📚 Using Android TTS, no pre-generation needed")
                return
            }

            // Get current progress
            val currentProgress = coursePrefs.getInt("progress_daily_bukhari", 0)
            val startHadithNumber = currentProgress + 1

            Log.i("MainActivityViewModel", "📚 Pre-generating TTS for hadith #$startHadithNumber and next 2 on app startup...")

            // Set voice
            val voice = try {
                TtsVoice.valueOf(selectedVoiceName)
            } catch (e: Exception) {
                TtsVoice.KOKORO_EN
            }
            sherpaOnnxTts.setVoice(voice)

            // Load Bukhari translations (same source as DrivingAudioService)
            val bukhariTranslationRepo = BukhariLocalTranslationRepository.getInstance(context)
            bukhariTranslationRepo.loadTranslations()

            // Pre-generate next 3 hadiths
            for (offset in 0 until 3) {
                val hadithNumber = startHadithNumber + offset
                if (hadithNumber > 7563) break

                val hadithText = bukhariTranslationRepo.getEnglishText(hadithNumber) ?: continue

                val introText = com.starception.submission.voice.EnglishTtsTextNormalizer
                    .bukhariIntro(hadithNumber)
                val fullText = "$introText $hadithText"

                if (!sherpaOnnxTts.isCached(fullText)) {
                    Log.i("MainActivityViewModel", "🔄 Pre-generating hadith #$hadithNumber (${fullText.length} chars)")
                    sherpaOnnxTts.preGenerateAsync(
                        text = fullText,
                        speakerId = selectedSpeakerId
                    )
                    delay(500) // Small delay between generations
                } else {
                    Log.d("MainActivityViewModel", "📦 Hadith #$hadithNumber already cached")
                }
            }

            Log.i("MainActivityViewModel", "✅ Startup TTS pre-generation complete")
        } catch (e: Exception) {
            Log.e("MainActivityViewModel", "Error pre-generating hadith TTS on startup", e)
        }
    }

    /**
     * Check if a news resource is bookmarked
     */
    fun isNewsResourceBookmarked(newsResourceId: String): Boolean {
        val currentState = _uiState.value
        return if (currentState is Success) {
            newsResourceId in currentState.userData.bookmarkedNewsResources
        } else {
            false
        }
    }

    /**
     * Toggle bookmark state for a news resource
     */
    fun toggleNewsResourceBookmark(newsResourceId: String) {
        viewModelScope.launch {
            val isCurrentlyBookmarked = isNewsResourceBookmarked(newsResourceId)
            userDataRepository.setNewsResourceBookmarked(newsResourceId, !isCurrentlyBookmarked)
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Hadith TTS reading is bound to this activity session (no foreground
        // service): if the activity is torn down mid-generation/mid-read, the
        // singleton TTS would keep going orphaned — next launch shows a bare
        // "Preparing audio" with no title and no controls. Stop it. Driving-mode
        // TTS runs its own foreground service and is not affected here.
        if (globalMedia.controllerState.value.playback.source is com.starception.submission.media.MediaSource.Hadith &&
            !com.starception.submission.services.DrivingAudioService.isRunning
        ) {
            sherpaOnnxTts.stopSpeaking()
        }
        globalMedia.cleanup()
    }
}

sealed interface MainActivityUiState {
    data object Loading : MainActivityUiState

    data class Success(val userData: UserData) : MainActivityUiState {
        override val shouldDisableDynamicTheming = !userData.useDynamicColor

        override val themeBrand: ThemeBrand = userData.themeBrand

        override val customThemeColor: Int = userData.customThemeColor
        override val customSecondaryColor: Int = userData.customSecondaryColor
        override val customTertiaryColor: Int = userData.customTertiaryColor

        override fun shouldUseDarkTheme(isSystemDarkTheme: Boolean) =
            when (userData.darkThemeConfig) {
                DarkThemeConfig.FOLLOW_SYSTEM -> isSystemDarkTheme
                DarkThemeConfig.LIGHT -> false
                DarkThemeConfig.DARK -> true
            }
    }

    /**
     * Returns `true` if the state wasn't loaded yet and it should keep showing the splash screen.
     */
    fun shouldKeepSplashScreen() = this is Loading

    /**
     * Returns `true` if the dynamic color is disabled.
     */
    val shouldDisableDynamicTheming: Boolean get() = true

    /**
     * Returns the theme brand to be used.
     */
    val themeBrand: ThemeBrand get() = ThemeBrand.DEFAULT

    /** Seed colour (ARGB) when [themeBrand] is CUSTOM. 0 = not set. */
    val customThemeColor: Int get() = 0
    val customSecondaryColor: Int get() = 0
    val customTertiaryColor: Int get() = 0

    /**
     * Returns `true` if dark theme should be used.
     */
    fun shouldUseDarkTheme(isSystemDarkTheme: Boolean) = isSystemDarkTheme
}
