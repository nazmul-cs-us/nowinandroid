package com.starception.submission.shared.audio

/** The shared module is not the Android app's media owner. */
actual class QuranAudioPlayer actual constructor() {
    actual fun play(url: String): Boolean = false
    actual fun pause() = Unit
    actual fun stop() = Unit
}
