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

    private var mp: MediaPlayer? = null

    fun toggle(url: String) {
        val player = mp
        if (currentUrl == url && player != null) {
            if (isPlaying) {
                player.pause(); isPlaying = false
            } else {
                player.start(); isPlaying = true
            }
            return
        }
        if (loadingUrl == url) return
        release()
        currentUrl = url
        loadingUrl = url
        val newPlayer = MediaPlayer()
        newPlayer.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        newPlayer.setOnPreparedListener {
            loadingUrl = null; newPlayer.start(); isPlaying = true
        }
        newPlayer.setOnCompletionListener { isPlaying = false }
        newPlayer.setOnErrorListener { _, _, _ ->
            loadingUrl = null; isPlaying = false; currentUrl = null; true
        }
        mp = newPlayer
        try {
            newPlayer.setDataSource(url)
            newPlayer.prepareAsync()
        } catch (_: Exception) {
            loadingUrl = null; isPlaying = false; currentUrl = null
        }
    }

    fun release() {
        mp?.let { runCatching { it.stop() }; it.release() }
        mp = null; isPlaying = false; loadingUrl = null; currentUrl = null
    }
}
