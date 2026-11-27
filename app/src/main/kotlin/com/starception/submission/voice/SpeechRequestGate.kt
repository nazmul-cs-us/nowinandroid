package com.starception.submission.voice

/** Stop/new speech permanently invalidates old work, even if native synthesis cannot cancel. */
internal class SpeechRequestGate {
    private var generation = 0L

    @Synchronized
    fun begin(replace: () -> Unit = {}): Long {
        generation += 1
        replace()
        return generation
    }

    @Synchronized
    fun isCurrent(request: Long): Boolean = request == generation

    /** Check and the final playback/state mutation must be one indivisible operation. */
    @Synchronized
    fun <T> withCurrent(request: Long, action: () -> T): T? =
        if (request == generation) action() else null
}
