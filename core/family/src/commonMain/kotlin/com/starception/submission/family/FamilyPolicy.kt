package com.starception.submission.family

/** The server owns relationship state. Local checks only control presentation. */
class FamilyPolicy {
    fun canCall(relationship: String): Boolean = relationship == "accepted"

    fun normalizeJid(input: String): String? {
        val value = input.trim().lowercase()
        if (value.length !in 3..254 || value.any { it.isWhitespace() || it in "/<>\"'&" }) return null
        val parts = value.split('@')
        if (parts.size != 2 || parts.any { it.isBlank() }) return null
        return value
    }
}

/** Retains terminal state so delayed/replayed ringing cannot revive a finished call. */
class FamilyCallReducer {
    var callId: String = ""
        private set
    var version: Long = -1
        private set
    var stage: String = "idle"
        private set

    fun apply(id: String, revision: Long, next: String, expiresAt: Long, now: Long): Boolean {
        if (id.isBlank() || revision < 1 || next !in setOf("ringing", "active", "ended")) return false
        if (id == callId && (revision <= version || stage == "ended")) return false
        if (id != callId && stage in setOf("ringing", "active")) return false
        callId = id
        version = revision
        stage = if (next == "ringing" && expiresAt <= now) "ended" else next
        return true
    }

    fun finishCall(id: String) { if (callId == id) stage = "ended" }
    fun clear() { callId = ""; version = -1; stage = "idle" }
}

/** Coordinates microphone/audio ownership across existing media features. */
object FamilyAudioSession {
    private val active = kotlinx.coroutines.flow.MutableStateFlow(false)
    val state: kotlinx.coroutines.flow.StateFlow<Boolean> = active
    val isActive: Boolean get() = active.value
    fun setActive(active: Boolean) { this.active.value = active }
}
