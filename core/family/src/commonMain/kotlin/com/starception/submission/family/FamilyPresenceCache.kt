package com.starception.submission.family

/** Memory-only availability. Call eligibility always remains a server decision. */
class FamilyPresenceCache {
    private var epoch = ""
    private data class Value(val status: String, val revision: Long)
    private val values = mutableMapOf<String, Value>()

    fun beginEpoch(next: String) {
        if (next.isNotEmpty() && next != epoch) { values.clear(); epoch = next }
    }
    fun apply(jid: String, status: String, revision: Long): Boolean {
        if ((status != "online" && status != "offline") || revision <= 0 || revision <= (values[jid]?.revision ?: 0)) return false
        values[jid] = Value(status, revision)
        return true
    }
    fun status(jid: String): String = values[jid]?.status ?: "unknown"
    fun retain(accepted: List<String>) { values.keys.retainAll(accepted.toSet()) }
    fun clear() { epoch = ""; values.clear() }
}
