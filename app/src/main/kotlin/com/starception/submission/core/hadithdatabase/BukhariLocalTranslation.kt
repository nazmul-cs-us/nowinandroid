package com.starception.submission.core.hadithdatabase

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Data class for local Bukhari hadith translation
 */
data class BukhariTranslation(
    val hadithNumber: Int,
    val volumeNumber: Int,
    val bookNumber: Int,
    val bookName: String,
    val narrator: String,
    val englishText: String
)

/**
 * Repository for loading local Bukhari English translations from JSON asset
 */
class BukhariLocalTranslationRepository private constructor(private val context: Context) {

    private var translationsMap: Map<Int, BukhariTranslation>? = null
    private var isLoaded = false

    /**
     * Load translations from JSON asset file
     */
    suspend fun loadTranslations(): Boolean {
        if (isLoaded) return true

        return withContext(Dispatchers.IO) {
            try {
                val jsonString = context.assets.open("sahih_bukhari.json").bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(jsonString)
                val map = mutableMapOf<Int, BukhariTranslation>()

                for (volIndex in 0 until jsonArray.length()) {
                    val volume = jsonArray.getJSONObject(volIndex)
                    val volumeName = volume.getString("name")
                    val volumeNumber = extractVolumeNumber(volumeName)
                    val books = volume.getJSONArray("books")

                    for (bookIndex in 0 until books.length()) {
                        val book = books.getJSONObject(bookIndex)
                        val bookName = book.getString("name")
                        val bookNumber = extractBookNumber(bookName)
                        val hadiths = book.getJSONArray("hadiths")

                        for (hadithIndex in 0 until hadiths.length()) {
                            val hadith = hadiths.getJSONObject(hadithIndex)
                            val info = hadith.getString("info")
                            val narrator = hadith.optString("by", "")
                            val text = hadith.getString("text")

                            val hadithNumber = extractHadithNumber(info)
                            if (hadithNumber > 0) {
                                map[hadithNumber] = BukhariTranslation(
                                    hadithNumber = hadithNumber,
                                    volumeNumber = volumeNumber,
                                    bookNumber = bookNumber,
                                    bookName = bookName,
                                    narrator = narrator,
                                    englishText = text.trim()
                                )
                            }
                        }
                    }
                }

                translationsMap = map
                isLoaded = true
                android.util.Log.d(TAG, "✅ Loaded ${map.size} Bukhari English translations")
                true
            } catch (e: Exception) {
                android.util.Log.e(TAG, "❌ Error loading Bukhari translations", e)
                false
            }
        }
    }

    /**
     * Get English translation for a hadith number
     */
    fun getTranslation(hadithNumber: Int): BukhariTranslation? {
        return translationsMap?.get(hadithNumber)
    }

    /**
     * Get formatted English text with narrator
     */
    fun getEnglishText(hadithNumber: Int): String? {
        val translation = getTranslation(hadithNumber) ?: return null
        return if (translation.narrator.isNotEmpty()) {
            "${translation.narrator}\n\n${translation.englishText}"
        } else {
            translation.englishText
        }
    }

    /**
     * Check if translations are loaded
     */
    fun isLoaded(): Boolean = isLoaded

    /**
     * Get total count of loaded translations
     */
    fun getCount(): Int = translationsMap?.size ?: 0

    private fun extractVolumeNumber(volumeName: String): Int {
        val regex = Regex("""Volume\s*(\d+)""", RegexOption.IGNORE_CASE)
        return regex.find(volumeName)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    private fun extractBookNumber(bookName: String): Int {
        val regex = Regex("""^(\d+)\.""")
        return regex.find(bookName)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    private fun extractHadithNumber(info: String): Int {
        val regex = Regex("""Number\s*(\d+)""", RegexOption.IGNORE_CASE)
        return regex.find(info)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    companion object {
        private const val TAG = "BukhariLocalTranslation"

        @Volatile
        private var INSTANCE: BukhariLocalTranslationRepository? = null

        fun getInstance(context: Context): BukhariLocalTranslationRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BukhariLocalTranslationRepository(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
}
