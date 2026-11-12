package com.starception.submission.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.starception.submission.core.model.data.ShamayelBooks
import com.starception.submission.shared.content.SharedContentStore
import com.starception.submission.shared.content.createSharedFortressRepository
import com.starception.submission.shared.hadith.createSharedHadithRepository
import com.starception.submission.shared.hadith.SharedHadith
import com.starception.submission.shared.quran.QuranTranslationLanguage
import com.starception.submission.shared.quran.createQuranVerseRepository
import com.starception.submission.shared.translation.SharedTranslationService
import kotlinx.coroutines.CancellationException

internal data class HomeReadingText(val text: String, val notice: String)

internal fun localHomeHadithReading(hadith: SharedHadith?, language: String): HomeReadingText? {
    hadith ?: return null
    val text = if (language == "ar" || language.isEmpty()) hadith.arabic else hadith.english
    if (text.isBlank()) return null
    val bengali = text.any { it in '\u0980'..'\u09FF' }
    return when {
        language == "ar" || language.isEmpty() -> HomeReadingText(text, "Arabic source")
        language == "bn" && bengali -> HomeReadingText(text, "Local translation")
        language == "en" && !bengali -> HomeReadingText(text, "Local English translation")
        else -> null
    }
}

internal fun homeHadithEnglishForTranslation(hadith: SharedHadith?): String? =
    hadith?.english?.takeIf { text -> text.isNotBlank() && text.none { it in '\u0980'..'\u09FF' } }

/** Translate the verified passage, keeping its source link and identity unchanged. */
@Composable
internal fun rememberHomeSuggestionTranslation(card: DeenlyKnowledgeCard?): HomeReadingText? {
    val store = remember { SharedContentStore() }
    val language = store.preferredContentLanguage()
    var reading by remember(card, language) { mutableStateOf<HomeReadingText?>(null) }
    LaunchedEffect(card, language) {
        card ?: return@LaunchedEffect
        val fallback = HomeReadingText(card.sourceText, "Selected translation unavailable · Showing source text")
        reading = HomeReadingText(card.sourceText, "Loading selected translation…")
        reading = try {
            if (card.target is DeenlyKnowledgeTarget.Surah) {
                val selected = if (language == "ar" || language.isEmpty()) QuranTranslationLanguage.None
                    else QuranTranslationLanguage.fromCode(language)
                val verses = createQuranVerseRepository().getVersesBySurah(card.target.number, selected)
                // The suggestion generator selects the opening three ayahs.
                val text = verses.take(3).mapNotNull { verse ->
                    (if (language == "ar" || language.isEmpty()) verse.arabicText else verse.translation)
                        .takeIf(String::isNotBlank)
                }.joinToString(" ")
                if (text.isBlank()) fallback else HomeReadingText(text,
                    if (language == "ar" || language.isEmpty()) "Arabic source" else "Local Quran translation")
            } else if (language == "en") {
                HomeReadingText(card.sourceText, "Local English translation")
            } else if (language == "ar" || language.isEmpty()) {
                val arabic = when (val target = card.target) {
                    is DeenlyKnowledgeTarget.BukhariHadith ->
                        createSharedHadithRepository().getHadith(target.hadithId)?.arabic
                    is DeenlyKnowledgeTarget.ShamayelBook -> ShamayelBooks.find(target.bookId)?.let { book ->
                        createSharedHadithRepository().getShamayelHadiths(book.firstHadithId, book.lastHadithId)
                            .firstOrNull { it.english == card.sourceText }?.arabic
                    }
                    is DeenlyKnowledgeTarget.FortressDua ->
                        createSharedFortressRepository().getChapterInvocations(target.chapterId).firstOrNull { dua ->
                            listOf(dua.translation, dua.context, dua.instruction, dua.note, dua.postContext, dua.description)
                                .filter(String::isNotBlank).joinToString(" ") == card.sourceText
                        }?.arabic
                    is DeenlyKnowledgeTarget.Surah -> null
                }
                if (arabic.isNullOrBlank()) fallback else HomeReadingText(arabic, "Arabic source")
            } else {
                val text = SharedTranslationService.translateFromEnglish(
                    card.sourceText, language, store.hadithTranslationProvider(),
                )
                if (text.isBlank() || text == card.sourceText) fallback
                else HomeReadingText(text, "Machine translation · May contain errors")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            fallback
        }
    }
    return reading
}
