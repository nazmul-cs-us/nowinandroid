/*
 * Copyright 2025 Starception
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.starception.submission.core.ui

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Process-wide, single-stream player for chapter recitations shown on news cards.
 * Only one card streams at a time; tapping another card switches the stream.
 * State is Compose snapshot state so cards recompose their play/pause icon.
 */
object ChapterAudioController {
    var currentUrl by mutableStateOf<String?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var loadingUrl by mutableStateOf<String?>(null)
        private set

    /** Title of the current track, set by the play button so the media bar can label it.
     *  Observable (mutableStateOf) so screens can follow app-level continuous playback. */
    var currentTitle by mutableStateOf<String?>(null)

    /** Interests-topic name of the current track (e.g. "Quranic Duas"), set by the Dua
     *  screen alongside [currentTitle] so the media bar's subtitle can show which topic
     *  the playing dua belongs to. Null when unknown (news-card chapter playback). */
    var currentTopic by mutableStateOf<String?>(null)

    private var mp: MediaPlayer? = null

    /**
     * Optional hook, supplied by the app module (which owns the download system), that maps an
     * audio URL to a LOCAL playable path — downloading and caching it first if needed. Returns
     * null when the URL should just be streamed (not a cacheable asset, or download failed).
     *
     * core/ui cannot depend on the app module, so the app registers this at startup. When it is
     * null (tests, other entry points), playback falls back to streaming the URL directly.
     */
    var localAudioResolver: (suspend (String) -> String?)? = null

    /**
     * Bridge callbacks to the app's global media controller (which owns the mini-bar with the
     * progress sweep). core/ui can't see the app module, so the app registers these at startup.
     * Mirrors the hadith media integration.
     */
    var onPlaybackStateChanged: ((isPlaying: Boolean, title: String) -> Unit)? = null
    var onProgressChanged: ((positionMs: Int, durationMs: Int) -> Unit)? = null

    /**
     * Fired when the current recitation finishes on its own (natural end — NOT a pause or stop).
     * The app bridges the [ChapterRecitationService] completion here; the Dua screen uses it to
     * auto-play the next dua in the chapter.
     */
    var onCompletion: (() -> Unit)? = null

    /**
     * Ordered dua titles ("Chapter: Dua N") for the list currently being played, set by the
     * Dua screen so the app-level auto-advance can continue through the topic in the
     * background. Duas in one chapter share a single chapter-level recitation, so the advance
     * skips to the next chapter's dua.
     */
    var playlistTitles: List<String> = emptyList()

    /**
     * Optional delegate, supplied by the app, that plays through a foreground MediaSession service
     * (ChapterRecitationService) so playback shows a system notification + lock-screen controls.
     * When set, toggle() delegates play/pause to it instead of using the in-app MediaPlayer.
     * The service reports state back via [updateExternalState] / [updateExternalProgress].
     *   play(url, title) — start (or toggle if same url); togglePlayPause() — pause/resume current.
     */
    var playbackDelegate: PlaybackDelegate? = null

    interface PlaybackDelegate {
        fun play(url: String, title: String)
        fun togglePlayPause()
        fun seekTo(positionMs: Int)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var resolveJob: Job? = null
    private var progressJob: Job? = null

    fun toggle(url: String) {
        // Delegate to the notification-backed service when available.
        playbackDelegate?.let { delegate ->
            if (currentUrl == url) {
                delegate.togglePlayPause()
            } else {
                currentUrl = url
                loadingUrl = url
                delegate.play(url, currentTitle.orEmpty())
            }
            return
        }

        val player = mp
        if (currentUrl == url && player != null) {
            if (isPlaying) {
                player.pause(); isPlaying = false
                onPlaybackStateChanged?.invoke(false, currentTitle.orEmpty())
                stopProgressTicker()
            } else {
                player.start(); isPlaying = true
                onPlaybackStateChanged?.invoke(true, currentTitle.orEmpty())
                startProgressTicker()
            }
            return
        }
        if (loadingUrl == url) return
        // Stop any current playback, but do NOT cancel an in-flight resolve/download here —
        // the download must survive re-taps/recomposition so it can finish and cache. The
        // currentUrl guard below discards a stale resolve if the user switched tracks.
        stopPlayback()
        currentUrl = url
        loadingUrl = url

        // Resolve to a local file (download-and-cache) off the resolver if present, then play.
        // Falls back to streaming the original URL when there's no resolver or it returns null.
        val resolver = localAudioResolver
        if (resolver == null) {
            play(url, url)
            return
        }
        resolveJob = scope.launch {
            val source = runCatching { resolver(url) }.getOrNull() ?: url
            // Guard against a newer toggle() having superseded this request.
            if (currentUrl == url) {
                play(url, source)
            }
        }
    }

    /** Play/pause the currently loaded track — invoked by the global media mini-bar. */
    fun togglePlayPause() {
        playbackDelegate?.let { it.togglePlayPause(); return }
        currentUrl?.let { toggle(it) }
    }

    /**
     * Called by the app when the notification-backed service reports its state, so the news-card
     * play/pause icons (Compose state) stay in sync with the service/notification.
     */
    fun updateExternalState(playing: Boolean, title: String) {
        isPlaying = playing
        loadingUrl = null
        currentTitle = title
    }

    /** Seek the current track — invoked by the global media mini-bar. */
    fun seekTo(positionMs: Int) {
        playbackDelegate?.let { it.seekTo(positionMs); return }
        mp?.let { player ->
            runCatching { player.seekTo(positionMs) }
            onProgressChanged?.invoke(player.currentPosition, player.duration)
        }
    }

    /**
     * Start MediaPlayer for [requestUrl] (the identity used for state) sourced from
     * [dataSource] (a local file path when cached, otherwise the remote URL to stream).
     */
    private fun play(requestUrl: String, dataSource: String) {
        val newPlayer = MediaPlayer()
        newPlayer.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        newPlayer.setOnPreparedListener {
            loadingUrl = null; newPlayer.start(); isPlaying = true
            onPlaybackStateChanged?.invoke(true, currentTitle.orEmpty())
            onProgressChanged?.invoke(0, newPlayer.duration)
            startProgressTicker()
        }
        newPlayer.setOnCompletionListener {
            isPlaying = false
            stopProgressTicker()
            onPlaybackStateChanged?.invoke(false, currentTitle.orEmpty())
            onCompletion?.invoke()
        }
        newPlayer.setOnErrorListener { _, _, _ ->
            loadingUrl = null; isPlaying = false; currentUrl = null
            stopProgressTicker()
            onPlaybackStateChanged?.invoke(false, currentTitle.orEmpty())
            true
        }
        mp = newPlayer
        try {
            newPlayer.setDataSource(dataSource)
            newPlayer.prepareAsync()
        } catch (_: Exception) {
            loadingUrl = null; isPlaying = false; currentUrl = null
            onPlaybackStateChanged?.invoke(false, currentTitle.orEmpty())
        }
    }

    /** Emit position/duration ~2x/sec while playing so the mini-bar progress advances. */
    private fun startProgressTicker() {
        stopProgressTicker()
        progressJob = scope.launch {
            while (isActive) {
                val player = mp ?: break
                if (isPlaying) {
                    runCatching {
                        onProgressChanged?.invoke(player.currentPosition, player.duration)
                    }
                }
                delay(500)
            }
        }
    }

    private fun stopProgressTicker() {
        progressJob?.cancel()
        progressJob = null
    }

    /** Tear down only the MediaPlayer, leaving any in-flight resolve/download running. */
    private fun stopPlayback() {
        stopProgressTicker()
        mp?.let { runCatching { it.stop() }; it.release() }
        mp = null; isPlaying = false; loadingUrl = null
    }

    fun release() {
        resolveJob?.cancel()
        resolveJob = null
        stopPlayback()
        currentUrl = null
        onPlaybackStateChanged?.invoke(false, currentTitle.orEmpty())
    }
}
