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

package com.starception.submission.shared.audio

/** A whole-surah MP3 endpoint supported by AVPlayer. */
fun quranAudioUrl(surahNumber: Int): String =
    "https://download.quranicaudio.com/qdc/mishari_al_afasy/murattal/$surahNumber.mp3"

/** Resolves/downloads one Surah audio file for the selected translation. */
expect suspend fun resolveQuranAudioUrl(
    surahNumber: Int,
    translationCode: String,
): String?

/** Prepares a chapter recording for the platform player (iOS requires a local file). */
expect suspend fun resolveFortressAudioUrl(url: String): String?

expect class QuranAudioPlayer() {
    fun play(url: String): Boolean
    fun pause()
    fun stop()

    /** Stream position in seconds, 0f when nothing is loaded. */
    fun positionSeconds(): Float

    /** Stream duration in seconds, 0f when unknown. */
    fun durationSeconds(): Float

    /** True while the stream is actively playing. */
    fun isPlaying(): Boolean

    /** Seek to a position in seconds. */
    fun seekTo(seconds: Float)
}

/** Select the requested recording, matching numeric surah prefixes rather than spelling. */
internal fun selectQuranAudioAsset(
    surahNumber: Int,
    translationCode: String,
    assets: Collection<com.starception.submission.core.assetcache.AssetEntry>,
): com.starception.submission.core.assetcache.AssetEntry? {
    val language = quranAudioLanguage(translationCode)
    return assets.firstOrNull { asset ->
        asset.category == "quran_audio_$language" &&
            asset.cdnKey.substringAfterLast('/').takeWhile(Char::isDigit).toIntOrNull() == surahNumber
    }
}

/** Reading's Arabic-only preference is an empty code, not necessarily "ar". */
internal fun quranAudioLanguage(translationCode: String): String = when (translationCode.lowercase()) {
    "bn" -> "bengali"
    "en" -> "english"
    "hi" -> "hindi"
    "ur" -> "urdu"
    else -> "arabic"
}

/** Every CDN language recording uses Mishary; only a selected Arabic source overrides it. */
internal fun quranPlaybackReciterName(translationCode: String, selected: QuranReciterSource?): String =
    if (quranAudioLanguage(translationCode) == "arabic") selected?.displayName ?: "Mishary Rashid Al-Alafasy"
    else "Mishary Rashid Al-Alafasy"
