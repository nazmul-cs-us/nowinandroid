package com.starception.submission.islamic.shared

import android.util.Log
import java.time.LocalDate
import java.time.chrono.HijrahChronology
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

/**
 * Thin wrapper around java.time.chrono.HijrahChronology (Umm al-Qura per JDK docs).
 * Returns null on the rare locales / out-of-range dates where the JDK chronology throws,
 * so callers can degrade gracefully instead of crashing the UI.
 */
object HijriClock {

    data class Today(val day: Int, val month: Int, val year: Int)

    fun today(now: LocalDate = LocalDate.now()): Today? = try {
        val hd: HijrahDate = HijrahChronology.INSTANCE.date(now)
        Today(
            day = hd.get(ChronoField.DAY_OF_MONTH),
            month = hd.get(ChronoField.MONTH_OF_YEAR),
            year = hd.get(ChronoField.YEAR),
        )
    } catch (e: Exception) {
        Log.w("HijriClock", "HijrahChronology lookup failed for $now", e)
        null
    }
}
