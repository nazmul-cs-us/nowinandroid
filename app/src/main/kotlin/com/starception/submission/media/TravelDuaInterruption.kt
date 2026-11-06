package com.starception.submission.media

/** Main-thread arbitration for the short, higher-priority travel dua. */
internal class TravelDuaInterruptionController {
    private data class Participant(val group: Any, val pause: () -> (() -> Unit)?)
    private data class Suspended(val group: Any, val resume: () -> Unit)
    private val participants = linkedMapOf<Any, Participant>()
    private val suspended = linkedMapOf<Any, Suspended>()
    private data class Deferred(
        val group: Any,
        val resumeGroupBeforeStart: Boolean,
        val start: () -> Unit,
        val discard: () -> Unit,
    )
    private val deferred = linkedMapOf<Any, Deferred>()
    private var interruptionOwner: Any? = null

    val isActive: Boolean get() = interruptionOwner != null
    val hasWaitingPlayback: Boolean get() = suspended.isNotEmpty() || deferred.isNotEmpty()

    fun register(owner: Any, group: Any = owner, pause: () -> (() -> Unit)?) {
        participants[owner] = Participant(group, pause)
    }

    fun unregister(owner: Any) {
        participants.remove(owner)
        cancel(owner)
    }

    fun cancel(owner: Any) {
        suspended.remove(owner)
        deferred.remove(owner)?.discard?.invoke()
    }

    fun cancelGroup(group: Any) {
        suspended.entries.removeAll { it.value.group === group }
        val cancelled = deferred.filterValues { it.group === group }
        cancelled.keys.forEach { deferred.remove(it) }
        cancelled.values.forEach { it.discard() }
    }

    /** Check immediately before starting audio, not just before asynchronous preparation. */
    fun deferIfInterrupted(
        owner: Any,
        onDiscard: () -> Unit = {},
        resumeGroupBeforeStart: Boolean = false,
        group: Any? = null,
        start: () -> Unit,
    ): Boolean {
        if (!isActive) return false
        suspended.remove(owner)
        deferred.remove(owner)?.discard?.invoke()
        deferred[owner] = Deferred(group ?: participants[owner]?.group ?: owner, resumeGroupBeforeStart, start, onDiscard)
        return true
    }

    fun begin(owner: Any): Boolean {
        if (isActive) return false
        interruptionOwner = owner
        try {
            participants.toList().forEach { (key, participant) ->
                participant.pause()?.let {
                    if (participants[key] === participant) suspended[key] = Suspended(participant.group, it)
                }
            }
        } catch (failure: Exception) {
            // Never start the dua over a source that failed to pause.
            interruptionOwner = null
            val resumes = suspended.values.map { it.resume }
            suspended.clear()
            resumes.forEach { it() }
            return false
        }
        return true
    }

    /** A newer user play request wins over the track interrupted earlier. */
    fun finish(owner: Any): Boolean {
        if (interruptionOwner !== owner) return false
        interruptionOwner = null
        val chosen = deferred.values.lastOrNull()
        val discarded = deferred.values.filter { it !== chosen }
        val group = chosen?.group ?: suspended.values.lastOrNull()?.group
        // A service-owned TTS queue and its native audio track are one source.
        // Resume its queue state before releasing a prepared/deferred clip.
        val resumes = if (chosen == null || chosen.resumeGroupBeforeStart) {
            suspended.values.filter { it.group === group }.map { it.resume }
        } else emptyList()
        deferred.clear()
        suspended.clear()
        discarded.forEach { it.discard() }
        resumes.forEach { it() }
        chosen?.start?.invoke()
        return resumes.isNotEmpty() || chosen != null
    }
}

internal object TravelDuaInterruption {
    val controller = TravelDuaInterruptionController()
}
