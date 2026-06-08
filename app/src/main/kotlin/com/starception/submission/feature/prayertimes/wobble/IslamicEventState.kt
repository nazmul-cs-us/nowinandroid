package com.starception.submission.feature.prayertimes.wobble

/**
 * State for the Hijri-calendar event banner shown in PullToSyncContainer.
 * Surfaces single-day or short-window Islamic events (Arafah, Ashura,
 * Laylat al-Qadr, Eid, Hijri new year) so the whole app reflects today's
 * devotional context, not just the search screen.
 *
 * Stacking inside PullToSyncContainer: prayer alert > download > media >
 * islamic event > silent mode > mushaf.
 */
data class IslamicEventState(
    val isActive: Boolean = false,
    val eventKey: String = "",
    val title: String = "",
    val searchQuery: String = "",
)
