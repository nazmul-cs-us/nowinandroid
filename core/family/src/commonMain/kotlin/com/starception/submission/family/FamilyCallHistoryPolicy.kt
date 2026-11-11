package com.starception.submission.family

/** Display rules shared by Android and iOS; records themselves are authored by the server. */
class FamilyCallHistoryPolicy {
    fun valid(kind: String, outcome: String): Boolean = kind in setOf("voice", "video") &&
        outcome in setOf("answered", "cancelled", "declined", "missed", "interrupted")

    fun title(kind: String): String = if (kind == "video") "Video call" else "Voice call"

    fun direction(outgoing: Boolean): String = if (outgoing) "Outgoing" else "Incoming"

    fun status(outcome: String, outgoing: Boolean): String = when (outcome) {
        "answered" -> "Answered"
        "cancelled" -> if (outgoing) "Cancelled" else "Missed"
        "missed" -> if (outgoing) "No answer" else "Missed"
        "declined" -> "Declined"
        "interrupted" -> "Interrupted"
        else -> "Ended"
    }

    fun missed(outcome: String, outgoing: Boolean): Boolean = !outgoing && outcome in setOf("cancelled", "missed")

    fun duration(seconds: Long): String {
        val safe = seconds.coerceAtLeast(0)
        return when {
            safe >= 3600 -> "${safe / 3600}h ${(safe % 3600) / 60}m"
            safe >= 60 -> "${safe / 60}m ${safe % 60}s"
            else -> "${safe}s"
        }
    }
}
