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
import android.content.pm.ApplicationInfo
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy.Builder
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.starception.submission.sync.initializers.Sync
import com.starception.submission.util.ProfileVerifierLogger
import com.starception.submission.util.AnrPreventionConfig
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import android.content.Intent
import android.os.Build
import android.util.Log
import com.starception.submission.services.PrayerNotificationService
import com.starception.submission.ui.search.InMemorySearchService
import com.starception.submission.util.PrayerNotificationManager
import com.starception.submission.prayer.util.FileLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * [Application] class for Submission
 */
@HiltAndroidApp
class SubmissionApplication : Application(), ImageLoaderFactory {
    @Inject
    lateinit var imageLoader: dagger.Lazy<ImageLoader>

    @Inject
    lateinit var profileVerifierLogger: ProfileVerifierLogger

    @Inject
    lateinit var inMemorySearchService: InMemorySearchService

    @Inject
    lateinit var settingsSyncManager: com.starception.submission.usersettings.sync.SettingsSyncManager

    @Inject
    lateinit var contentCoordinator: com.starception.submission.download.ContentCoordinator

    @Inject
    lateinit var audioDownloadHelper: com.starception.submission.download.AudioDownloadHelper

    @Inject
    lateinit var fortressTtsService: com.starception.submission.voice.SherpaOnnxTtsService

    override fun onCreate() {
        Log.d("SubmissionApplication", "Application onCreate started")
        super.onCreate()

        // Initialize FileLogger for prayer/adhan debugging
        FileLogger.init(this)
        FileLogger.i("SubmissionApplication", "Application onCreate started - FileLogger initialized")

        // Initialize Prayer Tracker for tracking completed prayers
        com.starception.submission.util.PrayerTracker.initialize(this)
        Log.d("SubmissionApplication", "Prayer Tracker initialized")

        // Verify ANR prevention configuration
        AnrPreventionConfig.isOptimizedForAnrPrevention()
        
        // Clean up any existing service instances to prevent conflicts
        cleanupExistingServices()
        
        setStrictModePolicy()

        // Use background thread for heavy initialization to prevent ANR
        Thread {
            try {
                // ENABLE Sync initialization to populate topics data for interests pages
                if (AnrPreventionConfig.ENABLE_BACKGROUND_SYNC) {
                    Sync.initialize(context = this)
                    Log.d("SubmissionApplication", "Sync initialized to populate topics data")
                } else {
                    Log.w("SubmissionApplication", "Sync disabled - interests pages will be empty")
                }
                profileVerifierLogger()
                
                // DISABLE prayer notification manager initialization to prevent ANR
                if (AnrPreventionConfig.ENABLE_AUTO_SERVICE_START) {
                    PrayerNotificationManager.initialize(this)
                }
                
                // Start cloud settings-sync (push on change, pull on login) when signed in.
                settingsSyncManager.start()

                // Rebuild derived content (news.db) when a content category finishes downloading.
                contentCoordinator.start()

                // Wire the news-card chapter play button (core/ui) to play-local-else-download-
                // then-cache fortress audio. core/ui can't see the download system, so we supply
                // the resolver here; a null return falls back to streaming the URL.
                com.starception.submission.core.ui.ChapterAudioController.localAudioResolver = { url ->
                    audioDownloadHelper.resolveFortressAudioUrlToLocalPath(url)
                }

                // Bridge ChapterAudioController <-> GlobalMediaViewModel so Fortress chapter
                // playback surfaces the shared media mini-bar with a progress sweep (like Surah/
                // Hadith). core/ui can't reference the app-module media layer, so wire it here.
                com.starception.submission.core.ui.ChapterAudioController.onPlaybackStateChanged =
                    { playing, title ->
                        com.starception.submission.media.GlobalMediaViewModel
                            .onFortressPlaybackChanged?.invoke(playing, title)
                    }
                com.starception.submission.core.ui.ChapterAudioController.onProgressChanged =
                    { pos, dur ->
                        com.starception.submission.media.GlobalMediaViewModel
                            .onFortressProgressChanged?.invoke(pos, dur)
                    }
                com.starception.submission.media.GlobalMediaViewModel.onFortressPlayPauseRequested =
                    { com.starception.submission.core.ui.ChapterAudioController.togglePlayPause() }
                com.starception.submission.media.GlobalMediaViewModel.onFortressSeekRequested =
                    { pos -> com.starception.submission.core.ui.ChapterAudioController.seekTo(pos) }

                // Route Fortress chapter playback through ChapterRecitationService so it shows a
                // system notification + lock-screen media controls (like Surah). The controller
                // resolves the CDN/local source here, then hands the final source to the service.
                val appCtx = applicationContext
                val delegateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

                // Spoken announcements for Fortress playback, via the on-device TTS:
                // the chapter (topic) name is announced whenever playback crosses into
                // a different chapter, and the dua number before every clip. Lives in
                // the play delegate so ONE hook covers both a user tapping play and
                // the app-level auto-advance. Speaks nothing (fast no-op) when the TTS
                // engine isn't downloaded; a timeout guards against the announcement
                // ever stalling playback.
                var lastAnnouncedChapter: String? = null
                val announceFortressTrack: suspend (String) -> Unit = announce@{ title ->
                    if (title.isBlank()) return@announce
                    val hasDuaSuffix = title.contains(": Dua ")
                    val chapter = if (hasDuaSuffix) title.substringBeforeLast(": Dua ").trim() else title.trim()
                    val duaNumber = if (hasDuaSuffix) title.substringAfterLast(": Dua ").trim() else null
                    // Reference BOOK names only (e.g. "Al-Bukhari and Muslim") —
                    // hadith numbers are deliberately not spoken. The database's
                    // collection_name column is empty, so match the prose
                    // reference strings against the canonical collections and
                    // speak the first two mentioned (the primary citation leads
                    // the string; later mentions are usually grading notes).
                    val referenceBooks = if (duaNumber?.toIntOrNull() != null) {
                        val refText = runCatching {
                            com.starception.submission.core.duadatabase.DuaDatabase
                                .getInstance(appCtx).duaDao()
                                .getDuaReferenceStringsByTitleAndPosition(chapter, duaNumber.toInt())
                        }.getOrNull().orEmpty().joinToString(" ")
                        // (match pattern in text) -> spoken form; Tirmithi/Tirmidhi
                        // spellings collapse to one spoken name.
                        val knownBooks = listOf(
                            "Al-Bukhari" to "Al-Bukhari",
                            "Muslim" to "Muslim",
                            "Abu Dawud" to "Abu Dawud",
                            "At-Tirmithi" to "At-Tirmidhi",
                            "At-Tirmidhi" to "At-Tirmidhi",
                            "An-Nasa'i" to "An-Nasa'i",
                            "Ibn Majah" to "Ibn Majah",
                            "Ahmad" to "Ahmad",
                            "Malik" to "Malik",
                            "Ad-Darimi" to "Ad-Darimi",
                            "Al-Hakim" to "Al-Hakim",
                        )
                        knownBooks.mapNotNull { (pattern, spoken) ->
                            val idx = refText.indexOf(pattern, ignoreCase = true)
                            if (idx >= 0) idx to spoken else null
                        }
                            .sortedBy { it.first }
                            .map { it.second }
                            .distinct()
                            .take(2)
                    } else {
                        emptyList()
                    }
                    val announcement = buildString {
                        if (!chapter.equals(lastAnnouncedChapter, ignoreCase = true)) {
                            append(chapter)
                            if (duaNumber != null) append(". ")
                        }
                        if (duaNumber != null) append("Dua $duaNumber")
                        if (referenceBooks.isNotEmpty()) {
                            append(". From ")
                            append(referenceBooks.joinToString(" and "))
                        }
                    }.trim()
                    if (announcement.isEmpty()) return@announce
                    lastAnnouncedChapter = chapter
                    try {
                        kotlinx.coroutines.withTimeoutOrNull(30_000) {
                            val done = kotlinx.coroutines.CompletableDeferred<Unit>()
                            val started = fortressTtsService.speak(announcement) { done.complete(Unit) }
                            if (started) done.await() // speak() invokes onComplete itself on failure
                        }
                    } catch (e: Exception) {
                        Log.w("DuaAutoPlay", "TTS announcement failed, playing audio directly", e)
                    }
                }

                com.starception.submission.core.ui.ChapterAudioController.playbackDelegate =
                    object : com.starception.submission.core.ui.ChapterAudioController.PlaybackDelegate {
                        override fun play(url: String, title: String) {
                            delegateScope.launch {
                                val source = runCatching {
                                    audioDownloadHelper.resolveFortressAudioUrlToLocalPath(url)
                                }.getOrNull() ?: url
                                // Announce after the (possibly slow) download resolve so
                                // the spoken title leads straight into the audio.
                                announceFortressTrack(title)
                                // Mirror the mini-bar: include the Interests topic name on
                                // the notification's subtitle line when the Dua screen set it.
                                val notifTopic = com.starception.submission.core.ui
                                    .ChapterAudioController.currentTopic
                                    ?.trim()?.takeIf { it.isNotEmpty() }
                                val notifSubtitle = if (notifTopic != null) {
                                    "Fortress of the Muslim · $notifTopic"
                                } else {
                                    "Fortress of the Muslim"
                                }
                                com.starception.submission.services.ChapterRecitationService.play(
                                    appCtx, source, title, notifSubtitle,
                                )
                            }
                        }
                        override fun togglePlayPause() {
                            com.starception.submission.services.ChapterRecitationService.toggle(appCtx)
                        }
                        override fun seekTo(positionMs: Int) { /* seek from bar not wired for service yet */ }
                    }

                // Bridge the service's playback state/progress back to the in-app media bar
                // (GlobalMediaViewModel) and the news-card play/pause icons (ChapterAudioController).
                com.starception.submission.services.ChapterRecitationState.onStateChanged =
                    { playing, title, _ ->
                        com.starception.submission.core.ui.ChapterAudioController.updateExternalState(playing, title)
                        com.starception.submission.media.GlobalMediaViewModel.onFortressPlaybackChanged?.invoke(playing, title)
                    }
                com.starception.submission.services.ChapterRecitationState.onProgressChanged =
                    { pos, dur ->
                        com.starception.submission.media.GlobalMediaViewModel.onFortressProgressChanged?.invoke(pos, dur)
                    }
                // Fortress-of-the-Muslim continuous playback: when a per-dua recitation
                // ("Chapter: Dua N") finishes on its own, play the next dua of the SAME
                // chapter (every dua has its own clip), then fall through to the next
                // chapter. Lives at the app level so it keeps going in the background
                // even after the Dua screen is closed. Chapter-level playback (news
                // card — title without ": Dua ") is left alone, and running past the
                // end of the book simply stops.
                val autoAdvanceNextDua: () -> Unit = {
                    val controller = com.starception.submission.core.ui.ChapterAudioController
                    val completedTitle = controller.currentTitle
                    Log.d("DuaAutoPlay", "onCompletion | completed='$completedTitle'")
                    // Only per-dua/chapter recitations ("Chapter: Dua N") auto-advance.
                    if (completedTitle != null && completedTitle.contains(": Dua ")) {
                        // Chapter titles can themselves contain colons ("Invocation for
                        // someone who says: \"...\""), so split on the ": Dua N" suffix.
                        val completedChapter = completedTitle.substringBeforeLast(": Dua ").trim()
                        val completedPosition = completedTitle.substringAfterLast(": Dua ").trim().toIntOrNull()
                        delegateScope.launch {
                            val dao = com.starception.submission.core.duadatabase.DuaDatabase
                                .getInstance(appCtx).duaDao()
                            // Next dua within the same chapter first.
                            val nextInChapter = if (completedPosition != null) {
                                runCatching {
                                    dao.getDuaAudioByTitleAndPosition(completedChapter, completedPosition + 1)
                                }.getOrNull()?.takeIf { it.isNotBlank() }
                            } else {
                                null
                            }
                            if (nextInChapter != null) {
                                val nextTitle = "$completedChapter: Dua ${completedPosition!! + 1}"
                                Log.d("DuaAutoPlay", "next in chapter -> '$nextTitle'")
                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                    controller.currentTitle = nextTitle
                                    controller.toggle(nextInChapter)
                                }
                                return@launch
                            }
                            // Chapter exhausted — move to the following chapter, preferring
                            // its Dua 1 per-dua clip (matches what the page play buttons
                            // resolve) over the whole-chapter recitation.
                            val next = runCatching {
                                dao.getNextChapterAfter(completedChapter)
                            }.getOrNull()
                            Log.d("DuaAutoPlay", "completedChapter='$completedChapter' nextChapter='${next?.title}'")
                            if (next != null) {
                                val firstDuaClip = runCatching {
                                    dao.getDuaAudioByTitleAndPosition(next.title, 1)
                                }.getOrNull()?.takeIf { it.isNotBlank() }
                                val audio = firstDuaClip ?: next.audioUrl
                                if (audio.isNotBlank()) {
                                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                                        controller.currentTitle = "${next.title}: Dua 1"
                                        controller.toggle(audio)
                                    }
                                }
                            }
                        }
                    }
                }
                com.starception.submission.services.ChapterRecitationState.onCompletion = { autoAdvanceNextDua() }
                com.starception.submission.core.ui.ChapterAudioController.onCompletion = { autoAdvanceNextDua() }

                Log.d("SubmissionApplication", "Background initialization completed")
            } catch (e: Exception) {
                Log.e("SubmissionApplication", "Error during background initialization", e)
            }
        }.apply {
            // Set thread priority to prevent blocking main thread
            priority = AnrPreventionConfig.getBackgroundThreadPriority()
            name = "AppInitThread"
        }.start()
        
        // Pre-warm the in-memory search indices (surahs + quranic duas + verses
        // + fortress chapters) on a background coroutine. Without this, the very
        // first keystroke pays ~160ms to build all four indices, so SQL hits
        // (fortress / ayahs) show before the in-memory hits — the gap is visible
        // to the user.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                inMemorySearchService.preload()
            } catch (e: Exception) {
                Log.w("SubmissionApplication", "Search index preload failed", e)
            }
        }

        Log.d("SubmissionApplication", "Application onCreate completed")
        
        // DISABLE automatic service startup from Application to prevent service timeout ANR
        // Service will only be started from MainActivity.onResume() after user interaction
        Log.d("SubmissionApplication", "Application initialized, service will start only from MainActivity")
    }
    
    /**
     * Clean up any existing service instances to prevent conflicts when app reopens
     */
    private fun cleanupExistingServices() {
        try {
            // Stop any existing prayer notification service to prevent conflicts
            val intent = Intent(this, PrayerNotificationService::class.java)
            stopService(intent)
            Log.d("SubmissionApplication", "Cleaned up existing service instances")
        } catch (e: Exception) {
            Log.e("SubmissionApplication", "Error cleaning up existing services", e)
        }
    }

    override fun newImageLoader(): ImageLoader = imageLoader.get()

    /**
     * Return true if the application is debuggable.
     */
    private fun isDebuggable(): Boolean {
        return 0 != applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE
    }

    /**
     * Set a thread policy that detects all potential problems on the main thread, such as network
     * and disk access.
     *
     * If a problem is found, the offending call will be logged and the application will be killed.
     */
    private fun setStrictModePolicy() {
        if (isDebuggable()) {
            StrictMode.setThreadPolicy(
                Builder().detectAll().penaltyLog().build(),
            )
        }
    }
}
