package com.starception.submission.feature.prayertimes

import com.starception.submission.core.duadatabase.Dua
import com.starception.submission.core.model.data.BukhariBook
import com.starception.submission.core.model.data.BukhariBooks
import com.starception.submission.feature.quran.QuranData
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/** Fortress chapters used by the contextual dashboard recommendation. */
internal val CONTEXTUAL_DUA_CHAPTER_IDS = setOf(
    1,   // Waking up
    27,  // Morning and evening remembrance
    28,  // Before sleeping
    29,  // Stirring in the night
    30,  // Fear or loneliness before sleep
    34,  // Worry and grief
    35,  // Anguish
    43,  // Difficulty
    44,  // After committing a sin
    46,  // An unwanted outcome
    82,  // Anger
    106, // Pleasing or displeasing news
    123, // Something pleasing happens
    126, // Fright
    129, // Repentance and forgiveness
    130, // Remembrance of Allah
)

internal sealed interface ContextualRecommendationTarget {
    data class Surah(
        val number: Int,
        val name: String,
    ) : ContextualRecommendationTarget

    data class FortressDua(
        val dua: Dua,
    ) : ContextualRecommendationTarget

    data class Bukhari(
        val book: BukhariBook,
    ) : ContextualRecommendationTarget
}

internal data class ContextualInsightRecommendation(
    val title: String,
    val supportingText: String,
    val footerText: String,
    val actionDescription: String,
    val target: ContextualRecommendationTarget,
)

private data class RecommendationWindow(
    val name: String,
    val surahCandidates: List<Int>,
    val duaChapterCandidates: List<Int>,
    val bukhariBookCandidates: List<Int>,
    val surahTitlePrefix: String,
)

/**
 * Builds a deterministic, private recommendation from the local date and time.
 *
 * Friday remains dedicated to Al-Kahf. On other days the source rotates between
 * Quran, Fortress of the Muslim, and a playable Sahih al-Bukhari book. Each time
 * window has its own relevant shortlist. Missing Fortress data gracefully falls
 * back to Quran.
 */
internal fun buildContextualInsightRecommendation(
    date: LocalDate,
    time: LocalTime,
    fortressDuasByChapter: Map<Int, List<Dua>>,
    locale: Locale = Locale.getDefault(),
): ContextualInsightRecommendation {
    val dayName = date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)

    if (date.dayOfWeek == DayOfWeek.FRIDAY) {
        val surah = QuranData.surahs.first { it.number == 18 }
        return ContextualInsightRecommendation(
            title = "Make time for ${surah.nameEnglish}",
            supportingText = surah.nameArabic,
            footerText = "Quran · Surah ${surah.number} · Friday",
            actionDescription = "Open ${surah.nameEnglish}",
            target = ContextualRecommendationTarget.Surah(
                number = surah.number,
                name = surah.nameEnglish,
            ),
        )
    }

    val window = recommendationWindow(time)
    // Rotate by day so recommendations stay stable for the entire day.
    when (date.dayOfYear % 3) {
        0 -> {
            val candidateIndex = Math.floorMod(
                date.toEpochDay(),
                window.bukhariBookCandidates.size.toLong(),
            ).toInt()
            val book = BukhariBooks.find(window.bukhariBookCandidates[candidateIndex])
            if (book != null) {
                return ContextualInsightRecommendation(
                    title = "Listen: ${book.nameEnglish}",
                    supportingText = book.nameArabic,
                    footerText = "Sahih al-Bukhari · Book ${book.id} · ${book.hadithCount} hadiths",
                    actionDescription = "Play ${book.nameEnglish} from Sahih al-Bukhari",
                    target = ContextualRecommendationTarget.Bukhari(book),
                )
            }
        }

        1 -> {
            selectFortressDua(date, window, fortressDuasByChapter)?.let { dua ->
                return ContextualInsightRecommendation(
                    title = contextualDuaTitle(dua.chapterId, dua.chapterTitle),
                    supportingText = dua.previewText(),
                    footerText = "Fortress of the Muslim · Dua ${dua.position} · ${window.name}",
                    actionDescription = "Open ${dua.chapterTitle}",
                    target = ContextualRecommendationTarget.FortressDua(dua),
                )
            }
        }
    }

    val candidateIndex = Math.floorMod(
        date.toEpochDay(),
        window.surahCandidates.size.toLong(),
    ).toInt()
    val surah = QuranData.surahs.first {
        it.number == window.surahCandidates[candidateIndex]
    }
    return ContextualInsightRecommendation(
        title = "${window.surahTitlePrefix} ${surah.nameEnglish}",
        supportingText = surah.nameArabic,
        footerText = "Quran · Surah ${surah.number} · $dayName ${window.name}",
        actionDescription = "Open ${surah.nameEnglish}",
        target = ContextualRecommendationTarget.Surah(
            number = surah.number,
            name = surah.nameEnglish,
        ),
    )
}

/**
 * Returns an audio-first recommendation while driving is detected.
 *
 * The suggestion remains deterministic for the current date/time window and
 * always targets a Bukhari book because that action starts playback directly;
 * it never opens a reading surface or starts audio without a user tap.
 */
internal fun buildDrivingInsightRecommendation(
    date: LocalDate,
    time: LocalTime,
): ContextualInsightRecommendation {
    val window = recommendationWindow(time)
    val candidateIndex = Math.floorMod(
        date.toEpochDay(),
        window.bukhariBookCandidates.size.toLong(),
    ).toInt()
    val book = checkNotNull(BukhariBooks.find(window.bukhariBookCandidates[candidateIndex]))
    return ContextualInsightRecommendation(
        title = "Listen: ${book.nameEnglish}",
        supportingText = "Hands-free Sahih al-Bukhari playback",
        footerText = "Driving mode · Book ${book.id} · ${book.hadithCount} hadiths",
        actionDescription = "Play ${book.nameEnglish} from Sahih al-Bukhari",
        target = ContextualRecommendationTarget.Bukhari(book),
    )
}

private fun recommendationWindow(time: LocalTime): RecommendationWindow = when (time.hour) {
    in 5..10 -> RecommendationWindow(
        name = "morning",
        surahCandidates = listOf(93, 94, 91),
        duaChapterCandidates = listOf(1, 27, 130),
        bukhariBookCandidates = listOf(2, 3, 80),
        surahTitlePrefix = "Begin gently with",
    )

    in 11..15 -> RecommendationWindow(
        name = "afternoon",
        surahCandidates = listOf(55, 49, 103),
        duaChapterCandidates = listOf(43, 44, 129, 130),
        bukhariBookCandidates = listOf(8, 9, 78),
        surahTitlePrefix = "Pause and reflect with",
    )

    in 16..18 -> RecommendationWindow(
        name = "evening",
        surahCandidates = listOf(103, 92, 55),
        duaChapterCandidates = listOf(27, 106, 123, 129),
        bukhariBookCandidates = listOf(66, 80, 81),
        surahTitlePrefix = "Reset your evening with",
    )

    in 19..22 -> RecommendationWindow(
        name = "night",
        surahCandidates = listOf(67, 32, 112),
        duaChapterCandidates = listOf(28, 29, 30, 34),
        bukhariBookCandidates = listOf(19, 80, 81),
        surahTitlePrefix = "Close the day with",
    )

    else -> RecommendationWindow(
        name = "quiet hours",
        surahCandidates = listOf(73, 67, 32),
        duaChapterCandidates = listOf(34, 35, 126, 129),
        bukhariBookCandidates = listOf(19, 80, 81),
        surahTitlePrefix = "Take a quiet moment with",
    )
}

private fun selectFortressDua(
    date: LocalDate,
    window: RecommendationWindow,
    fortressDuasByChapter: Map<Int, List<Dua>>,
): Dua? {
    val availableChapters = window.duaChapterCandidates.filter {
        !fortressDuasByChapter[it].isNullOrEmpty()
    }
    if (availableChapters.isEmpty()) return null

    val chapterIndex = Math.floorMod(
        date.toEpochDay(),
        availableChapters.size.toLong(),
    ).toInt()
    val duas = fortressDuasByChapter.getValue(availableChapters[chapterIndex])
    val duaIndex = Math.floorMod(
        date.toEpochDay() + availableChapters[chapterIndex],
        duas.size.toLong(),
    ).toInt()
    return duas[duaIndex]
}

private fun Dua.previewText(): String = sequenceOf(
    translation,
    transliteration,
    arabic,
)
    .filterNotNull()
    .map { it.replace(Regex("\\s+"), " ").trim() }
    .firstOrNull { it.isNotBlank() }
    ?: chapterTitle

private fun contextualDuaTitle(chapterId: Int, fallback: String): String = when (chapterId) {
    1 -> "Start the day with this dua"
    27 -> "Morning and evening remembrance"
    28 -> "A dua before sleep"
    29 -> "When waking in the night"
    30 -> "For calm before sleep"
    34 -> "For worry and grief"
    35 -> "For moments of anguish"
    43 -> "When something feels difficult"
    44 -> "Return to Allah after a mistake"
    46 -> "When plans do not work out"
    82 -> "For a moment of anger"
    106 -> "Remember Allah in every outcome"
    123 -> "When something brings joy"
    126 -> "When feeling frightened"
    129 -> "Repentance and forgiveness"
    130 -> "Remembering Allah"
    else -> fallback
}
