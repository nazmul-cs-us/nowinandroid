package com.starception.submission.family

class FamilyLastSeenPolicy {
    fun shortLabel(presence: String, lastSeenAt: Long, now: Long, available: Boolean): String =
        label(presence, lastSeenAt, now, available)
            .replace(" minutes ago", "m ago").replace(" minute ago", "m ago")
            .replace(" hours ago", "h ago").replace(" hour ago", "h ago")
            .replace(" days ago", "d ago").replace(" day ago", "d ago")

    fun label(presence: String, lastSeenAt: Long, now: Long, available: Boolean): String {
        if (!available) return "Status unavailable"
        if (presence == "online") return "Online"
        if (presence != "offline") return "Status unavailable"
        if (lastSeenAt <= 0) return "Offline"
        val minutes = ((now - lastSeenAt).coerceAtLeast(0) / 60000)
        return when {
            minutes < 1 -> "Last seen just now"
            minutes < 60 -> "Last seen $minutes ${if (minutes == 1L) "minute" else "minutes"} ago"
            minutes < 1440 -> {
                val hours = minutes / 60
                "Last seen $hours ${if (hours == 1L) "hour" else "hours"} ago"
            }
            else -> {
                val days = minutes / 1440
                "Last seen $days ${if (days == 1L) "day" else "days"} ago"
            }
        }
    }
}
