package com.starception.submission.core.duadatabase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for Fortress of the Muslim duas
 * Provides a clean API for accessing dua data
 */
@Singleton
class DuaRepository @Inject constructor(
    private val duaDao: DuaDao
) {
    // ============= Metadata =============

    /**
     * Get book metadata
     */
    suspend fun getBookMetadata(): DuaBookMetadata? {
        return duaDao.getMetadata()?.toDuaBookMetadata()
    }

    // ============= Chapters =============

    /**
     * Get all chapters with dua count
     */
    suspend fun getAllChapters(): List<DuaChapter> {
        return duaDao.getAllChaptersWithCount().map { it.toDuaChapter() }
    }

    /**
     * Get all chapters as Flow
     */
    fun getAllChaptersFlow(): Flow<List<DuaChapter>> {
        return duaDao.getAllChaptersWithCountFlow().map { chapters ->
            chapters.map { it.toDuaChapter() }
        }
    }

    /**
     * Get chapter by ID
     */
    suspend fun getChapterById(chapterId: Int): DuaChapter? {
        return duaDao.getChapterWithCount(chapterId)?.toDuaChapter()
    }

    /**
     * Search chapters by title
     */
    suspend fun searchChapters(query: String): List<DuaChapter> {
        return duaDao.searchChapters(query).map { it.toDuaChapter() }
    }

    /**
     * Get total chapter count
     */
    suspend fun getChapterCount(): Int {
        return duaDao.getChapterCount()
    }

    /**
     * Get chapters by category/topic
     */
    suspend fun getChaptersByCategory(category: DuaCategory): List<DuaChapter> {
        val chapters = duaDao.getAllChaptersWithCount()
        return chapters.filter { chapter ->
            category.keywords.any { keyword ->
                chapter.title.lowercase().contains(keyword.lowercase())
            }
        }.map { it.toDuaChapter() }
    }

    // ============= Duas =============

    /**
     * Get all duas for a chapter
     */
    suspend fun getDuasByChapter(chapterId: Int): List<Dua> {
        return duaDao.getInvocationsWithChapterTitle(chapterId).map { it.toDua() }
    }

    /**
     * Get all duas for a chapter as Flow
     */
    fun getDuasByChapterFlow(chapterId: Int): Flow<List<DuaBasic>> {
        return duaDao.getInvocationsByChapterFlow(chapterId).map { list ->
            list.map { it.toDuaBasic() }
        }
    }

    /**
     * Get a single dua by ID
     */
    suspend fun getDuaById(duaId: Int): Dua? {
        val entity = duaDao.getInvocationById(duaId) ?: return null
        val chapter = duaDao.getChapterById(entity.chapterId)
        return entity.toDua(chapter?.title ?: "")
    }

    /**
     * Get random dua (for daily dua feature)
     */
    suspend fun getRandomDua(): Dua? {
        return duaDao.getRandomDua()?.toDua()
    }

    /**
     * Get total dua count
     */
    suspend fun getDuaCount(): Int {
        return duaDao.getInvocationCount()
    }

    // ============= Search =============

    /**
     * Search duas in all fields
     */
    suspend fun searchDuas(query: String, limit: Int = 100): List<Dua> {
        val results = duaDao.searchAll(query, limit)
        return results.map { entity ->
            val chapter = duaDao.getChapterById(entity.chapterId)
            entity.toDua(chapter?.title ?: "")
        }
    }

    /**
     * Search duas where every supplied token must hit somewhere on the row
     * (including the chapter title via JOIN). Pass empty strings for unused slots.
     */
    suspend fun searchDuasMultiToken(
        tokens: List<String>,
        limit: Int = 30,
    ): List<Dua> {
        if (tokens.isEmpty()) return emptyList()
        val t0 = tokens[0]
        val t1 = tokens.getOrElse(1) { "" }
        val t2 = tokens.getOrElse(2) { "" }
        val results = duaDao.searchAllMultiToken(t0, t1, t2, limit)
        // Batch chapter lookups to avoid N+1 round trips
        val chapterIds = results.map { it.chapterId }.toSet()
        val chapterTitles = chapterIds.associateWith { id ->
            duaDao.getChapterById(id)?.title ?: ""
        }
        return results.map { entity ->
            entity.toDua(chapterTitles[entity.chapterId] ?: "")
        }
    }

    /**
     * Search duas by Arabic text
     */
    suspend fun searchByArabic(query: String): List<Dua> {
        val results = duaDao.searchByArabic(query)
        return results.map { entity ->
            val chapter = duaDao.getChapterById(entity.chapterId)
            entity.toDua(chapter?.title ?: "")
        }
    }

    /**
     * Search duas by translation
     */
    suspend fun searchByTranslation(query: String): List<Dua> {
        val results = duaDao.searchByTranslation(query)
        return results.map { entity ->
            val chapter = duaDao.getChapterById(entity.chapterId)
            entity.toDua(chapter?.title ?: "")
        }
    }

    // ============= Footnotes =============

    /**
     * Get footnotes for a chapter
     */
    suspend fun getFootnotesByChapter(chapterId: Int): List<DuaFootnote> {
        return duaDao.getFootnotesByChapter(chapterId).map { it.toDuaFootnote() }
    }

    /**
     * Get all footnotes
     */
    suspend fun getAllFootnotes(): List<DuaFootnote> {
        return duaDao.getAllFootnotes().map { it.toDuaFootnote() }
    }

    // ============= Full Chapter Data =============

    /**
     * Get chapter with all its duas and footnotes
     */
    suspend fun getChapterWithDuas(chapterId: Int): ChapterWithDuas? {
        val chapterWithCount = duaDao.getChapterWithCount(chapterId) ?: return null
        val chapter = chapterWithCount.toDuaChapter()
        val invocations = duaDao.getInvocationsWithChapterTitle(chapterId).map { it.toDua() }
        val footnotes = duaDao.getFootnotesByChapter(chapterId).map { it.toDuaFootnote() }

        return ChapterWithDuas(
            chapter = chapter,
            invocations = invocations,
            footnotes = footnotes
        )
    }

    // ============= Navigation =============

    /**
     * Get next chapter
     */
    suspend fun getNextChapter(currentChapterId: Int): DuaChapter? {
        val entity = duaDao.getNextChapter(currentChapterId) ?: return null
        val count = duaDao.getInvocationCountByChapter(entity.id)
        return entity.toDuaChapter(count)
    }

    /**
     * Get previous chapter
     */
    suspend fun getPreviousChapter(currentChapterId: Int): DuaChapter? {
        val entity = duaDao.getPreviousChapter(currentChapterId) ?: return null
        val count = duaDao.getInvocationCountByChapter(entity.id)
        return entity.toDuaChapter(count)
    }

    // ============= Category-Based Access =============

    /**
     * Get duas by category keywords
     */
    suspend fun getDuasByKeyword(keyword: String): List<Dua> {
        val results = duaDao.getDuasByCategory(keyword)
        return results.map { entity ->
            val chapter = duaDao.getChapterById(entity.chapterId)
            entity.toDua(chapter?.title ?: "")
        }
    }

    /**
     * Get all available categories with chapter counts
     */
    suspend fun getCategoriesWithCounts(): List<DuaCategoryWithCount> {
        val allChapters = duaDao.getAllChaptersWithCount()
        return DuaCategory.entries.map { category ->
            val matchingChapters = allChapters.filter { chapter ->
                category.keywords.any { keyword ->
                    chapter.title.lowercase().contains(keyword.lowercase())
                }
            }
            DuaCategoryWithCount(
                category = category,
                chapterCount = matchingChapters.size,
                totalDuaCount = matchingChapters.sumOf { it.duaCount }
            )
        }.filter { it.chapterCount > 0 }
    }
}

/**
 * Dua categories for organizing occasions
 * Covers all 129 chapters from Fortress of the Muslim
 */
enum class DuaCategory(
    val displayName: String,
    val arabicName: String,
    val keywords: List<String>,
    val icon: String
) {
    MORNING_EVENING(
        displayName = "Morning & Evening",
        arabicName = "أذكار الصباح والمساء",
        keywords = listOf(
            "morning", "evening", "waking", "sleep", "sleeping", "night",
            "dream", "turning over", "unrest", "retiring"
        ),
        icon = "🌅"
    ),
    PRAYER(
        displayName = "Prayer",
        arabicName = "الصلاة",
        keywords = listOf(
            "prayer", "salah", "mosque", "ablution", "athan", "salam",
            "prostrat", "bowing", "rukoo", "sujood", "tashahhud", "witr",
            "qunoot", "recitation", "quran"
        ),
        icon = "🕌"
    ),
    HOME(
        displayName = "Home & Daily",
        arabicName = "المنزل واليومية",
        keywords = listOf(
            "home", "entering", "leaving", "garment", "toilet", "undressing",
            "dressing", "wearing", "new garment"
        ),
        icon = "🏠"
    ),
    FOOD_DRINK(
        displayName = "Food & Drink",
        arabicName = "الطعام والشراب",
        keywords = listOf(
            "eating", "food", "drink", "meal", "fast", "fasting", "break",
            "fruit", "host", "completing the meal"
        ),
        icon = "🍽️"
    ),
    TRAVEL(
        displayName = "Travel",
        arabicName = "السفر",
        keywords = listOf(
            "travel", "journey", "transport", "vehicle", "mounting", "town",
            "village", "market", "ascending", "descending", "returning",
            "lodging", "resident", "traveller", "stumbles", "dawn approaches"
        ),
        icon = "✈️"
    ),
    PROTECTION(
        displayName = "Protection",
        arabicName = "الحماية",
        keywords = listOf(
            "refuge", "protection", "evil", "fear", "devil", "shaytan",
            "dajjal", "whispering", "eye", "omens", "obstinate", "ward off",
            "children", "startled", "enemy", "authority", "people"
        ),
        icon = "🛡️"
    ),
    DISTRESS(
        displayName = "Distress & Anxiety",
        arabicName = "الكرب والقلق",
        keywords = listOf(
            "distress", "anxiety", "sorrow", "difficult", "afflict", "debt",
            "mishap", "overtaken", "trial", "tribulation", "angry", "insulted"
        ),
        icon = "💔"
    ),
    HEALTH(
        displayName = "Health & Sickness",
        arabicName = "الصحة والمرض",
        keywords = listOf(
            "sick", "pain", "health", "visiting", "body", "hope of life",
            "nearing death"
        ),
        icon = "🏥"
    ),
    SOCIAL(
        displayName = "Social & Etiquette",
        arabicName = "الاجتماعية والآداب",
        keywords = listOf(
            "guest", "newlywed", "marriage", "sneezing", "favour", "praise",
            "greeting", "gift", "love", "sitting", "gathering", "kafir",
            "etiquette", "wealth", "debtor", "offer", "muslim", "news",
            "pleasing", "displeasing", "pleasant", "amazement", "delight"
        ),
        icon = "👥"
    ),
    DEATH(
        displayName = "Death & Funeral",
        arabicName = "الموت والجنازة",
        keywords = listOf(
            "death", "deceased", "funeral", "grave", "calamity", "condolence",
            "burying", "closing eyes", "nearing death", "advancement of reward"
        ),
        icon = "⚰️"
    ),
    WEATHER(
        displayName = "Weather & Nature",
        arabicName = "الطقس والطبيعة",
        keywords = listOf(
            "rain", "wind", "thunder", "storm", "skies", "rainfall",
            "crescent", "moon", "rooster", "dogs", "barking"
        ),
        icon = "🌧️"
    ),
    HAJJ(
        displayName = "Hajj & Umrah",
        arabicName = "الحج والعمرة",
        keywords = listOf(
            "hajj", "safa", "marwah", "talbiyah", "jamarat", "arafah",
            "black stone", "yemeni", "sacred site", "pebble", "mashaar"
        ),
        icon = "🕋"
    ),
    FORGIVENESS(
        displayName = "Forgiveness & Repentance",
        arabicName = "الاستغفار والتوبة",
        keywords = listOf(
            "forgiveness", "repent", "repentance", "sin", "expiation",
            "seeking forgiveness", "shirk", "committing"
        ),
        icon = "🤲"
    ),
    GUIDANCE(
        displayName = "Guidance & Faith",
        arabicName = "الهداية والإيمان",
        keywords = listOf(
            "guidance", "istikharah", "doubt", "faith", "seeking guidance"
        ),
        icon = "✨"
    ),
    REMEMBRANCE(
        displayName = "Remembrance & Dhikr",
        arabicName = "الذكر والأذكار",
        keywords = listOf(
            "remembrance", "glorification", "tasbeeh", "excellence",
            "prophet", "prayers upon"
        ),
        icon = "📿"
    ),
    FAMILY(
        displayName = "Family & Marriage",
        arabicName = "الأسرة والزواج",
        keywords = listOf(
            "groom", "wedding", "sexual", "intercourse", "children",
            "newlywed", "marriage"
        ),
        icon = "👨‍👩‍👧‍👦"
    ),
    SACRIFICE(
        displayName = "Sacrifice & Worship",
        arabicName = "الذبيحة والعبادة",
        keywords = listOf(
            "slaughtering", "sacrifice", "offering"
        ),
        icon = "🐑"
    )
}

/**
 * Category with counts for UI display
 */
data class DuaCategoryWithCount(
    val category: DuaCategory,
    val chapterCount: Int,
    val totalDuaCount: Int
)
