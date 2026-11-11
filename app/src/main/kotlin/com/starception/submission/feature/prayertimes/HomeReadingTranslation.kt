package com.starception.submission.feature.prayertimes

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.starception.submission.core.hadithdatabase.BukhariLocalTranslationRepository
import com.starception.submission.core.hadithdatabase.HadithRepository
import com.starception.submission.core.model.deenly.PrayerHadithCandidate
import com.starception.submission.core.translation.TranslationService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class HomeReadingText(val text: String, val notice: String)

@Composable
internal fun rememberReadingLanguage(): String {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("quran_prefs", Context.MODE_PRIVATE) }
    var code by remember(prefs) { mutableStateOf(prefs.getString("quran_translation", "en") ?: "en") }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "quran_translation") code = prefs.getString(key, "en") ?: "en"
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return code
}

@Composable
internal fun rememberHomeHadithTranslation(source: PrayerHadithCandidate?): HomeReadingText? {
    val context = LocalContext.current
    val language = rememberReadingLanguage()
    var result by remember(source, language) { mutableStateOf<HomeReadingText?>(null) }
    LaunchedEffect(source, language) {
        source ?: return@LaunchedEffect
        result = HomeReadingText(source.text, "Loading selected translation…")
        result = try {
            loadHomeHadithTranslation(context, source, language)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            HomeReadingText(source.text, "Selected translation unavailable · Showing source text")
        }
    }
    return result
}

@Composable
internal fun rememberHomeSuggestionTranslation(candidate: NowNudgeKnowledgeCandidate?): HomeReadingText? {
    val context = LocalContext.current
    val language = rememberReadingLanguage()
    var result by remember(candidate, language) { mutableStateOf<HomeReadingText?>(null) }
    LaunchedEffect(candidate, language) {
        candidate ?: return@LaunchedEffect
        val original = candidate.nudge.supportingText?.takeIf(String::isNotBlank) ?: candidate.nudge.label
        result = HomeReadingText(original, "Loading selected translation…")
        result = try {
            when (val target = candidate.target) {
                is NowNudgeKnowledgeTarget.Hadith -> loadHomeHadithTranslation(context,
                    PrayerHadithCandidate(target.databaseFile, target.collectionName, target.hadithNumber, original), language)
                is NowNudgeKnowledgeTarget.QuranAyah -> {
                    val text = withContext(Dispatchers.IO) {
                        com.starception.submission.core.qurandatabase.QuranTranslationRepository(context, language.ifEmpty { "ar" })
                            .getAyahsBySurahNumber(target.surahNumber)
                            .firstOrNull { it.numberInSurah == target.ayahNumber }?.text
                    }
                    if (text.isNullOrBlank()) HomeReadingText(original, "Selected translation unavailable · Showing source text")
                    else HomeReadingText(text, if (language == "ar" || language.isEmpty()) "Arabic source" else "Local Quran translation")
                }
                is NowNudgeKnowledgeTarget.FortressDua -> {
                    val dua = target.dua
                    when (language) {
                        "ar", "" -> HomeReadingText(dua.arabic?.takeIf(String::isNotBlank) ?: original, "Arabic source")
                        "en" -> HomeReadingText(dua.translation?.takeIf(String::isNotBlank) ?: original, "Local English translation")
                        else -> {
                            val english = dua.translation?.takeIf(String::isNotBlank) ?: original
                            val translated = TranslationService.getInstance(context).translateFromEnglish(english, language)
                            if (translated != english && translated.isNotBlank()) HomeReadingText(translated, "Machine translation · May contain errors")
                            else HomeReadingText(original, "Selected translation unavailable · Showing source text")
                        }
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            HomeReadingText(original, "Selected translation unavailable · Showing source text")
        }
    }
    return result
}

internal suspend fun loadHomeHadithTranslation(
    context: Context,
    source: PrayerHadithCandidate,
    language: String,
): HomeReadingText {
    val hadith = HadithRepository(context).getHadith(source.databaseFile, source.hadithNumber)
    val local = when (language) {
        "ar", "" -> hadith?.textArabic
        "bn" -> hadith?.bengaliText ?: hadith?.textPlain?.takeIf { it.any { char -> char in '\u0980'..'\u09FF' } }
        "en" -> hadith?.englishText ?: if (source.databaseFile.contains("bukhari")) {
            BukhariLocalTranslationRepository.getInstance(context).let { repo ->
                repo.loadTranslations()
                repo.getEnglishText(source.hadithNumber)
            }
        } else null
        else -> null
    }?.takeIf(String::isNotBlank)
    if (local != null) return HomeReadingText(local, if (language == "ar" || language.isEmpty()) "Arabic source" else "Local translation")
    if (language in listOf("", "ar", "transliteration")) {
        return HomeReadingText(source.text, "Selected translation unavailable · Showing source text")
    }
    val original = hadith?.textArabic?.takeIf(String::isNotBlank) ?: source.text
    val translated = TranslationService.getInstance(context).translate(original, language)
    return if (translated.isNotBlank() && translated != original) {
        HomeReadingText(translated, "Machine translation · May contain errors")
    } else {
        HomeReadingText(source.text, "Selected translation unavailable · Showing source text")
    }
}
