package com.starception.submission.family

import org.json.JSONObject

data class FamilyChatMessage(val id: String, val from: String, val to: String, val text: String, val sequence: Long, val createdAt: Long) {
    companion object { fun parse(o: JSONObject) = FamilyChatMessage(o.getString("id"), o.getString("from"), o.getString("to"), o.getString("text"), o.getLong("sequence"), o.getLong("createdAt")) }
}
data class FamilyConversation(val peer: String, val unread: Int, val lastMessage: FamilyChatMessage?)
data class FamilyNotice(val id: String, val kind: String, val peer: String, val count: Int, val unread: Boolean, val createdAt: Long)
