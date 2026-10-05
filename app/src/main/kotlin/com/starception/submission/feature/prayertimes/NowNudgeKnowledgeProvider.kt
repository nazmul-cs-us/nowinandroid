/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.starception.submission.feature.prayertimes

import android.content.Context
import com.starception.submission.core.hadithdatabase.BukhariLocalTranslationRepository
import com.starception.submission.core.hadithdatabase.HadithDatabase
import com.starception.submission.core.hadithdatabase.HadithRepository
import com.starception.submission.core.model.data.BukhariBooks
import com.starception.submission.core.model.data.ShamayelBooks
import com.starception.submission.core.model.deenly.DeenlyActionIds
import com.starception.submission.core.model.deenly.DeenlyNudge
import com.starception.submission.core.model.deenly.DeenlyNudgeAction
import com.starception.submission.core.qurandatabase.QuranTranslationRepository
import com.starception.submission.download.AssetRepository
import com.starception.submission.ml.DeenlyKnowledgeDecision
import com.starception.submission.ml.DeenlyKnowledgeModel
import com.starception.submission.ml.DeenlyKnowledgeTask
import com.starception.submission.ml.DeenlySourceEnvelope
import java.security.MessageDigest
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal sealed interface NowNudgeKnowledgeTarget {
    data class QuranAyah(
        val surahNumber: Int,
        val ayahNumber: Int,
    ) : NowNudgeKnowledgeTarget

    data class Hadith(
        val collectionName: String,
        val databaseFile: String,
        val hadithNumber: Int,
    ) : NowNudgeKnowledgeTarget
}

internal data class GroundedKnowledgeSource(
    val id: String,
    val collection: String,
    val reference: String,
    val topic: String,
    val sourceText: String,
    val target: NowNudgeKnowledgeTarget,
)

internal data class NowNudgeKnowledgeCandidate(
    val nudge: DeenlyNudge,
    val target: NowNudgeKnowledgeTarget,
)

internal fun interface GroundedKnowledgeSourceLoader {
    suspend fun load(date: LocalDate, variation: Int): GroundedKnowledgeSource?
}

internal fun interface KnowledgeDecisionGenerator {
    suspend fun generate(
        task: DeenlyKnowledgeTask,
        source: DeenlySourceEnvelope,
    ): DeenlyKnowledgeDecision?
}

/**
 * Turns one immutable database record into a model-titled Now Nudge.
 *
 * The model never supplies the body, citation, identifier, or destination. Those values stay
 * attached to the exact database record selected by [GroundedKnowledgeSourceLoader].
 */
internal class NowNudgeKnowledgeProvider(
    private val sourceLoader: GroundedKnowledgeSourceLoader,
    private val decisionGenerator: KnowledgeDecisionGenerator,
) {
    suspend fun load(
        date: LocalDate,
        variation: Int = 0,
        appSituation: String? = null,
    ): NowNudgeKnowledgeCandidate? {
        val source = withContext(Dispatchers.IO) {
            sourceLoader.load(date, variation)
        } ?: return null
        val envelope = DeenlySourceEnvelope(
            collection = source.collection,
            reference = source.reference,
            topic = source.topic,
            appSituation = appSituation,
            sourceText = source.sourceText,
            sourceTextSha256 = source.sourceText.sha256(),
        )
        val decision = decisionGenerator.generate(DeenlyKnowledgeTask.KNOWLEDGE, envelope)
        return buildCandidate(date, source, decision)
    }

    companion object {
        fun create(
            context: Context,
            assetRepository: AssetRepository,
        ): NowNudgeKnowledgeProvider {
            val appContext = context.applicationContext
            val model = DeenlyKnowledgeModel(appContext)
            return NowNudgeKnowledgeProvider(
                sourceLoader = AndroidGroundedKnowledgeSourceLoader(
                    context = appContext,
                    assetRepository = assetRepository,
                ),
                decisionGenerator = KnowledgeDecisionGenerator(model::generate),
            )
        }

        internal fun buildCandidate(
            date: LocalDate,
            source: GroundedKnowledgeSource,
            decision: DeenlyKnowledgeDecision?,
        ): NowNudgeKnowledgeCandidate? {
            val generated = decision as? DeenlyKnowledgeDecision.Knowledge ?: return null
            val title = generated.title
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(MAX_TITLE_CHARS)
                .trim()
                .takeIf(String::isNotEmpty)
                ?: return null
            return NowNudgeKnowledgeCandidate(
                nudge = DeenlyNudge(
                    id = "model-knowledge-${date}-${source.id}",
                    action = DeenlyNudgeAction.OPEN_CONTEXTUAL_RECOMMENDATION,
                    label = title,
                    actionId = DeenlyActionIds.LEARNING_OPEN_KNOWLEDGE,
                    supportingText = source.sourceText,
                    sourceLabel = source.reference,
                ),
                target = source.target,
            )
        }

        private const val MAX_TITLE_CHARS = 84
    }
}

private class AndroidGroundedKnowledgeSourceLoader(
    context: Context,
    private val assetRepository: AssetRepository,
) : GroundedKnowledgeSourceLoader {
    private val appContext = context.applicationContext
    private val quranRepository by lazy {
        QuranTranslationRepository(appContext, "en", assetRepository)
    }
    private val hadithRepository by lazy {
        HadithRepository(appContext, assetRepository)
    }
    private val bukhariTranslations by lazy {
        BukhariLocalTranslationRepository.getInstance(appContext, assetRepository)
    }

    override suspend fun load(date: LocalDate, variation: Int): GroundedKnowledgeSource? {
        val selectionDate = date.plusDays(variation.coerceAtLeast(0).toLong())
        val sourceCount = HADITH_COLLECTIONS.size + 1
        val firstSource = Math.floorMod(selectionDate.toEpochDay(), sourceCount.toLong()).toInt()
        repeat(sourceCount) { offset ->
            val sourceIndex = (firstSource + offset) % sourceCount
            val source = runCatching {
                if (sourceIndex == 0) {
                    loadQuran(selectionDate)
                } else {
                    loadHadith(selectionDate, HADITH_COLLECTIONS[sourceIndex - 1])
                }
            }.getOrNull()
            if (source != null) return source
        }
        return null
    }

    private suspend fun loadQuran(date: LocalDate): GroundedKnowledgeSource? {
        val firstReference = Math.floorMod(
            date.toEpochDay() * 37L,
            QURAN_REFERENCES.size.toLong(),
        ).toInt()
        repeat(QURAN_REFERENCES.size) { offset ->
            val reference = QURAN_REFERENCES[(firstReference + offset) % QURAN_REFERENCES.size]
            val ayah = quranRepository.getAyahsBySurahNumber(reference.surahNumber)
                .firstOrNull { it.numberInSurah == reference.ayahNumber }
                ?: return@repeat
            val sourceText = ayah.text.takeIf(String::isGroundedText) ?: return@repeat
            val surah = quranRepository.getSurahByNumber(reference.surahNumber)
            val topic = surah?.nameTranslation
                ?.takeIf(String::isNotBlank)
                ?: surah?.nameEnglish?.takeIf(String::isNotBlank)
                ?: "Surah ${reference.surahNumber}"
            return GroundedKnowledgeSource(
                id = "quran-${reference.surahNumber}-${reference.ayahNumber}",
                collection = "quran",
                reference = "Quran ${reference.surahNumber}:${reference.ayahNumber}",
                topic = topic,
                sourceText = sourceText,
                target = NowNudgeKnowledgeTarget.QuranAyah(
                    surahNumber = reference.surahNumber,
                    ayahNumber = reference.ayahNumber,
                ),
            )
        }
        return null
    }

    private suspend fun loadHadith(
        date: LocalDate,
        collection: HadithCollectionSpec,
    ): GroundedKnowledgeSource? {
        if (!HadithDatabase.isDatabaseAvailable(appContext, collection.databaseFile)) return null
        val metadata = HadithDatabase.getCollectionMetadata(appContext, collection.databaseFile)
        val lastHadithId = collection.lastHadithId
            ?: metadata?.hadithCount?.takeIf { it > 0 }
            ?: return null
        val firstId = Math.floorMod(
            date.toEpochDay() * 53L + collection.databaseFile.hashCode(),
            lastHadithId.toLong(),
        ).toInt() + 1

        if (collection.databaseFile == BUKHARI_DATABASE) {
            bukhariTranslations.loadTranslations()
        }

        repeat(minOf(lastHadithId, MAX_HADITH_ATTEMPTS)) { attempt ->
            val hadithId = Math.floorMod(
                firstId - 1 + (attempt * HADITH_PROBE_STEP),
                lastHadithId,
            ) + 1
            val hadith = runCatching {
                hadithRepository.getHadith(collection.databaseFile, hadithId)
            }.getOrNull() ?: return@repeat

            val bukhari = if (collection.databaseFile == BUKHARI_DATABASE) {
                bukhariTranslations.getTranslation(hadithId)
            } else {
                null
            }
            val sourceText = when (collection.databaseFile) {
                BUKHARI_DATABASE -> bukhari?.englishText
                SHAMAYEL_DATABASE -> hadith.englishText
                else -> hadith.englishText ?: hadith.textPlain
            }?.takeIf(String::isEnglishGroundedText) ?: return@repeat

            val displayName = metadata?.nameEnglish
                ?.takeIf(String::isNotBlank)
                ?: collection.displayName
            val topic = when (collection.databaseFile) {
                BUKHARI_DATABASE -> bukhari?.bookName
                    ?: BukhariBooks.findByHadithId(hadithId)?.nameEnglish
                SHAMAYEL_DATABASE -> ShamayelBooks.findByHadithId(hadithId)?.nameEnglish
                else -> displayName
            }?.takeIf(String::isNotBlank) ?: displayName
            val reference = if (bukhari != null) {
                "Sahih al-Bukhari, Volume ${bukhari.volumeNumber}, " +
                    "Book ${bukhari.bookNumber}, Hadith ${bukhari.hadithNumber}"
            } else {
                "$displayName $hadithId"
            }
            return GroundedKnowledgeSource(
                id = "${collection.databaseFile.removeSuffix(".db")}-$hadithId",
                collection = collection.modelCollection,
                reference = reference,
                topic = topic,
                sourceText = sourceText,
                target = NowNudgeKnowledgeTarget.Hadith(
                    collectionName = displayName,
                    databaseFile = collection.databaseFile,
                    hadithNumber = hadithId,
                ),
            )
        }
        return null
    }

    private data class QuranReference(val surahNumber: Int, val ayahNumber: Int)

    private data class HadithCollectionSpec(
        val databaseFile: String,
        val displayName: String,
        val modelCollection: String,
        val lastHadithId: Int? = null,
    )

    private companion object {
        const val BUKHARI_DATABASE = "sahih_bukhari.db"
        const val SHAMAYEL_DATABASE = "shamayele_tirmidhi_complete.db"
        const val MAX_HADITH_ATTEMPTS = 64
        const val HADITH_PROBE_STEP = 37

        val QURAN_REFERENCES = listOf(
            QuranReference(1, 4),
            QuranReference(2, 115),
            QuranReference(2, 152),
            QuranReference(2, 186),
            QuranReference(2, 286),
            QuranReference(3, 139),
            QuranReference(13, 28),
            QuranReference(16, 90),
            QuranReference(39, 53),
            QuranReference(49, 13),
            QuranReference(55, 13),
            QuranReference(94, 5),
            QuranReference(94, 6),
            QuranReference(103, 3),
        )

        val HADITH_COLLECTIONS = listOf(
            HadithCollectionSpec(BUKHARI_DATABASE, "Sahih al-Bukhari", "sahih_al_bukhari", 7_277),
            HadithCollectionSpec("sahih_muslim.db", "Sahih Muslim", "sahih_muslim"),
            HadithCollectionSpec("sunan_abu_dawud.db", "Sunan Abu Dawud", "sunan_abu_dawud"),
            HadithCollectionSpec("sunan_tirmidhi.db", "Jami' at-Tirmidhi", "jami'_at-tirmidhi"),
            HadithCollectionSpec("sunan_nasai.db", "Sunan an-Nasa'i", "sunan_an-nasa'i"),
            HadithCollectionSpec("sunan_ibn_majah.db", "Sunan Ibn Majah", "sunan_ibn_majah"),
            HadithCollectionSpec("muwatta_malik.db", "Muwatta Malik", "muwatta_malik"),
            HadithCollectionSpec("musnad_ahmad.db", "Musnad Ahmad", "musnad_ahmad"),
            HadithCollectionSpec("sunan_darimi.db", "Sunan ad-Darimi", "sunan_ad-darimi"),
            HadithCollectionSpec(SHAMAYEL_DATABASE, "Shama'il At-Tirmidhi", "shamayel_at_tirmidhi", 322),
        )
    }
}

private fun String.isGroundedText(): Boolean = isNotBlank() && length >= 12

/** Prevents Arabic-only legacy fields from being presented as an English bot suggestion. */
internal fun String.isEnglishGroundedText(): Boolean {
    if (!isGroundedText()) return false
    val latinLetters = count { it in 'A'..'Z' || it in 'a'..'z' }
    val arabicLetters = count { character ->
        character.code in 0x0600..0x06FF ||
            character.code in 0x0750..0x077F ||
            character.code in 0x08A0..0x08FF
    }
    return latinLetters >= 12 && latinLetters > arabicLetters
}

private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(toByteArray())
    .joinToString(separator = "") { "%02x".format(it) }
