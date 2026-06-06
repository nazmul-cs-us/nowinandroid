package com.starception.submission.ui.search

import java.time.LocalTime

/** A curated empty-state suggestion the user can tap to run as a query. */
data class PopularSuggestion(
    val display: String,
    val query: String,
)

/**
 * Time-of-day-aware hint text + popular search chips for the search bar.
 *
 * The window definitions follow rough Islamic prayer-time bands. They are
 * resolved at render time so the hint rotates throughout the day without any
 * background work or extra plumbing.
 */
object SearchHints {

    enum class Slot { FAJR_MORNING, FORENOON, DHUHR, ASR, MAGHRIB_EVENING, ISHA_NIGHT, LATE_NIGHT }

    fun currentSlot(now: LocalTime = LocalTime.now()): Slot = when (now.hour) {
        in 4..6 -> Slot.FAJR_MORNING
        in 7..11 -> Slot.FORENOON
        in 12..14 -> Slot.DHUHR
        in 15..17 -> Slot.ASR
        in 18..19 -> Slot.MAGHRIB_EVENING
        in 20..22 -> Slot.ISHA_NIGHT
        else -> Slot.LATE_NIGHT
    }

    /** Hint to show inside the SearchBar pill, e.g. "Try 'morning duas'…". */
    fun hintFor(slot: Slot = currentSlot()): String = rotatingHintsFor(slot).first()

    /**
     * Ordered list of hint candidates for [slot]. The typewriter animation
     * cycles through these one at a time, typing each then erasing before
     * moving on. Mix of duas, surahs, and timing words tied to the slot's
     * prayer-time band keeps suggestions feeling fresh through the day.
     */
    fun rotatingHintsFor(slot: Slot = currentSlot()): List<String> = when (slot) {
        Slot.FAJR_MORNING -> listOf(
            "Try 'morning duas'",
            "Search 'Fajr'",
            "Try 'waking up'",
            "Try 'Ayatul Kursi'",
        )
        Slot.FORENOON -> listOf(
            "Try 'Surah Yasin'",
            "Try 'morning dhikr'",
            "Try 'gratitude'",
            "Search 'Quran'",
        )
        Slot.DHUHR -> listOf(
            "Try 'Dhuhr'",
            "Try 'Surah Mulk'",
            "Try 'patience'",
            "Search 'Ayatul Kursi'",
        )
        Slot.ASR -> listOf(
            "Try 'Asr'",
            "Try 'travel duas'",
            "Try 'Surah Kahf'",
            "Try 'forgiveness'",
        )
        Slot.MAGHRIB_EVENING -> listOf(
            "Try 'evening duas'",
            "Search 'Ar-Rahman'",
            "Try 'Maghrib'",
            "Try 'forgiveness'",
        )
        Slot.ISHA_NIGHT -> listOf(
            "Try 'Ayatul Kursi'",
            "Try 'sleep duas'",
            "Search 'Isha'",
            "Try 'Surah Mulk'",
        )
        Slot.LATE_NIGHT -> listOf(
            "Try 'Tahajjud'",
            "Try 'Surah Sajdah'",
            "Try 'repentance'",
            "Search 'Ar-Rahman'",
        )
    }

    /**
     * Curated popular-search chips shown above recents on the empty state.
     * Ordered roughly by what's most relevant for [slot] — the first few rotate
     * with the time of day while the universal favourites stay anchored at the end.
     */
    fun popularSuggestions(slot: Slot = currentSlot()): List<PopularSuggestion> {
        val universal = listOf(
            PopularSuggestion("Ayatul Kursi", "Ayatul Kursi"),
            PopularSuggestion("Ar-Rahman", "Ar-Rahman"),
            PopularSuggestion("Anxiety", "anxiety"),
            PopularSuggestion("Travel", "travel"),
        )
        val timed = when (slot) {
            Slot.FAJR_MORNING -> listOf(
                PopularSuggestion("Morning duas", "morning"),
                PopularSuggestion("Fajr", "Fajr"),
                PopularSuggestion("Waking up", "waking"),
            )
            Slot.FORENOON -> listOf(
                PopularSuggestion("Surah Yasin", "Yasin"),
                PopularSuggestion("Morning dhikr", "morning"),
                PopularSuggestion("Gratitude", "gratitude"),
            )
            Slot.DHUHR -> listOf(
                PopularSuggestion("Dhuhr", "Dhuhr"),
                PopularSuggestion("Surah Mulk", "Mulk"),
                PopularSuggestion("Patience", "patience"),
            )
            Slot.ASR -> listOf(
                PopularSuggestion("Asr", "Asr"),
                PopularSuggestion("Travel duas", "travel"),
                PopularSuggestion("Surah Kahf", "Kahf"),
            )
            Slot.MAGHRIB_EVENING -> listOf(
                PopularSuggestion("Evening duas", "evening"),
                PopularSuggestion("Maghrib", "Maghrib"),
                PopularSuggestion("Forgiveness", "forgiveness"),
            )
            Slot.ISHA_NIGHT -> listOf(
                PopularSuggestion("Sleep duas", "sleep"),
                PopularSuggestion("Ayatul Kursi", "Ayatul Kursi"),
                PopularSuggestion("Isha", "Isha"),
            )
            Slot.LATE_NIGHT -> listOf(
                PopularSuggestion("Tahajjud", "Tahajjud"),
                PopularSuggestion("Surah Sajdah", "Sajdah"),
                PopularSuggestion("Repentance", "repentance"),
            )
        }
        // De-dupe by query while preserving order: timed ones first, universals after.
        val seen = HashSet<String>()
        val result = ArrayList<PopularSuggestion>(timed.size + universal.size)
        (timed + universal).forEach {
            if (seen.add(it.query.lowercase())) result.add(it)
        }
        return result
    }
}
