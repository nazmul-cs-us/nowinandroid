package com.starception.submission.core.hadithdatabase

import android.content.Context
import com.starception.submission.download.AssetRepository

/**
 * Compatibility facade for existing playback callers. English must come from
 * the SAME database row as the reader. The old Volume/Book/Number JSON is not a
 * crosswalk: its Number field restarts and must never be keyed by a local ID.
 */
class BukhariLocalTranslationRepository private constructor(
    private val context: Context,
    private val assetRepository: AssetRepository?,
) {
    suspend fun loadTranslations(): Boolean =
        HadithDatabase.isDatabaseAvailable(context, "sahih_bukhari.db")

    suspend fun getEnglishText(hadithNumber: Int): String? {
        val hadith = HadithRepository(context, assetRepository)
            .getHadith("sahih_bukhari.db", hadithNumber) ?: return null
        return (hadith.englishText ?: hadith.textPlain)?.takeIf(String::isNotBlank)
    }

    companion object {
        fun getInstance(context: Context, assetRepository: AssetRepository? = null) =
            BukhariLocalTranslationRepository(context.applicationContext, assetRepository)
    }
}
