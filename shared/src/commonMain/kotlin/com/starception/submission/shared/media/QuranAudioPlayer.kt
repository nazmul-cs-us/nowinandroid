/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.starception.submission.shared.audio

/** A whole-surah MP3 endpoint supported by AVPlayer. */
fun quranAudioUrl(surahNumber: Int): String =
    "https://download.quranicaudio.com/qdc/mishari_al_afasy/murattal/$surahNumber.mp3"

expect class QuranAudioPlayer() {
    fun play(url: String): Boolean
    fun pause()
    fun stop()
}
