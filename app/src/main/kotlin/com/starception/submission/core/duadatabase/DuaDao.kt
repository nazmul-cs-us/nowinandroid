package com.starception.submission.core.duadatabase

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Fortress of the Muslim database
 * Provides queries for chapters, invocations (duas), and footnotes
 *
 * Database: fortress_of_the_muslim.db
 * Tables: metadata, chapters, invocations, footnotes
 */
@Dao
interface DuaDao {

    // ============= Metadata Queries =============

    /**
     * Get book metadata (title, subtitle, publisher)
     */
    @Query("SELECT * FROM metadata WHERE id = 1")
    suspend fun getMetadata(): DuaMetadataEntity?

    // ============= Chapter Queries =============

    /**
     * Get all chapters
     */
    @Query("SELECT * FROM chapters ORDER BY id ASC")
    suspend fun getAllChapters(): List<DuaChapterEntity>

    /**
     * Get all chapters as Flow for reactive updates
     */
    @Query("SELECT * FROM chapters ORDER BY id ASC")
    fun getAllChaptersFlow(): Flow<List<DuaChapterEntity>>

    /**
     * Get chapter by ID
     */
    @Query("SELECT * FROM chapters WHERE id = :chapterId")
    suspend fun getChapterById(chapterId: Int): DuaChapterEntity?

    /**
     * Get all chapters with dua count
     */
    @Query("""
        SELECT c.id, c.title,
               (SELECT COUNT(*) FROM invocations i WHERE i.chapter_id = c.id) as duaCount
        FROM chapters c
        ORDER BY c.id ASC
    """)
    suspend fun getAllChaptersWithCount(): List<ChapterWithCount>

    /**
     * Get all chapters with dua count as Flow
     */
    @Query("""
        SELECT c.id, c.title,
               (SELECT COUNT(*) FROM invocations i WHERE i.chapter_id = c.id) as duaCount
        FROM chapters c
        ORDER BY c.id ASC
    """)
    fun getAllChaptersWithCountFlow(): Flow<List<ChapterWithCount>>

    /**
     * Search chapters by title
     */
    @Query("""
        SELECT c.id, c.title,
               (SELECT COUNT(*) FROM invocations i WHERE i.chapter_id = c.id) as duaCount
        FROM chapters c
        WHERE c.title LIKE '%' || :query || '%'
        ORDER BY c.id ASC
    """)
    suspend fun searchChapters(query: String): List<ChapterWithCount>

    /**
     * Get total chapter count
     */
    @Query("SELECT COUNT(*) FROM chapters")
    suspend fun getChapterCount(): Int

    // ============= Invocation (Dua) Queries =============

    /**
     * Get all invocations for a chapter
     */
    @Query("SELECT * FROM invocations WHERE chapter_id = :chapterId ORDER BY position ASC")
    suspend fun getInvocationsByChapter(chapterId: Int): List<DuaInvocationEntity>

    /**
     * Get all invocations for a chapter as Flow
     */
    @Query("SELECT * FROM invocations WHERE chapter_id = :chapterId ORDER BY position ASC")
    fun getInvocationsByChapterFlow(chapterId: Int): Flow<List<DuaInvocationEntity>>

    /**
     * Get invocation by ID
     */
    @Query("SELECT * FROM invocations WHERE id = :invocationId")
    suspend fun getInvocationById(invocationId: Int): DuaInvocationEntity?

    /**
     * Get all invocations (all duas across all chapters)
     */
    @Query("SELECT * FROM invocations ORDER BY chapter_id ASC, position ASC")
    suspend fun getAllInvocations(): List<DuaInvocationEntity>

    /**
     * Get total invocation count
     */
    @Query("SELECT COUNT(*) FROM invocations")
    suspend fun getInvocationCount(): Int

    /**
     * Get invocation count for a chapter
     */
    @Query("SELECT COUNT(*) FROM invocations WHERE chapter_id = :chapterId")
    suspend fun getInvocationCountByChapter(chapterId: Int): Int

    /**
     * Search invocations by Arabic text
     */
    @Query("""
        SELECT * FROM invocations
        WHERE arabic LIKE '%' || :query || '%'
        ORDER BY chapter_id ASC, position ASC
        LIMIT :limit
    """)
    suspend fun searchByArabic(query: String, limit: Int = 50): List<DuaInvocationEntity>

    /**
     * Search invocations by transliteration
     */
    @Query("""
        SELECT * FROM invocations
        WHERE transliteration LIKE '%' || :query || '%'
        ORDER BY chapter_id ASC, position ASC
        LIMIT :limit
    """)
    suspend fun searchByTransliteration(query: String, limit: Int = 50): List<DuaInvocationEntity>

    /**
     * Search invocations by translation (English)
     */
    @Query("""
        SELECT * FROM invocations
        WHERE translation LIKE '%' || :query || '%'
        ORDER BY chapter_id ASC, position ASC
        LIMIT :limit
    """)
    suspend fun searchByTranslation(query: String, limit: Int = 50): List<DuaInvocationEntity>

    /**
     * Search all fields (Arabic, transliteration, translation, context)
     */
    @Query("""
        SELECT * FROM invocations
        WHERE arabic LIKE '%' || :query || '%'
           OR transliteration LIKE '%' || :query || '%'
           OR translation LIKE '%' || :query || '%'
           OR context LIKE '%' || :query || '%'
           OR instruction LIKE '%' || :query || '%'
        ORDER BY chapter_id ASC, position ASC
        LIMIT :limit
    """)
    suspend fun searchAll(query: String, limit: Int = 100): List<DuaInvocationEntity>

    /**
     * Get invocations with chapter title
     */
    @Query("""
        SELECT i.id, i.chapter_id as chapterId, c.title as chapterTitle,
               i.position, i.arabic, i.transliteration, i.translation,
               i.context, i.instruction, i.note, i.post_context as postContext,
               i.description
        FROM invocations i
        INNER JOIN chapters c ON i.chapter_id = c.id
        WHERE i.chapter_id = :chapterId
        ORDER BY i.position ASC
    """)
    suspend fun getInvocationsWithChapterTitle(chapterId: Int): List<DuaWithChapterTitle>

    /**
     * Get random dua (for daily dua feature)
     */
    @Query("""
        SELECT i.id, i.chapter_id as chapterId, c.title as chapterTitle,
               i.position, i.arabic, i.transliteration, i.translation,
               i.context, i.instruction, i.note, i.post_context as postContext,
               i.description
        FROM invocations i
        INNER JOIN chapters c ON i.chapter_id = c.id
        WHERE i.arabic IS NOT NULL AND i.arabic != ''
        ORDER BY RANDOM()
        LIMIT 1
    """)
    suspend fun getRandomDua(): DuaWithChapterTitle?

    /**
     * Get duas by category keywords (for smart filtering)
     * Example: "morning", "evening", "sleep", "prayer"
     */
    @Query("""
        SELECT i.* FROM invocations i
        INNER JOIN chapters c ON i.chapter_id = c.id
        WHERE c.title LIKE '%' || :keyword || '%'
        ORDER BY i.chapter_id ASC, i.position ASC
    """)
    suspend fun getDuasByCategory(keyword: String): List<DuaInvocationEntity>

    // ============= Footnote Queries =============

    /**
     * Get all footnotes for a chapter
     */
    @Query("SELECT * FROM footnotes WHERE chapter_id = :chapterId")
    suspend fun getFootnotesByChapter(chapterId: Int): List<DuaFootnoteEntity>

    /**
     * Get footnote by ID
     */
    @Query("SELECT * FROM footnotes WHERE id = :footnoteId")
    suspend fun getFootnoteById(footnoteId: Int): DuaFootnoteEntity?

    /**
     * Get all footnotes
     */
    @Query("SELECT * FROM footnotes ORDER BY chapter_id ASC")
    suspend fun getAllFootnotes(): List<DuaFootnoteEntity>

    /**
     * Get total footnote count
     */
    @Query("SELECT COUNT(*) FROM footnotes")
    suspend fun getFootnoteCount(): Int

    /**
     * Search footnotes by term
     */
    @Query("""
        SELECT * FROM footnotes
        WHERE term LIKE '%' || :query || '%'
           OR definition LIKE '%' || :query || '%'
        ORDER BY chapter_id ASC
    """)
    suspend fun searchFootnotes(query: String): List<DuaFootnoteEntity>

    // ============= Combined Queries =============

    /**
     * Get chapter with all its invocations and footnotes
     */
    @Query("""
        SELECT c.id, c.title,
               (SELECT COUNT(*) FROM invocations i WHERE i.chapter_id = c.id) as duaCount
        FROM chapters c
        WHERE c.id = :chapterId
    """)
    suspend fun getChapterWithCount(chapterId: Int): ChapterWithCount?

    /**
     * Get next chapter
     */
    @Query("SELECT * FROM chapters WHERE id > :currentChapterId ORDER BY id ASC LIMIT 1")
    suspend fun getNextChapter(currentChapterId: Int): DuaChapterEntity?

    /**
     * Get previous chapter
     */
    @Query("SELECT * FROM chapters WHERE id < :currentChapterId ORDER BY id DESC LIMIT 1")
    suspend fun getPreviousChapter(currentChapterId: Int): DuaChapterEntity?

    // ============= Hadith Reference Queries =============

    /**
     * Get all hadith references for an invocation
     */
    @Query("SELECT * FROM hadith_references WHERE invocation_id = :invocationId ORDER BY id ASC")
    suspend fun getHadithReferencesForInvocation(invocationId: Int): List<HadithReferenceEntity>

    /**
     * Get all hadith references for an invocation as Flow
     */
    @Query("SELECT * FROM hadith_references WHERE invocation_id = :invocationId ORDER BY id ASC")
    fun getHadithReferencesForInvocationFlow(invocationId: Int): Flow<List<HadithReferenceEntity>>

    /**
     * Get hadith reference by ID
     */
    @Query("SELECT * FROM hadith_references WHERE id = :referenceId")
    suspend fun getHadithReferenceById(referenceId: Int): HadithReferenceEntity?

    /**
     * Get all hadith references
     */
    @Query("SELECT * FROM hadith_references ORDER BY collection_name ASC, hadith_number ASC")
    suspend fun getAllHadithReferences(): List<HadithReferenceEntity>

    /**
     * Get hadith references by collection name
     */
    @Query("SELECT * FROM hadith_references WHERE collection_name = :collectionName ORDER BY hadith_number ASC")
    suspend fun getHadithReferencesByCollection(collectionName: String): List<HadithReferenceEntity>

    /**
     * Get total hadith reference count
     */
    @Query("SELECT COUNT(*) FROM hadith_references")
    suspend fun getHadithReferenceCount(): Int

    /**
     * Get count of hadith references for an invocation
     */
    @Query("SELECT COUNT(*) FROM hadith_references WHERE invocation_id = :invocationId")
    suspend fun getHadithReferenceCountForInvocation(invocationId: Int): Int

    /**
     * Get unique collection names
     */
    @Query("SELECT DISTINCT collection_name FROM hadith_references WHERE collection_name IS NOT NULL ORDER BY collection_name ASC")
    suspend fun getUniqueCollectionNames(): List<String>

    /**
     * Find invocation by chapter title and position
     * This is used to map news_resources dua IDs to fortress_of_the_muslim invocation IDs
     * for hadith reference lookup
     */
    @Query("""
        SELECT i.* FROM invocations i
        INNER JOIN chapters c ON i.chapter_id = c.id
        WHERE LOWER(c.title) = LOWER(:chapterTitle) AND i.position = :position
        LIMIT 1
    """)
    suspend fun getInvocationByChapterAndPosition(chapterTitle: String, position: Int): DuaInvocationEntity?

    /**
     * Get hadith references by chapter title and position
     * This combines the lookup of invocation ID and hadith references in one query
     */
    @Query("""
        SELECT hr.* FROM hadith_references hr
        INNER JOIN invocations i ON hr.invocation_id = i.id
        INNER JOIN chapters c ON i.chapter_id = c.id
        WHERE LOWER(c.title) = LOWER(:chapterTitle) AND i.position = :position
        ORDER BY hr.id ASC
    """)
    suspend fun getHadithReferencesByChapterAndPosition(chapterTitle: String, position: Int): List<HadithReferenceEntity>
}

// ============= Data Classes for Query Results =============

/**
 * Chapter with dua count
 */
data class ChapterWithCount(
    val id: Int,
    val title: String,
    val duaCount: Int
)

/**
 * Dua with chapter title included
 */
data class DuaWithChapterTitle(
    val id: Int,
    val chapterId: Int,
    val chapterTitle: String,
    val position: Int,
    val arabic: String?,
    val transliteration: String?,
    val translation: String?,
    val context: String?,
    val instruction: String?,
    val note: String?,
    val postContext: String?,
    val description: String?
)

// Extension function
fun DuaWithChapterTitle.toDua() = Dua(
    id = id,
    chapterId = chapterId,
    chapterTitle = chapterTitle,
    position = position,
    arabic = arabic,
    transliteration = transliteration,
    translation = translation,
    context = context,
    instruction = instruction,
    note = note,
    postContext = postContext,
    description = description
)

fun ChapterWithCount.toDuaChapter() = DuaChapter(
    id = id,
    title = title,
    duaCount = duaCount
)
