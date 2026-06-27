package com.starception.submission.core.duadatabase

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Metadata entity for Fortress of the Muslim book information
 */
@Entity(tableName = "metadata")
data class DuaMetadataEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "subtitle")
    val subtitle: String?,

    @ColumnInfo(name = "publisher")
    val publisher: String?,

    @ColumnInfo(name = "source_ids")
    val sourceIds: String?
)

/**
 * Chapter entity representing occasions/categories of duas
 * Example: "When waking up", "Before sleeping", "Upon entering the mosque"
 */
@Entity(tableName = "chapters")
data class DuaChapterEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "audio_url")
    val audioUrl: String? = null
)

/**
 * Invocation entity representing individual duas/supplications
 * Contains Arabic text, transliteration, translation, and contextual information
 */
@Entity(
    tableName = "invocations",
    foreignKeys = [
        ForeignKey(
            entity = DuaChapterEntity::class,
            parentColumns = ["id"],
            childColumns = ["chapter_id"],
            onDelete = ForeignKey.NO_ACTION
        )
    ],
    indices = [Index(name = "idx_invocations_chapter", value = ["chapter_id"])]
)
data class DuaInvocationEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int,

    @ColumnInfo(name = "chapter_id")
    val chapterId: Int,

    @ColumnInfo(name = "position")
    val position: Int,

    @ColumnInfo(name = "arabic")
    val arabic: String?,

    @ColumnInfo(name = "transliteration")
    val transliteration: String?,

    @ColumnInfo(name = "translation")
    val translation: String?,

    @ColumnInfo(name = "context")
    val context: String?,

    @ColumnInfo(name = "instruction")
    val instruction: String?,

    @ColumnInfo(name = "note")
    val note: String?,

    @ColumnInfo(name = "post_context")
    val postContext: String?,

    @ColumnInfo(name = "description")
    val description: String?,

    @ColumnInfo(name = "source_ids")
    val sourceIds: String?
)

/**
 * Hadith reference entity linking invocations to hadith collections
 * Contains reference information to lookup hadith in separate databases
 *
 * Note: ForeignKey and Index constraints exist in the pre-packaged SQLite database
 * but are NOT defined here to avoid Room schema validation issues.
 * Room's fallbackToDestructiveMigration() will use the pre-packaged database as-is.
 */
@Entity(tableName = "hadith_references")
data class HadithReferenceEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int,

    @ColumnInfo(name = "invocation_id")
    val invocationId: Int,

    @ColumnInfo(name = "collection_id")
    val collectionId: Int?,

    @ColumnInfo(name = "collection_name")
    val collectionName: String?,

    @ColumnInfo(name = "hadith_number")
    val hadithNumber: Int?,

    @ColumnInfo(name = "reference_str")
    val referenceStr: String?,

    @ColumnInfo(name = "database_file")
    val databaseFile: String?
)

/**
 * Footnote entity for term definitions and additional notes
 */
@Entity(
    tableName = "footnotes",
    foreignKeys = [
        ForeignKey(
            entity = DuaChapterEntity::class,
            parentColumns = ["id"],
            childColumns = ["chapter_id"],
            onDelete = ForeignKey.NO_ACTION
        )
    ],
    indices = [Index(name = "idx_footnotes_chapter", value = ["chapter_id"])]
)
data class DuaFootnoteEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int,

    @ColumnInfo(name = "chapter_id")
    val chapterId: Int,

    @ColumnInfo(name = "term")
    val term: String?,

    @ColumnInfo(name = "definition")
    val definition: String?,

    @ColumnInfo(name = "note")
    val note: String?,

    @ColumnInfo(name = "source_ids")
    val sourceIds: String?
)

// ============= Domain Models =============

/**
 * Domain model for a chapter with dua count
 */
data class DuaChapter(
    val id: Int,
    val title: String,
    val duaCount: Int = 0
)

/**
 * Domain model for an individual dua/invocation
 */
data class Dua(
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

/**
 * Lightweight dua model for list display
 */
data class DuaBasic(
    val id: Int,
    val chapterId: Int,
    val position: Int,
    val arabic: String?,
    val transliteration: String?,
    val translation: String?
)

/**
 * Chapter with all its invocations
 */
data class ChapterWithDuas(
    val chapter: DuaChapter,
    val invocations: List<Dua>,
    val footnotes: List<DuaFootnote>
)

/**
 * Domain model for footnote
 */
data class DuaFootnote(
    val id: Int,
    val term: String?,
    val definition: String?,
    val note: String?
)

/**
 * Book metadata
 */
data class DuaBookMetadata(
    val title: String,
    val subtitle: String?,
    val publisher: String?
)

/**
 * Domain model for hadith reference
 */
data class HadithReference(
    val id: Int,
    val invocationId: Int,
    val collectionId: Int?,
    val collectionName: String?,
    val hadithNumber: Int?,
    val referenceStr: String?,
    val databaseFile: String?
) {
    /**
     * Get display name for the reference (e.g., "Bukhari #113")
     */
    val displayName: String
        get() = buildString {
            if (!collectionName.isNullOrEmpty()) {
                append(collectionName)
                if (hadithNumber != null) {
                    append(" #$hadithNumber")
                }
            } else if (!referenceStr.isNullOrEmpty()) {
                append(referenceStr)
            } else {
                append("Hadith Reference")
            }
        }
}

// ============= Extension Functions =============

fun DuaChapterEntity.toDuaChapter(duaCount: Int = 0) = DuaChapter(
    id = id,
    title = title,
    duaCount = duaCount
)

fun DuaInvocationEntity.toDua(chapterTitle: String = "") = Dua(
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

fun DuaInvocationEntity.toDuaBasic() = DuaBasic(
    id = id,
    chapterId = chapterId,
    position = position,
    arabic = arabic,
    transliteration = transliteration,
    translation = translation
)

fun DuaFootnoteEntity.toDuaFootnote() = DuaFootnote(
    id = id,
    term = term,
    definition = definition,
    note = note
)

fun DuaMetadataEntity.toDuaBookMetadata() = DuaBookMetadata(
    title = title,
    subtitle = subtitle,
    publisher = publisher
)

fun HadithReferenceEntity.toHadithReference() = HadithReference(
    id = id,
    invocationId = invocationId,
    collectionId = collectionId,
    collectionName = collectionName,
    hadithNumber = hadithNumber,
    referenceStr = referenceStr,
    databaseFile = databaseFile
)
