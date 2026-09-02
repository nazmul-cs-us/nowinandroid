/*
 * Copyright 2021 The Android Open Source Project
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

package com.starception.submission.shared.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import com.starception.submission.core.designsystem.theme.DarkAndroidColorScheme
import com.starception.submission.core.designsystem.theme.DarkCoastalColorScheme
import com.starception.submission.core.designsystem.theme.DarkDefaultColorScheme
import com.starception.submission.core.designsystem.theme.DarkRoyalColorScheme
import com.starception.submission.core.designsystem.theme.LightAndroidColorScheme
import com.starception.submission.core.designsystem.theme.LightCoastalColorScheme
import com.starception.submission.core.designsystem.theme.LightDefaultColorScheme
import com.starception.submission.core.designsystem.theme.LightRoyalColorScheme
import com.starception.submission.core.designsystem.theme.sharedTypography
import com.starception.submission.core.model.data.DarkThemeConfig
import com.starception.submission.core.model.data.ThemeBrand
import com.starception.submission.prayer.model.prayerDefaultsFor
import com.starception.submission.shared.PrayerSchedule
import com.starception.submission.shared.assets.iosCloudAssets
import com.starception.submission.shared.location.DeviceLocation
import com.starception.submission.shared.location.LocationProvider
import com.starception.submission.shared.notifications.IosPrayerSchedulePublisher
import com.starception.submission.shared.salah.SalahProgress
import com.starception.submission.shared.salah.SalahTracker
import com.starception.submission.shared.settings.LastLocationStore
import com.starception.submission.shared.settings.UserAudioSettings
import com.starception.submission.shared.settings.UserAppearanceSettings
import com.starception.submission.shared.settings.UserPrayerSettings
import com.starception.submission.shared.settings.VoiceRecognitionMode
import com.starception.submission.shared.travel.IosTravelDuaMonitor
import com.starception.submission.shared.voice.IosSherpaAssetResolver
import com.starception.submission.shared.voice.IosSherpaEventSink
import com.starception.submission.shared.voice.IosSherpaService
import com.starception.submission.shared.voice.NarrationVoice
import com.starception.submission.shared.voice.PlatformSpeechRecognizer
import com.starception.submission.shared.voice.PlatformSpeechSynthesizer
import com.starception.submission.shared.voice.SpeechRecognitionEvent
import com.starception.submission.shared.weather.CurrentConditionsClient
import kotlin.time.Clock
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import platform.Foundation.NSBundle
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIViewController

/**
 * Bridges the shared Compose UI into UIKit so `iosApp/` can present it.
 *
 * This is the whole iOS UI boundary: Swift owns the app lifecycle and hands the
 * screen to Compose. Everything below this line is shared with Android.
 */
@Suppress("FunctionName")
fun PrayerTimesViewController(
    sherpaService: IosSherpaService? = null,
): UIViewController = ComposeUIViewController {
    val tracker = remember { SalahTracker() }
    val settingsStore = remember { UserPrayerSettings() }
    val appearanceStore = remember { UserAppearanceSettings() }
    val audioStore = remember { UserAudioSettings() }
    val locationStore = remember { LastLocationStore() }
    val speechRecognizer = remember { PlatformSpeechRecognizer() }
    val speechSynthesizer = remember { PlatformSpeechSynthesizer() }
    val coroutineScope = rememberCoroutineScope()
    val startInSettings = remember {
        NSProcessInfo.processInfo.arguments.any { it == "--start-settings" }
    }

    var location by remember { mutableStateOf(locationStore.location()) }
    var resolved by remember { mutableStateOf(false) }
    var weatherCode by remember { mutableStateOf<Int?>(null) }
    var temperature by remember { mutableStateOf<Double?>(null) }
    var refreshRequest by remember { mutableStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    var syncResultText by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableStateOf(Clock.System.now()) }
    var themeSettings by remember { mutableStateOf(appearanceStore.settings()) }
    var travelDuaSettings by remember { mutableStateOf(audioStore.travelDua()) }
    var isTravelDuaPlaying by remember { mutableStateOf(false) }
    var recognitionMode by remember { mutableStateOf(audioStore.recognitionMode()) }
    var recognitionTestState by remember { mutableStateOf(VoiceTestState.IDLE) }
    var recognitionTestText by remember { mutableStateOf<String?>(null) }
    var recognitionSession by remember { mutableStateOf(0) }
    val narrationVoices = remember(sherpaService) {
        if (sherpaService == null) {
            speechSynthesizer.voices()
        } else {
            listOf(
                NarrationVoice(
                    IosSherpaAssetResolver.KOKORO_VOICE_ID,
                    "Kokoro",
                    "High-quality English",
                    totalSpeakers = 10,
                ),
                NarrationVoice(
                    IosSherpaAssetResolver.VITS_VOICE_ID,
                    "VCTK British",
                    "British English",
                    totalSpeakers = 109,
                ),
            )
        }
    }
    var selectedNarrationVoiceIdentifier by remember(narrationVoices) {
        mutableStateOf(
            audioStore.narrationVoiceIdentifier()
                ?.takeIf { saved -> narrationVoices.any { it.identifier == saved } }
                ?: narrationVoices.firstOrNull()?.identifier,
        )
    }
    var isNarrationSpeaking by remember { mutableStateOf(false) }
    var selectedNarrationSpeakerId by remember { mutableStateOf(audioStore.narrationSpeakerId()) }
    var narrationStatus by remember { mutableStateOf<String?>(null) }
    var narrationError by remember { mutableStateOf<String?>(null) }
    var narrationSession by remember { mutableStateOf(0) }
    var narrationJob by remember { mutableStateOf<Job?>(null) }
    var recognitionJob by remember { mutableStateOf<Job?>(null) }
    var automaticTravelDuaRequests by remember { mutableStateOf(0) }
    var contentStorageState by remember { mutableStateOf(ContentStorageState()) }
    var contentDownloadJob by remember { mutableStateOf<Job?>(null) }
    var contentRefreshJob by remember { mutableStateOf<Job?>(null) }
    val travelDuaMonitor = remember {
        IosTravelDuaMonitor { automaticTravelDuaRequests += 1 }
    }

    fun stopAllSpeech() {
        narrationSession += 1
        narrationJob?.cancel()
        narrationJob = null
        sherpaService?.stopSpeaking()
        speechSynthesizer.stop()
        isTravelDuaPlaying = false
        isNarrationSpeaking = false
        narrationStatus = null
    }

    fun playTravelDua() {
        recognitionSession += 1
        recognitionJob?.cancel()
        recognitionJob = null
        sherpaService?.stopRecognition()
        speechRecognizer.stop()
        stopAllSpeech()
        narrationError = null
        isTravelDuaPlaying = true
        val started = speechSynthesizer.speak(
            text = TRAVEL_DUA_ARABIC,
            language = "ar-SA",
        ) { error ->
            isTravelDuaPlaying = false
            if (error != null) narrationError = error
        }
        if (!started) {
            isTravelDuaPlaying = false
            narrationError = "An Arabic system voice is not installed"
        }
    }

    fun refreshContentStorage(message: String? = null, retryCategory: String? = null) {
        contentRefreshJob?.cancel()
        contentStorageState = contentStorageState.copy(
            isLoading = true,
            error = message,
            retryCategoryKey = retryCategory,
        )
        contentRefreshJob = coroutineScope.launch {
            val refreshed = loadIosContentStorageState()
            contentStorageState = refreshed.copy(
                error = message ?: refreshed.error,
                retryCategoryKey = retryCategory,
            )
            contentRefreshJob = null
        }
    }

    fun downloadContentCategory(category: String) {
        if (contentDownloadJob?.isActive == true) return
        contentStorageState = contentStorageState.copy(
            error = null,
            categories = contentStorageState.categories.map {
                if (it.categoryKey == category) {
                    it.copy(isDownloading = true, progress = 0f)
                } else {
                    it
                }
            },
        )
        contentDownloadJob = coroutineScope.launch {
            var message: String? = null
            try {
                val manifest = iosCloudAssets.loadManifest()
                    ?: error("The Cloudflare asset manifest is unavailable")
                val result = iosCloudAssets.downloadCategory(category, manifest) { progress ->
                    contentStorageState = contentStorageState.copy(
                        categories = contentStorageState.categories.map {
                            if (it.categoryKey == category) {
                                it.copy(isDownloading = true, progress = progress.fraction)
                            } else {
                                it
                            }
                        },
                    )
                }
                if (!result.isComplete) {
                    message = "Could not download ${result.missingAssets.size} files. Try again."
                }
            } catch (_: CancellationException) {
                // User-requested cancellation is reflected by the refreshed cache state.
            } catch (error: Exception) {
                message = error.message ?: "The content download failed"
            } finally {
                contentStorageState = contentStorageState.copy(
                    categories = contentStorageState.categories.map {
                        if (it.categoryKey == category) it.copy(isDownloading = false) else it
                    },
                )
                contentDownloadJob = null
                refreshContentStorage(message, retryCategory = category.takeIf { message != null })
            }
        }
    }

    fun deleteContentCategory(category: String) {
        if (contentDownloadJob?.isActive == true || contentRefreshJob?.isActive == true) return
        if (category.startsWith("model_")) {
            recognitionSession += 1
            recognitionJob?.cancel()
            recognitionJob = null
            sherpaService?.stopRecognition()
            speechRecognizer.stop()
            recognitionTestState = VoiceTestState.IDLE
            recognitionTestText = "Say yes or no to verify recognition."
            stopAllSpeech()
        }
        contentStorageState = contentStorageState.copy(isLoading = true, error = null)
        contentRefreshJob = coroutineScope.launch {
            val message = try {
                val manifest = iosCloudAssets.loadManifest()
                    ?: error("The Cloudflare asset manifest is unavailable")
                iosCloudAssets.deleteCategory(category, manifest)
                null
            } catch (error: Exception) {
                error.message ?: "The downloaded content could not be deleted"
            }
            val refreshed = loadIosContentStorageState()
            contentStorageState = refreshed.copy(error = message ?: refreshed.error)
            contentRefreshJob = null
        }
    }

    LaunchedEffect(travelDuaSettings) {
        travelDuaMonitor.update(travelDuaSettings)
    }

    LaunchedEffect(automaticTravelDuaRequests) {
        if (automaticTravelDuaRequests > 0) playTravelDua()
    }

    DisposableEffect(Unit) {
        onDispose {
            travelDuaMonitor.stop()
            speechRecognizer.stop()
            speechSynthesizer.stop()
            sherpaService?.shutdown()
            recognitionJob?.cancel()
            narrationJob?.cancel()
            contentDownloadJob?.cancel()
            contentRefreshJob?.cancel()
        }
    }

    LaunchedEffect(refreshRequest) {
        isRefreshing = refreshRequest > 0
        val savedLocation = location
        val refreshedLocation = LocationProvider().current()
        if (refreshedLocation != null) {
            location = refreshedLocation
            locationStore.save(refreshedLocation)
        }
        resolved = true

        val target = refreshedLocation ?: savedLocation ?: FALLBACK_LOCATION
        val conditions = CurrentConditionsClient.fetch(target.latitude, target.longitude)
        weatherCode = conditions?.weatherCode
        temperature = conditions?.temperatureCelsius
        if (refreshRequest > 0) {
            isRefreshing = false
            syncResultText = if (refreshedLocation != null) {
                "Location and prayer times updated"
            } else if (savedLocation != null) {
                "Location unavailable; using last saved location"
            } else {
                "Location unavailable; using Dubai fallback"
            }
            delay(3_000)
            syncResultText = null
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = Clock.System.now()
        }
    }

    val place = location ?: FALLBACK_LOCATION
    val country = prayerDefaultsFor(place.countryCode)
    var prayerSettings by remember(place.countryCode, country) {
        mutableStateOf(settingsStore.settings(place.countryCode, country))
    }
    var notificationPrefs by remember { mutableStateOf(settingsStore.notifications()) }
    // Prayer times belong to the resolved location, not necessarily the device's
    // current timezone (for example, when viewing a cached location while travelling).
    val localNow = now.toLocalDateTime(timeZoneForOffset(place.timeZoneOffset))
    val today = localNow.date

    var completed by remember(today) { mutableStateOf(tracker.completed(today)) }

    // Keyed, not recomputed on every recomposition. The calculator is not cheap
    // and logs several thousand lines per run, all synchronously on the main
    // thread — recomputing it for a "+1 minute" tap stalls the UI visibly.
    val day = remember(
        today,
        place.latitude,
        place.longitude,
        place.timeZoneOffset,
        place.countryCode,
        country,
        prayerSettings,
        weatherCode,
        temperature,
        localNow.hour,
        localNow.minute,
    ) {
        PrayerSchedule.forDate(
            year = today.year,
            month = today.monthNumber,
            day = today.dayOfMonth,
            latitude = place.latitude,
            longitude = place.longitude,
            timeZoneOffset = place.timeZoneOffset,
            // The country's own method keeps its authority's published offsets.
            defaults = country,
            settings = prayerSettings,
            // Null until the forecast arrives, which prayerSkyWeather treats as Clear.
            weatherCode = weatherCode,
            temperatureCelsius = temperature,
            countryCode = place.countryCode,
            isFriday = today.dayOfWeek == DayOfWeek.FRIDAY,
            nowHour = localNow.hour,
            nowMinute = localNow.minute,
        )
    }

    LaunchedEffect(
        today,
        place,
        country,
        prayerSettings,
        notificationPrefs,
        localNow.hour,
        localNow.minute,
    ) {
        IosPrayerSchedulePublisher.publish(
            locationName = place.placeName,
            startDate = today,
            latitude = place.latitude,
            longitude = place.longitude,
            timeZoneOffset = place.timeZoneOffset,
            countryCode = place.countryCode,
            defaults = country,
            settings = prayerSettings,
            preferences = notificationPrefs,
        )
    }

    val useDarkTheme = when (themeSettings.darkThemeConfig) {
        DarkThemeConfig.FOLLOW_SYSTEM -> isSystemInDarkTheme()
        DarkThemeConfig.LIGHT -> false
        DarkThemeConfig.DARK -> true
    }
    val appVersion = remember {
        NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
            ?: "Unknown"
    }

    MaterialTheme(
        colorScheme = iosColorScheme(themeSettings.brand, useDarkTheme),
        typography = sharedTypography(),
    ) {
        SharedNavHost(
            startInSettings = startInSettings,
            latitude = place.latitude,
            longitude = place.longitude,
            today = today,
            home = { actions ->
                PrayerTimesScreen(
                    placeName = place.placeName.ifEmpty { "Locating…" },
                    day = day,
                    salah = SalahProgress.from(completed),
                    onTogglePrayer = { completed = tracker.toggle(today, it) },
                    offsets = prayerSettings.timeOffsets,
                    onAdjustPrayer = { prayer, delta ->
                        prayerSettings = settingsStore.adjust(
                            place.countryCode,
                            country,
                            prayer,
                            delta,
                        )
                    },
                    onOpenSettings = actions.onOpenSettings,
                    today = today,
                    isLocating = !resolved,
                    isRefreshing = isRefreshing,
                    syncResultText = syncResultText,
                    onRefresh = { refreshRequest += 1 },
                    latitude = place.latitude,
                    longitude = place.longitude,
                    notifications = notificationPrefs,
                    onTogglePrayerNotification = { prayer ->
                        notificationPrefs = notificationPrefs.togglePrayer(prayer)
                        settingsStore.saveNotifications(notificationPrefs)
                    },
                    onOpenProfile = actions.onOpenProfile,
                    onOpenSearch = actions.onOpenSearch,
                    onVoiceTap = actions.onOpenSearch,
                    onOpenQuran = actions.onOpenQuran,
                    onOpenQibla = actions.onOpenQibla,
                    onOpenRecommendation = actions.onOpenRecommendation,
                    onSelectBottom = actions.onSelectBottom,
                )
            },
            settings = { onBack ->
                PrayerSettingsScreen(
                    settings = prayerSettings,
                    countryName = country?.countryName,
                    showRestoreOption = settingsStore.isChanged(place.countryCode, country),
                    onSettingsChange = { updated ->
                        prayerSettings = updated
                        settingsStore.save(place.countryCode, updated)
                    },
                    onRestore = {
                        settingsStore.restoreDefaults(place.countryCode)
                        prayerSettings = settingsStore.settings(place.countryCode, country)
                    },
                    onBack = onBack,
                    notifications = notificationPrefs,
                    onNotificationsChange = { updated ->
                        notificationPrefs = updated
                        settingsStore.saveNotifications(updated)
                    },
                    themeSettings = themeSettings,
                    onThemeBrandChange = { brand ->
                        appearanceStore.saveBrand(brand)
                        themeSettings = themeSettings.copy(brand = brand)
                    },
                    onDarkThemeConfigChange = { config ->
                        appearanceStore.saveDarkTheme(config)
                        themeSettings = themeSettings.copy(darkThemeConfig = config)
                    },
                    appVersion = appVersion,
                    audioState = AudioSettingsState(
                        travelDua = travelDuaSettings,
                        isTravelDuaPlaying = isTravelDuaPlaying,
                        recognitionMode = recognitionMode,
                        recognitionTestState = recognitionTestState,
                        recognitionTestText = recognitionTestText,
                        narrationVoices = narrationVoices,
                        selectedNarrationVoiceIdentifier = selectedNarrationVoiceIdentifier,
                        selectedNarrationSpeakerId = selectedNarrationSpeakerId,
                        isNarrationSpeaking = isNarrationSpeaking,
                        narrationStatus = narrationStatus,
                        narrationError = narrationError,
                    ),
                    audioActions = AudioSettingsActions(
                        onTravelDuaChange = { updated ->
                            travelDuaSettings = updated
                            audioStore.saveTravelDua(updated)
                        },
                        onTestTravelDua = { playTravelDua() },
                        onStopTravelDua = { stopAllSpeech() },
                        onRecognitionModeSelected = { mode ->
                            recognitionSession += 1
                            recognitionJob?.cancel()
                            recognitionJob = null
                            sherpaService?.stopRecognition()
                            speechRecognizer.stop()
                            recognitionTestState = VoiceTestState.IDLE
                            recognitionTestText = null
                            recognitionMode = mode
                            audioStore.saveRecognitionMode(mode)
                        },
                        onStartRecognitionTest = {
                            stopAllSpeech()
                            recognitionJob?.cancel()
                            val session = ++recognitionSession
                            recognitionTestState = VoiceTestState.LISTENING
                            recognitionTestText = if (sherpaService == null) {
                                "Waiting for speech"
                            } else {
                                "Preparing offline model..."
                            }
                            recognitionJob = coroutineScope.launch {
                                if (sherpaService == null) {
                                    speechRecognizer.start(recognitionMode) { event ->
                                        if (session == recognitionSession) {
                                            when (event) {
                                                SpeechRecognitionEvent.Listening -> {
                                                    recognitionTestState = VoiceTestState.LISTENING
                                                    recognitionTestText = "Say yes or no"
                                                        .takeIf { recognitionMode == VoiceRecognitionMode.KEYWORDS }
                                                }
                                                is SpeechRecognitionEvent.Partial -> {
                                                    recognitionTestState = VoiceTestState.LISTENING
                                                    recognitionTestText = event.text
                                                }
                                                is SpeechRecognitionEvent.Result -> {
                                                    recognitionTestState = VoiceTestState.SUCCESS
                                                    recognitionTestText = event.text
                                                }
                                                is SpeechRecognitionEvent.Error -> {
                                                    recognitionTestState = VoiceTestState.ERROR
                                                    recognitionTestText = event.message
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    val paths = IosSherpaAssetResolver.recognition(recognitionMode) { progress ->
                                        recognitionTestText = "Downloading offline model ${(progress * 100).toInt()}%"
                                    }
                                    if (session != recognitionSession) return@launch
                                    if (paths == null) {
                                        recognitionTestState = VoiceTestState.ERROR
                                        recognitionTestText = "Unable to download the offline recognition model"
                                        return@launch
                                    }
                                    val sink = object : IosSherpaEventSink {
                                        override fun onRecognitionStarted() {
                                            if (session == recognitionSession) {
                                                recognitionTestState = VoiceTestState.LISTENING
                                                recognitionTestText = if (recognitionMode == VoiceRecognitionMode.KEYWORDS) {
                                                    "Say yes or no"
                                                } else {
                                                    "Listening..."
                                                }
                                            }
                                        }

                                        override fun onKeyword(keyword: String) {
                                            if (session == recognitionSession) {
                                                recognitionTestState = VoiceTestState.SUCCESS
                                                recognitionTestText = keyword
                                                sherpaService.stopRecognition()
                                            }
                                        }

                                        override fun onPartialResult(text: String) {
                                            if (session == recognitionSession) recognitionTestText = text
                                        }

                                        override fun onFinalResult(text: String) {
                                            if (session == recognitionSession) {
                                                recognitionTestState = VoiceTestState.SUCCESS
                                                recognitionTestText = text
                                            }
                                        }

                                        override fun onTtsStarted(sampleRate: Int) = Unit
                                        override fun onTtsFinished() = Unit

                                        override fun onError(message: String) {
                                            if (session == recognitionSession) {
                                                recognitionTestState = VoiceTestState.ERROR
                                                recognitionTestText = message
                                            }
                                        }
                                    }
                                    val started = if (recognitionMode == VoiceRecognitionMode.KEYWORDS) {
                                        sherpaService.startKeywordSpotting(paths, sink)
                                    } else {
                                        sherpaService.startOnlineRecognition(paths, sink)
                                    }
                                    if (!started) {
                                        recognitionTestState = VoiceTestState.ERROR
                                        recognitionTestText = "Unable to start offline recognition"
                                        return@launch
                                    }
                                }
                                delay(7_000)
                                if (session == recognitionSession &&
                                    recognitionTestState == VoiceTestState.LISTENING
                                ) {
                                    sherpaService?.stopRecognition()
                                    speechRecognizer.stop()
                                    recognitionTestState = VoiceTestState.ERROR
                                    recognitionTestText = "No speech detected"
                                }
                            }
                        },
                        onStopRecognitionTest = {
                            recognitionSession += 1
                            recognitionJob?.cancel()
                            recognitionJob = null
                            sherpaService?.stopRecognition()
                            speechRecognizer.stop()
                            recognitionTestState = VoiceTestState.IDLE
                            recognitionTestText = null
                        },
                        onNarrationVoiceSelected = { voice ->
                            stopAllSpeech()
                            selectedNarrationVoiceIdentifier = voice.identifier
                            selectedNarrationSpeakerId = 0
                            audioStore.saveNarrationVoiceIdentifier(voice.identifier)
                            audioStore.saveNarrationSpeakerId(0)
                            narrationStatus = null
                            narrationError = null
                        },
                        onNarrationSpeakerSelected = { speakerId ->
                            val maxSpeaker = narrationVoices
                                .firstOrNull { it.identifier == selectedNarrationVoiceIdentifier }
                                ?.totalSpeakers
                                ?.minus(1)
                                ?: 0
                            selectedNarrationSpeakerId = speakerId.coerceIn(0, maxSpeaker)
                            audioStore.saveNarrationSpeakerId(selectedNarrationSpeakerId)
                        },
                        onPreviewNarration = {
                            recognitionSession += 1
                            recognitionJob?.cancel()
                            recognitionJob = null
                            sherpaService?.stopRecognition()
                            speechRecognizer.stop()
                            stopAllSpeech()
                            val session = narrationSession
                            narrationStatus = null
                            narrationError = null
                            isNarrationSpeaking = true
                            if (sherpaService == null) {
                                val started = speechSynthesizer.speak(
                                    text = NARRATION_SAMPLE,
                                    voiceIdentifier = selectedNarrationVoiceIdentifier,
                                ) { error ->
                                    isNarrationSpeaking = false
                                    narrationError = error
                                }
                                if (!started) {
                                    isNarrationSpeaking = false
                                    narrationError = "The selected system voice is unavailable"
                                }
                            } else {
                                narrationJob = coroutineScope.launch {
                                    narrationStatus = "Preparing offline voice..."
                                    val voiceId = selectedNarrationVoiceIdentifier
                                        ?: IosSherpaAssetResolver.KOKORO_VOICE_ID
                                    val paths = IosSherpaAssetResolver.tts(voiceId) { progress ->
                                        if (session == narrationSession) {
                                            narrationStatus =
                                                "Downloading offline voice ${(progress * 100).toInt()}%"
                                        }
                                    }
                                    if (session != narrationSession) return@launch
                                    if (paths == null) {
                                        isNarrationSpeaking = false
                                        narrationStatus = null
                                        narrationError = "Unable to download the offline voice model"
                                        return@launch
                                    }
                                    narrationStatus = null
                                    val sink = object : IosSherpaEventSink {
                                        override fun onRecognitionStarted() = Unit
                                        override fun onKeyword(keyword: String) = Unit
                                        override fun onPartialResult(text: String) = Unit
                                        override fun onFinalResult(text: String) = Unit
                                        override fun onTtsStarted(sampleRate: Int) {
                                            if (session == narrationSession) isNarrationSpeaking = true
                                        }

                                        override fun onTtsFinished() {
                                            if (session == narrationSession) isNarrationSpeaking = false
                                        }

                                        override fun onError(message: String) {
                                            if (session == narrationSession) {
                                                isNarrationSpeaking = false
                                                narrationStatus = null
                                                narrationError = message
                                            }
                                        }
                                    }
                                    if (!sherpaService.speak(
                                            NARRATION_SAMPLE,
                                            paths,
                                            selectedNarrationSpeakerId,
                                            1f,
                                            sink,
                                        )
                                    ) {
                                        isNarrationSpeaking = false
                                        narrationStatus = null
                                        narrationError = "Unable to start the offline voice"
                                    }
                                }
                            }
                        },
                        onStopNarration = { stopAllSpeech() },
                    ),
                    contentStorageState = contentStorageState,
                    contentStorageActions = ContentStorageActions(
                        onRefresh = { refreshContentStorage() },
                        onDownloadCategory = ::downloadContentCategory,
                        onCancelDownload = { contentDownloadJob?.cancel() },
                        onDeleteCategory = ::deleteContentCategory,
                    ),
                )
            },
        )
    }
}

private suspend fun loadIosContentStorageState(): ContentStorageState =
    withContext(Dispatchers.Default) {
        try {
            val manifest = iosCloudAssets.loadManifest()
                ?: return@withContext ContentStorageState(
                    error = "The Cloudflare asset manifest is unavailable",
                )
            val categories = manifest.categories
                .filterKeys { it in IOS_CONTENT_STORAGE_CATEGORIES }
                .map { (category, info) ->
                val status = iosCloudAssets.getCategoryStatus(category, manifest)
                ContentStorageCategoryState(
                    categoryKey = category,
                    displayName = storageCategoryDisplayName(category),
                    description = storageCategoryDescription(category),
                    totalSize = status.totalBytes.takeIf { it > 0L } ?: info.totalSize,
                    downloadedSize = status.downloadedBytes,
                    availableSize = status.availableBytes,
                    fileCount = status.totalFiles.takeIf { it > 0 } ?: info.fileCount,
                    required = info.required,
                    isDownloaded = status.isDownloaded,
                    isAvailable = status.isAvailable,
                    progress = if (status.totalBytes > 0L) {
                        status.downloadedBytes.toFloat() / status.totalBytes
                    } else {
                        1f
                    },
                )
            }.sortedWith(
                compareByDescending<ContentStorageCategoryState> { it.required }
                    .thenBy { it.displayName },
            )
            ContentStorageState(categories = categories)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            ContentStorageState(error = error.message ?: "Downloaded content could not be checked")
        }
    }

private const val NARRATION_SAMPLE =
    "Assalamu alaikum, this is your selected narration voice."
private val IOS_CONTENT_STORAGE_CATEGORIES = setOf(
    "hadith_sahih_bukhari",
    "model_asr",
    "model_kws",
    "model_tts_kokoro",
    "model_tts_vits",
)
private const val TRAVEL_DUA_ARABIC =
    "سبحان الذي سخر لنا هذا وما كنا له مقرنين وإنا إلى ربنا لمنقلبون"

private fun iosColorScheme(brand: ThemeBrand, dark: Boolean): ColorScheme = when (brand) {
    ThemeBrand.DEFAULT -> if (dark) DarkDefaultColorScheme else LightDefaultColorScheme
    ThemeBrand.ANDROID -> if (dark) DarkAndroidColorScheme else LightAndroidColorScheme
    ThemeBrand.COASTAL -> if (dark) DarkCoastalColorScheme else LightCoastalColorScheme
    ThemeBrand.ROYAL -> if (dark) DarkRoyalColorScheme else LightRoyalColorScheme
    ThemeBrand.CUSTOM -> if (dark) DarkCoastalColorScheme else LightCoastalColorScheme
}

/**
 * Used only when neither Core Location nor a previously saved fix is available.
 *
 * Prayer times are wrong for the wrong place, so this is a stopgap, not a
 * default worth keeping. A successful fix is persisted and takes precedence on
 * future launches.
 */
private val FALLBACK_LOCATION = DeviceLocation(
    latitude = 25.1030198,
    longitude = 55.1677409,
    timeZoneOffset = 4.0,
    placeName = "Nad Al Hamar, Dubai",
    countryCode = "AE",
)

private fun timeZoneForOffset(offsetHours: Double): TimeZone {
    val offsetMinutes = (offsetHours * 60).roundToInt().coerceIn(-18 * 60, 18 * 60)
    val absoluteMinutes = abs(offsetMinutes)
    val hours = (absoluteMinutes / 60).toString().padStart(2, '0')
    val minutes = (absoluteMinutes % 60).toString().padStart(2, '0')
    val sign = if (offsetMinutes < 0) "-" else "+"
    return TimeZone.of("UTC$sign$hours:$minutes")
}

private fun com.starception.submission.prayer.model.PrayerNotificationPreferences.togglePrayer(
    prayer: String,
) = when (prayer.lowercase()) {
    "fajr" -> copy(fajrNotificationEnabled = !fajrNotificationEnabled)
    "dhuhr" -> copy(dhuhrNotificationEnabled = !dhuhrNotificationEnabled)
    "asr" -> copy(asrNotificationEnabled = !asrNotificationEnabled)
    "maghrib" -> copy(maghribNotificationEnabled = !maghribNotificationEnabled)
    "isha" -> copy(ishaNotificationEnabled = !ishaNotificationEnabled)
    else -> this
}
