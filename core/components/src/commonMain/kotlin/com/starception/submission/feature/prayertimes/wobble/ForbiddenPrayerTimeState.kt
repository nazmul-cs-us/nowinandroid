package com.starception.submission.feature.prayertimes.wobble

/** Live warning shown while voluntary prayer is restricted. */
data class ForbiddenPrayerTimeState(
    val isActive: Boolean = false,
    val periodKey: String = "",
    val displayText: String = "",
)
