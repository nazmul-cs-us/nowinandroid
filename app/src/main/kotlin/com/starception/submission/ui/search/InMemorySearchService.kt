package com.starception.submission.ui.search

import android.content.Context
import com.starception.submission.core.duadatabase.ChapterWithCount
import com.starception.submission.core.duadatabase.Dua
import com.starception.submission.core.duadatabase.DuaDao
import com.starception.submission.core.duadatabase.toDua
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
 * (surahs, quranic duas, Fortress chapters and invocations, popular verses).
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
    @Volatile private var fortressDuaIndex: FieldWeightedIndex<Dua>? = null

    private suspend fun ensureReady() {
        if (surahIndex != null) return
        readyMutex.withLock {
            if (surahIndex != null) return
            withContext(Dispatchers.IO) {
                val t0 = System.nanoTime()
                val surahs = runCatching { quranDao.getAllSurahsOnce() }
                    .getOrDefault(emptyList())
                val tSurahsLoaded = System.nanoTime()
                surahIndex = FieldWeightedIndex(
                    items = surahs,
                    fields = listOf(
                        IndexedField("nameEn", weight = 10.0) { it.nameEnglish },
                        IndexedField("aliases", weight = 9.5) {
                            SURAH_SEARCH_ALIASES[it.number].orEmpty()
                        },
                        IndexedField("nameTr", weight = 8.0) { it.nameTranslation },
                        IndexedField("nameAr", weight = 8.0) { it.nameArabic },
                        IndexedField("number", weight = 6.0) { it.number.toString() },
                    ),
                )
                val tSurahIdx = System.nanoTime()

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

                val tQDuasIdx = System.nanoTime()
                val tVerseIdx = System.nanoTime()

                val chapters = runCatching { duaDao.getAllChaptersWithCount() }
                    .getOrDefault(emptyList())
                chapterIndex = FieldWeightedIndex(
                    items = chapters,
                    fields = listOf(
                        IndexedField("title", weight = 10.0) { it.title },
                        IndexedField("aliases", weight = 9.5) {
                            FORTRESS_CHAPTER_SEARCH_ALIASES[it.id].orEmpty()
                        },
                    ),
                )

                // Fortress is small enough to keep a ranked index in memory. Include the
                // chapter title on every invocation so a typo such as "anxity" can still
                // surface the duas from "Distress & Anxiety", while exact SQL matches
                // remain available as a complementary content-search path.
                val chapterTitles = chapters.associate { it.id to it.title }
                val fortressDuas = runCatching { duaDao.getAllInvocations() }
                    .getOrDefault(emptyList())
                    .map { invocation ->
                        invocation.toDua(chapterTitles[invocation.chapterId].orEmpty())
                    }
                fortressDuaIndex = FieldWeightedIndex(
                    items = fortressDuas,
                    fields = listOf(
                        IndexedField("chapter", weight = 10.0) { it.chapterTitle },
                        IndexedField("chapterAliases", weight = 9.5) {
                            FORTRESS_CHAPTER_SEARCH_ALIASES[it.chapterId].orEmpty()
                        },
                        IndexedField("transliteration", weight = 8.0) { it.transliteration },
                        IndexedField("translation", weight = 7.0) { it.translation },
                        IndexedField("context", weight = 6.0) { it.context },
                        IndexedField("instruction", weight = 5.0) { it.instruction },
                        IndexedField("description", weight = 4.0) { it.description },
                        IndexedField("arabic", weight = 6.0) { it.arabic },
                    ),
                )
                val tDone = System.nanoTime()
                fun ms(start: Long, end: Long) = (end - start) / 1_000_000
                android.util.Log.d(
                    "SearchPerf",
                    "ensureReady built indices total=${ms(t0, tDone)}ms " +
                        "(surahs:load=${ms(t0, tSurahsLoaded)} idx=${ms(tSurahsLoaded, tSurahIdx)}, " +
                        "qduas+verses=${ms(tSurahIdx, tVerseIdx)}, chapters=${ms(tVerseIdx, tDone)})",
                )
            }
        }
    }

    /** Pre-warm indices on app start so the first user keystroke isn't waiting on disk. */
    suspend fun preload() = ensureReady()

    /**
     * Run the tokenized, ranked search against every in-memory source. Each
     * source returns at most [limitPerSource] items, sorted by score desc.
     */
    suspend fun search(query: String, limitPerSource: Int = 5): InMemorySearchResult {
        val tReady = System.nanoTime()
        ensureReady()
        val tReadyDone = System.nanoTime()
        val tokens = SearchTokenizer.tokenize(query)
        if (tokens.isEmpty()) return InMemorySearchResult()
        val fullNorm = SearchTokenizer.normalize(query.trim())
        val tQ = System.nanoTime()
        val result = InMemorySearchResult(
            surahs = surahIndex?.query(tokens, fullNorm, limitPerSource).orEmpty(),
            quranicDuas = quranicDuaIndex?.query(tokens, fullNorm, limitPerSource).orEmpty(),
            verses = verseIndex?.query(tokens, fullNorm, limitPerSource).orEmpty(),
            chapters = chapterIndex?.query(tokens, fullNorm, limitPerSource).orEmpty(),
            fortressDuas = fortressDuaIndex?.query(
                tokens,
                fullNorm,
                limitPerSource,
            ).orEmpty(),
        )
        val tDone = System.nanoTime()
        android.util.Log.d(
            "SearchPerf",
            "search('$query') readyWait=${(tReadyDone - tReady) / 1_000_000}ms " +
                "queries=${(tDone - tQ) / 1_000_000}ms total=${(tDone - tReady) / 1_000_000}ms " +
                "[s=${result.surahs.size} qd=${result.quranicDuas.size} " +
                    "v=${result.verses.size} c=${result.chapters.size} " +
                    "fd=${result.fortressDuas.size}]",
        )
        return result
    }
}

/** Ranked, capped results for each in-memory source. */
data class InMemorySearchResult(
    val surahs: List<RankedHit<SurahEntity>> = emptyList(),
    val quranicDuas: List<RankedHit<QuranicDuaEntity>> = emptyList(),
    val verses: List<RankedHit<SuggestedVerse>> = emptyList(),
    val chapters: List<RankedHit<ChapterWithCount>> = emptyList(),
    val fortressDuas: List<RankedHit<Dua>> = emptyList(),
)

/**
 * Carefully scoped conventional spellings that cannot always be inferred from
 * character distance alone. They are indexed like names, so exact canonical
 * names still receive the strongest field weight.
 */
private val SURAH_SEARCH_ALIASES = mapOf(
    1 to "fatiha fateha fatihah",
    2 to "baqara baqarah bakara bakarah",
    3 to "imran imraan",
    17 to "isra bani israel",
    18 to "kahf kehf",
    19 to "maryam mariam",
    20 to "taha ta-ha ta ha",
    36 to "yasin yaseen ya-sin ya sin",
    55 to "rahman rehman ar-rahman",
    56 to "waqiah waqia waqiyah",
    67 to "mulk al-mulk",
    93 to "duha dhuha zoha",
    94 to "sharh inshirah",
    106 to "quraysh quraish kuraish koreish",
    107 to "maun maoon al-maun",
    108 to "kawthar kauthar kausar",
    112 to "ikhlas iklas",
    113 to "falaq falak",
    114 to "nas naas",
)

/** Common terms and alternate spellings that differ from the bundled Fortress titles. */
private val FORTRESS_CHAPTER_SEARCH_ALIASES = mapOf(
    15 to "adhan athan azan call to prayer",
    17 to "ruku rukoo bowing",
    19 to "sujud sujood sajda sajdah prostration",
    22 to "tashahhud attahiyat at-tahiyyat",
    26 to "istikhara istikhaara guidance decision",
    27 to "adhkar azkar morning evening remembrance",
    28 to "sleep bedtime night",
    31 to "bad dream nightmare",
    34 to "anxiety anxious distress depression sadness sorrow",
    35 to "distress hardship difficulty anguish",
    41 to "debt loan repayment",
    42 to "waswasa distraction satan shaytan prayer",
    45 to "satan shaytan devil waswasa",
)
