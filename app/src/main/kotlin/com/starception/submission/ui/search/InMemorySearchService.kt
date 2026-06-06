package com.starception.submission.ui.search

import android.content.Context
import com.starception.submission.core.duadatabase.ChapterWithCount
import com.starception.submission.core.duadatabase.DuaDao
import com.starception.submission.core.qurandatabase.QuranDao
import com.starception.submission.core.qurandatabase.SurahEntity
import com.starception.submission.core.quranicduas.QuranicDuaDatabase
import com.starception.submission.core.quranicduas.QuranicDuaEntity
import com.starception.submission.feature.search.SuggestedVerse
import com.starception.submission.feature.search.SuggestedVerses
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Holds in-memory ranked indices for the small, name-keyed search sources
 * (surahs, quranic duas, fortress chapter titles, popular verses).
 *
 * Indices are built once on first call to [search] and kept for the app lifetime.
 * A single 1–3-token query against all four indices returns in <5ms on a Pixel 6,
 * so the ViewModel can render in-memory hits *before* the SQL-backed sources
 * (ayahs, fortress invocations, topics/news FTS) catch up.
 */
@Singleton
class InMemorySearchService @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val quranDao: QuranDao,
    private val duaDao: DuaDao,
) {
    private val quranicDuaDao by lazy {
        QuranicDuaDatabase.getInstance(appContext).quranicDuaDao()
    }

    private val readyMutex = Mutex()
    @Volatile private var surahIndex: FieldWeightedIndex<SurahEntity>? = null
    @Volatile private var quranicDuaIndex: FieldWeightedIndex<QuranicDuaEntity>? = null
    @Volatile private var verseIndex: FieldWeightedIndex<SuggestedVerse>? = null
    @Volatile private var chapterIndex: FieldWeightedIndex<ChapterWithCount>? = null

    private suspend fun ensureReady() {
        if (surahIndex != null) return
        readyMutex.withLock {
            if (surahIndex != null) return
            withContext(Dispatchers.IO) {
                val surahs = runCatching { quranDao.getAllSurahsOnce() }
                    .getOrDefault(emptyList())
                surahIndex = FieldWeightedIndex(
                    items = surahs,
                    fields = listOf(
                        IndexedField("nameEn", weight = 10.0) { it.nameEnglish },
                        IndexedField("nameTr", weight = 8.0) { it.nameTranslation },
                        IndexedField("nameAr", weight = 8.0) { it.nameArabic },
                        IndexedField("number", weight = 6.0) { it.number.toString() },
                    ),
                )

                val qDuas = runCatching { quranicDuaDao.getAllQuranicDuas() }
                    .getOrDefault(emptyList())
                quranicDuaIndex = FieldWeightedIndex(
                    items = qDuas,
                    fields = listOf(
                        IndexedField("title", weight = 10.0) { it.title },
                        IndexedField("translation", weight = 6.0) { it.translation },
                        IndexedField("arabic", weight = 6.0) { it.arabic },
                        IndexedField("transliteration", weight = 5.0) { it.transliteration },
                        IndexedField("explanation", weight = 4.0) { it.explanation },
                        IndexedField("surahRef", weight = 5.0) { it.surahReference },
                    ),
                )

                verseIndex = FieldWeightedIndex(
                    items = SuggestedVerses.verses,
                    fields = listOf(
                        IndexedField("name", weight = 10.0) { it.name },
                        IndexedField("arabicName", weight = 8.0) { it.arabicName },
                        IndexedField("description", weight = 6.0) { it.description },
                        IndexedField("category", weight = 7.0) { it.category },
                        IndexedField("ref", weight = 5.0) {
                            "${it.surahNumber}:${it.ayahNumber}"
                        },
                    ),
                )

                val chapters = runCatching { duaDao.getAllChaptersWithCount() }
                    .getOrDefault(emptyList())
                chapterIndex = FieldWeightedIndex(
                    items = chapters,
                    fields = listOf(
                        IndexedField("title", weight = 10.0) { it.title },
                    ),
                )
            }
        }
    }

    /**
     * Run the tokenized, ranked search against every in-memory source. Each
     * source returns at most [limitPerSource] items, sorted by score desc.
     */
    suspend fun search(query: String, limitPerSource: Int = 5): InMemorySearchResult {
        ensureReady()
        val tokens = SearchTokenizer.tokenize(query)
        if (tokens.isEmpty()) return InMemorySearchResult()
        val fullNorm = SearchTokenizer.normalize(query.trim())
        return InMemorySearchResult(
            surahs = surahIndex?.query(tokens, fullNorm, limitPerSource).orEmpty(),
            quranicDuas = quranicDuaIndex?.query(tokens, fullNorm, limitPerSource).orEmpty(),
            verses = verseIndex?.query(tokens, fullNorm, limitPerSource).orEmpty(),
            chapters = chapterIndex?.query(tokens, fullNorm, limitPerSource).orEmpty(),
        )
    }
}

/** Ranked, capped results for each in-memory source. */
data class InMemorySearchResult(
    val surahs: List<RankedHit<SurahEntity>> = emptyList(),
    val quranicDuas: List<RankedHit<QuranicDuaEntity>> = emptyList(),
    val verses: List<RankedHit<SuggestedVerse>> = emptyList(),
    val chapters: List<RankedHit<ChapterWithCount>> = emptyList(),
)
