package com.starception.submission.ui.search

import android.content.Context
import com.starception.submission.core.duadatabase.ChapterWithCount
import com.starception.submission.core.duadatabase.Dua
import com.starception.submission.core.duadatabase.DuaDao
import com.starception.submission.core.duadatabase.toDua
import com.starception.submission.core.qurandatabase.QuranDao
import com.starception.submission.core.qurandatabase.QuranTranslationHelper
import com.starception.submission.core.qurandatabase.SurahEntity
import com.starception.submission.core.quranicduas.QuranicDuaDatabase
import com.starception.submission.core.quranicduas.QuranicDuaEntity
import com.starception.submission.feature.search.SuggestedVerse
import com.starception.submission.feature.search.SuggestedVerses
import com.starception.submission.ui.AppTaskProgressBus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    // Needed to reach the same Quran database the reader uses: the helper resolves the
    // downloaded file through this, and without it refuses to open at all, since the
    // bundled asset is a zero-byte placeholder.
    private val assetRepository: com.starception.submission.download.AssetRepository,
) {
    private val quranicDuaDao by lazy {
        QuranicDuaDatabase.getInstance(appContext).quranicDuaDao()
    }

    private companion object {
        const val SOURCE_QURAN = "quran"
        const val SOURCE_FORTRESS = "fortress"

        /** Wording for the banner while sources are being read. */
        const val INDEXING_LABEL = "Preparing search — results may be incomplete"

        /**
         * The database the reader actually serves Arabic from — the translation-aware
         * instance, not the `quran.db` shell the injected DAO opens. Watched by file
         * stamp because it appears mid-session when its download completes.
         */
        const val QURAN_READER_DATABASE = "quran_ar_instance"
        const val FORTRESS_DATABASE = "fortress_of_the_muslim_v2.db"
    }

    private val readyMutex = Mutex()
    @Volatile private var surahIndex: FieldWeightedIndex<SurahEntity>? = null
    @Volatile private var quranicDuaIndex: FieldWeightedIndex<QuranicDuaEntity>? = null
    @Volatile private var verseIndex: FieldWeightedIndex<SuggestedVerse>? = null
    @Volatile private var chapterIndex: FieldWeightedIndex<ChapterWithCount>? = null
    @Volatile private var fortressDuaIndex: FieldWeightedIndex<Dua>? = null

    private val _status = MutableStateFlow(SearchIndexStatus())

    /**
     * What the app may tell the user about the index behind their results.
     *
     * Exposed so a first run — where the content databases are still downloading and an
     * index over them is necessarily partial — can say so, instead of silently returning
     * less than the app holds.
     */
    val status: StateFlow<SearchIndexStatus> = _status.asStateFlow()

    /** Fingerprints of each source's backing file, so an unchanged source is skipped. */
    private val sourceStamps = HashMap<String, String>()

    /**
     * Builds any source that is missing, empty, or whose database has changed on disk.
     *
     * A source is re-read when its file's size or modification time moves — which is what
     * a finished download looks like — and also whenever its index came out empty, since
     * that is the state a download is expected to resolve. Sources that are populated and
     * untouched cost one `File.length()` each.
     */
    private suspend fun ensureReady(force: Boolean = false) {
        readyMutex.withLock {
            val work = mutableListOf<suspend () -> Unit>()

            if (force || surahIndex.isEmptyIndex() || stampChanged(SOURCE_QURAN, quranDatabaseFile())) {
                work += ::buildSurahIndex
            }
            if (force || quranicDuaIndex.isEmptyIndex()) work += ::buildQuranicDuaIndex
            if (verseIndex == null) work += { buildVerseIndex() }
            if (force || chapterIndex.isEmptyIndex() || fortressDuaIndex.isEmptyIndex() ||
                stampChanged(SOURCE_FORTRESS, fortressDatabaseFile())
            ) {
                work += ::buildFortressIndices
            }

            if (work.isEmpty()) {
                publishStatus(building = false)
                return
            }

            publishStatus(building = true)
            try {
                withContext(Dispatchers.IO) {
                    val t0 = System.nanoTime()
                    work.forEachIndexed { index, build ->
                        // Shown in the pull-to-sync banner, so a first run — where every
                        // source is being read for the first time and results until it
                        // finishes are necessarily partial — says so rather than quietly
                        // returning less than the app holds.
                        AppTaskProgressBus.update(INDEXING_LABEL, index, work.size)
                        build()
                    }
                    android.util.Log.d(
                        "SearchPerf",
                        "ensureReady built ${work.size} source(s) in " +
                            "${(System.nanoTime() - t0) / 1_000_000}ms",
                    )
                }
            } finally {
                // Cleared from `finally` so a source that throws cannot leave the banner
                // up for the rest of the session.
                AppTaskProgressBus.clear()
                publishStatus(building = false)
            }
        }
    }

    private suspend fun buildSurahIndex() {
        val surahs = loadSurahs()
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
    }

    /**
     * Surah names from whichever database actually holds them.
     *
     * The injected DAO reads `quran.db`, which is bundled as a zero-byte placeholder and
     * filled from the CDN; until that lands, Room serves a schema-shaped shell with no
     * rows, and an index over it silently matches nothing. The reader gets its text from
     * the translation-aware instance instead, so that is asked first and the injected DAO
     * kept as the fallback.
     */
    private suspend fun loadSurahs(): List<SurahEntity> {
        val fromReader = runCatching {
            QuranTranslationHelper
                .getDatabase(appContext, "ar", assetRepository)
                .quranDao()
                .getAllSurahsOnce()
        }.getOrDefault(emptyList())
        if (fromReader.isNotEmpty()) return fromReader
        return runCatching { quranDao.getAllSurahsOnce() }.getOrDefault(emptyList())
    }

    private suspend fun buildQuranicDuaIndex() {
        val qDuas = runCatching { quranicDuaDao.getAllQuranicDuas() }.getOrDefault(emptyList())
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
    }

    /** Ships in the APK as Kotlin, so it is built once and never goes stale. */
    private fun buildVerseIndex() {
        verseIndex = FieldWeightedIndex(
            items = SuggestedVerses.verses,
            fields = listOf(
                IndexedField("name", weight = 10.0) { it.name },
                IndexedField("arabicName", weight = 8.0) { it.arabicName },
                IndexedField("description", weight = 6.0) { it.description },
                IndexedField("category", weight = 7.0) { it.category },
                IndexedField("ref", weight = 5.0) { "${it.surahNumber}:${it.ayahNumber}" },
            ),
        )
    }

    private suspend fun buildFortressIndices() {
        val chapters = runCatching { duaDao.getAllChaptersWithCount() }.getOrDefault(emptyList())
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
            .map { invocation -> invocation.toDua(chapterTitles[invocation.chapterId].orEmpty()) }
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
    }

    private fun FieldWeightedIndex<*>?.isEmptyIndex(): Boolean = this == null || this.isEmpty()

    private fun quranDatabaseFile(): java.io.File =
        appContext.getDatabasePath(QURAN_READER_DATABASE)

    private fun fortressDatabaseFile(): java.io.File =
        appContext.getDatabasePath(FORTRESS_DATABASE)

    /** True when the file's fingerprint differs from the one the index was built on. */
    private fun stampChanged(source: String, file: java.io.File): Boolean {
        val stamp = if (file.exists()) "${file.length()}:${file.lastModified()}" else "absent"
        val changed = sourceStamps[source] != stamp
        if (changed) sourceStamps[source] = stamp
        return changed
    }

    private fun publishStatus(building: Boolean) {
        _status.value = SearchIndexStatus(
            building = building,
            surahsIndexed = surahIndex?.size ?: 0,
            fortressDuasIndexed = fortressDuaIndex?.size ?: 0,
            quranicDuasIndexed = quranicDuaIndex?.size ?: 0,
        )
    }

    /**
     * Re-checks every source for data that has arrived since the last build.
     *
     * Call on app foreground: content databases are downloaded in the background, so the
     * moment the user returns is exactly when the index is most likely to be out of date.
     */
    suspend fun refresh() = ensureReady()

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

/**
 * What the in-memory indices currently hold, for surfaces that want to be honest about
 * partial results.
 *
 * [complete] is the one a caller should gate a warning on: it is false both while a build
 * is running and when a build finished over a database that had not arrived yet, which
 * from the reader's point of view are the same situation — the app knows more than the
 * search can find.
 */
data class SearchIndexStatus(
    val building: Boolean = false,
    val surahsIndexed: Int = 0,
    val fortressDuasIndexed: Int = 0,
    val quranicDuasIndexed: Int = 0,
) {
    /** The Qur'an has 114 surahs; anything less means the source was short. */
    val complete: Boolean
        get() = !building && surahsIndexed >= EXPECTED_SURAH_COUNT && fortressDuasIndexed > 0

    private companion object {
        const val EXPECTED_SURAH_COUNT = 114
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
