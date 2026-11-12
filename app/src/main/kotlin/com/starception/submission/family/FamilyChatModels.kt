package com.starception.submission.family

import org.json.JSONObject

data class FamilyChatMessage(val id: String, val from: String, val to: String, val text: String, val sequence: Long, val createdAt: Long,
    val call: FamilyChatCall? = null, val deliveredAt: Long = 0, val seenAt: Long = 0) {
    fun mergeReceipt(other: FamilyChatMessage): FamilyChatMessage = other.copy(
        deliveredAt = maxOf(deliveredAt, other.deliveredAt), seenAt = maxOf(seenAt, other.seenAt))
    fun preview(me: String): String = call?.let {
        val policy = FamilyCallHistoryPolicy()
        "${policy.title(it.kind)} · ${policy.status(it.outcome, from == me)}"
    } ?: text

    companion object {
        fun parse(o: JSONObject): FamilyChatMessage {
            val id = o.getString("id")
            val call = if (o.optString("type") == "call") FamilyChatCall.parse(o.optJSONObject("call"))
                ?.takeIf { id == "call:${it.id}" } else null
            return FamilyChatMessage(id, o.getString("from"), o.getString("to"), o.getString("text"),
                o.getLong("sequence"), o.getLong("createdAt"), call,
                o.optLong("deliveredAt").coerceAtLeast(0), o.optLong("seenAt").coerceAtLeast(0))
        }
    }
}
data class FamilyChatCall(val id: String, val kind: String, val outcome: String, val durationSeconds: Long? = null) {
    companion object {
        fun parse(value: JSONObject?): FamilyChatCall? = runCatching {
            val o = requireNotNull(value)
            val id = o.getString("id"); val kind = o.getString("kind"); val outcome = o.getString("outcome")
            require(id.isNotBlank() && FamilyCallHistoryPolicy().valid(kind, outcome))
            val duration = if (o.has("durationSeconds") && !o.isNull("durationSeconds")) o.getLong("durationSeconds") else null
            require(duration == null || duration in 0..86400)
            FamilyChatCall(id, kind, outcome, duration)
        }.getOrNull()
    }
}
data class FamilyConversation(val peer: String, val unread: Int, val lastMessage: FamilyChatMessage?)
data class FamilyNotice(val id: String, val kind: String, val peer: String, val count: Int, val unread: Boolean, val createdAt: Long)
