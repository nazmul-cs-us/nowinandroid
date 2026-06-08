package com.starception.submission.islamic.shared

import com.starception.submission.feature.prayertimes.wobble.IslamicEventState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves today's Islamic-event banner from the local Hijri date.
 *
 * V1 is fully offline — it uses HijriClock (java.time HijrahChronology) and a small
 * hand-written rule table for the highest-impact single-day events. The Aladhan API
 * has a richer `holidays` field per date; that can layer on top later without
 * changing the public surface of this provider.
 *
 * Re-resolves every 5 minutes so a banner that activates at Maghrib (Laylat al-Qadr)
 * or rolls past midnight reaches the UI without a manual refresh.
 */
@Singleton
class IslamicEventStateProvider @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(resolve())
    val state: StateFlow<IslamicEventState> = _state.asStateFlow()

    init {
        scope.launch {
            while (isActive) {
                _state.value = resolve()
                delay(TimeUnit.MINUTES.toMillis(5))
            }
        }
    }

    private fun resolve(
        now: LocalDate = LocalDate.now(),
        time: LocalTime = LocalTime.now(),
    ): IslamicEventState {
        val hijri = HijriClock.today(now) ?: return IslamicEventState()
        return when {
            hijri.month == 1 && hijri.day == 1 -> IslamicEventState(
                isActive = true,
                eventKey = "hijri_new_year",
                title = "Hijri New Year",
                searchQuery = "Muharram",
            )
            hijri.month == 1 && hijri.day in 9..10 -> IslamicEventState(
                isActive = true,
                eventKey = "ashura",
                title = "Day of Ashura",
                searchQuery = "Ashura",
            )
            hijri.month == 9 && hijri.day in 21..30 && isOddNight(hijri.day) && isNightTime(time) ->
                IslamicEventState(
                    isActive = true,
                    eventKey = "laylat_qadr",
                    title = "Tonight may be Laylat al-Qadr",
                    searchQuery = "Qadr",
                )
            hijri.month == 10 && hijri.day == 1 -> IslamicEventState(
                isActive = true,
                eventKey = "eid_fitr",
                title = "Eid Mubarak — Eid al-Fitr",
                searchQuery = "Eid",
            )
            hijri.month == 12 && hijri.day == 9 -> IslamicEventState(
                isActive = true,
                eventKey = "arafah",
                title = "Day of Arafah",
                searchQuery = "Arafah",
            )
            hijri.month == 12 && hijri.day == 10 -> IslamicEventState(
                isActive = true,
                eventKey = "eid_adha",
                title = "Eid Mubarak — Eid al-Adha",
                searchQuery = "Eid",
            )
            hijri.month == 12 && hijri.day in 11..13 -> IslamicEventState(
                isActive = true,
                eventKey = "tashreeq",
                title = "Days of Tashreeq",
                searchQuery = "Takbir",
            )
            else -> IslamicEventState()
        }
    }

    /** Odd nights of the last 10 of Ramadan: 21, 23, 25, 27, 29. */
    private fun isOddNight(day: Int): Boolean = day % 2 == 1

    /** Rough "night" window: after Maghrib through Fajr, without depending on prayer-time data. */
    private fun isNightTime(time: LocalTime): Boolean {
        val hour = time.hour
        return hour >= 18 || hour < 5
    }
}
