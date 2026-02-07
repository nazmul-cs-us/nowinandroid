package com.starception.submission.core.translation

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Translation service that translates text using Reverso API (primary)
 * with Google Translate as fallback.
 *
 * Uses the same language preference as the Dua/Quran translation setting.
 */
class TranslationService private constructor(private val context: Context) {

    companion object {
        private const val TAG = "TranslationService"
        private const val PREFS_NAME = "quran_prefs"
        private const val TRANSLATION_KEY = "quran_translation"
        private const val CACHE_PREFS_NAME = "translation_cache"

        @Volatile
        private var instance: TranslationService? = null

        fun getInstance(context: Context): TranslationService {
            return instance ?: synchronized(this) {
                instance ?: TranslationService(context.applicationContext).also { instance = it }
            }
        }

        // Language code mapping from app codes to Reverso/Google codes
        private val languageCodeMap = mapOf(
            "en" to "en",
            "ar" to "ar",
            "bn" to "bn",  // Bengali
            "zh" to "zh",  // Chinese
            "es" to "es",  // Spanish
            "fr" to "fr",  // French
            "id" to "id",  // Indonesian
            "ru" to "ru",  // Russian
            "sv" to "sv",  // Swedish
            "tr" to "tr",  // Turkish
            "ur" to "ur"   // Urdu
        )

        // Reverso supported language pairs (from English)
        private val reversoSupportedLanguages = setOf(
            "ar", "zh", "fr", "de", "he", "it", "ja", "ko", "nl", "pl",
            "pt", "ro", "ru", "es", "tr", "uk"
        )

        // Translation provider constants
        const val PROVIDER_AUTO = "auto"      // Try Reverso first, fallback to Google
        const val PROVIDER_GOOGLE = "google"
        const val PROVIDER_REVERSO = "reverso"
        private const val PROVIDER_KEY = "translation_provider"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val cachePrefs: SharedPreferences = context.getSharedPreferences(CACHE_PREFS_NAME, Context.MODE_PRIVATE)

    // In-memory cache for faster access
    private val memoryCache = ConcurrentHashMap<String, String>()

    /**
     * Get the currently selected translation provider
     */
    fun getSelectedProvider(): String {
        return prefs.getString(PROVIDER_KEY, PROVIDER_AUTO) ?: PROVIDER_AUTO
    }

    /**
     * Set the translation provider
     */
    fun setSelectedProvider(provider: String) {
        prefs.edit().putString(PROVIDER_KEY, provider).apply()
    }

    /**
     * Get the currently selected translation language code
     */
    fun getSelectedLanguage(): String {
        return prefs.getString(TRANSLATION_KEY, "en") ?: "en"
    }

    /**
     * Translate text to the selected language.
     * Auto-detects source language.
     * Handles Arabic → English translation when English is selected.
     *
     * @param text The text to translate
     * @param targetLang Optional target language. If not provided, reads from SharedPreferences.
     */
    suspend fun translate(text: String, targetLang: String? = null): String {
        val targetLanguage = targetLang ?: getSelectedLanguage()

        // Detect if text is primarily Arabic
        val isArabic = text.any { it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' }
        val sourceLang = if (isArabic) "ar" else "auto"

        // If source and target are the same, return original
        if (sourceLang == targetLanguage) {
            return text
        }

        // If target is transliteration, return original (no translation API for transliteration)
        if (targetLanguage == "transliteration") {
            return text
        }

        // If target is English and source is not Arabic, return original
        // (text is already in English)
        if (targetLanguage == "en" && !isArabic) {
            return text
        }

        // Check cache first
        val cacheKey = generateCacheKey(text, targetLanguage)
        getCachedTranslation(cacheKey)?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                val provider = getSelectedProvider()
                var result: String? = null

                when (provider) {
                    PROVIDER_GOOGLE -> {
                        // Use Google only
                        result = translateWithGoogle(text, sourceLang, targetLanguage)
                        Log.d(TAG, "Using Google Translate provider")
                    }
                    PROVIDER_REVERSO -> {
                        // Use Reverso only
                        if (reversoSupportedLanguages.contains(targetLanguage)) {
                            result = translateWithReverso(text, sourceLang, targetLanguage)
                            Log.d(TAG, "Using Reverso provider")
                        } else {
                            Log.w(TAG, "Reverso doesn't support $targetLanguage, falling back to Google")
                            result = translateWithGoogle(text, sourceLang, targetLanguage)
                        }
                    }
                    else -> {
                        // Auto mode: Try Reverso first, fallback to Google
                        if (reversoSupportedLanguages.contains(targetLanguage)) {
                            result = translateWithReverso(text, sourceLang, targetLanguage)
                        }
                        if (result == null) {
                            result = translateWithGoogle(text, sourceLang, targetLanguage)
                        }
                        Log.d(TAG, "Using Auto provider (Reverso -> Google fallback)")
                    }
                }

                if (result != null) {
                    cacheTranslation(cacheKey, result)
                    return@withContext result
                }

                // If all fail, return original text
                Log.w(TAG, "Translation failed, returning original text")
                text
            } catch (e: Exception) {
                Log.e(TAG, "Translation error", e)
                text
            }
        }
    }

    /**
     * Translate text from English to the target language.
     * Used for Bukhari hadiths where we have local English translations.
     *
     * @param text The English text to translate
     * @param targetLang The target language code
     */
    suspend fun translateFromEnglish(text: String, targetLang: String): String {
        // If target is English, return as-is
        if (targetLang == "en") {
            return text
        }

        // If target is transliteration, return original
        if (targetLang == "transliteration") {
            return text
        }

        // Check cache first
        val cacheKey = generateCacheKey("en_$text", targetLang)
        getCachedTranslation(cacheKey)?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                val provider = getSelectedProvider()
                var result: String? = null

                when (provider) {
                    PROVIDER_GOOGLE -> {
                        result = translateWithGoogle(text, "en", targetLang)
                        Log.d(TAG, "Translating from English using Google")
                    }
                    PROVIDER_REVERSO -> {
                        if (reversoSupportedLanguages.contains(targetLang)) {
                            result = translateWithReverso(text, "en", targetLang)
                            Log.d(TAG, "Translating from English using Reverso")
                        } else {
                            result = translateWithGoogle(text, "en", targetLang)
                        }
                    }
                    else -> {
                        // Auto mode
                        if (reversoSupportedLanguages.contains(targetLang)) {
                            result = translateWithReverso(text, "en", targetLang)
                        }
                        if (result == null) {
                            result = translateWithGoogle(text, "en", targetLang)
                        }
                    }
                }

                if (result != null) {
                    cacheTranslation(cacheKey, result)
                    return@withContext result
                }

                Log.w(TAG, "Translation from English failed, returning original")
                text
            } catch (e: Exception) {
                Log.e(TAG, "Translation from English error", e)
                text
            }
        }
    }

    /**
     * Translate using Reverso API
     */
    private fun translateWithReverso(text: String, sourceLang: String, targetLang: String): String? {
        return try {
            val url = URL("https://api.reverso.net/translate/v1/translation")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            connection.doOutput = true
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val requestBody = JSONObject().apply {
                put("input", text)
                put("from", sourceLang)
                put("to", targetLang)
                put("format", "text")
                put("options", JSONObject().apply {
                    put("sentenceSplitter", true)
                    put("origin", "reversomobile")
                    put("contextResults", false)
                    put("languageDetection", false)
                })
            }

            connection.outputStream.bufferedWriter().use { it.write(requestBody.toString()) }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().readText()
                val jsonResponse = JSONObject(response)
                val translations = jsonResponse.optJSONArray("translation")
                if (translations != null && translations.length() > 0) {
                    val result = translations.getString(0)
                    Log.d(TAG, "Reverso translation successful: ${text.take(50)}... -> ${result.take(50)}...")
                    result
                } else {
                    null
                }
            } else {
                Log.w(TAG, "Reverso API returned ${connection.responseCode}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Reverso translation failed", e)
            null
        }
    }

    /**
     * Translate using Google Translate API (free endpoint)
     * Handles long texts by chunking them into smaller pieces
     */
    private fun translateWithGoogle(text: String, sourceLang: String, targetLang: String): String? {
        return try {
            // Split long text into chunks to avoid URL length limits
            val maxChunkSize = 1500  // Safe limit for URL encoding
            if (text.length > maxChunkSize) {
                Log.d(TAG, "Text too long (${text.length} chars), splitting into chunks")
                val chunks = text.chunked(maxChunkSize)
                val translatedChunks = chunks.mapNotNull { chunk ->
                    translateGoogleChunk(chunk, sourceLang, targetLang)
                }
                if (translatedChunks.size == chunks.size) {
                    val result = translatedChunks.joinToString("")
                    Log.d(TAG, "Google translation successful (chunked): ${text.take(50)}... -> ${result.take(50)}...")
                    result
                } else {
                    Log.w(TAG, "Some chunks failed to translate")
                    null
                }
            } else {
                translateGoogleChunk(text, sourceLang, targetLang)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Google translation failed", e)
            null
        }
    }

    /**
     * Translate a single chunk using Google Translate API
     */
    private fun translateGoogleChunk(text: String, sourceLang: String, targetLang: String): String? {
        return try {
            val encodedText = URLEncoder.encode(text, "UTF-8")
            val urlString = "https://translate.googleapis.com/translate_a/single?" +
                    "client=gtx&sl=$sourceLang&tl=$targetLang&dt=t&q=$encodedText"

            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().readText()
                // Google returns nested arrays: [[["translated text","original text",...],...],...]
                val jsonArray = JSONArray(response)
                val translations = jsonArray.optJSONArray(0)
                if (translations != null) {
                    val result = StringBuilder()
                    for (i in 0 until translations.length()) {
                        val translationPart = translations.optJSONArray(i)
                        if (translationPart != null && translationPart.length() > 0) {
                            val translatedText = translationPart.optString(0)
                            if (translatedText.isNotEmpty()) {
                                result.append(translatedText)
                            }
                        }
                    }
                    if (result.isNotEmpty()) {
                        Log.d(TAG, "Google translation successful: ${text.take(50)}... -> ${result.toString().take(50)}...")
                        result.toString()
                    } else {
                        null
                    }
                } else {
                    null
                }
            } else {
                Log.w(TAG, "Google Translate API returned ${connection.responseCode}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Google chunk translation failed", e)
            null
        }
    }

    /**
     * Generate a cache key for the translation
     */
    private fun generateCacheKey(text: String, targetLang: String): String {
        return "${text.hashCode()}_$targetLang"
    }

    /**
     * Get cached translation
     */
    private fun getCachedTranslation(key: String): String? {
        // Check memory cache first
        memoryCache[key]?.let { return it }

        // Check persistent cache
        val cached = cachePrefs.getString(key, null)
        if (cached != null) {
            memoryCache[key] = cached  // Populate memory cache
        }
        return cached
    }

    /**
     * Cache translation
     */
    private fun cacheTranslation(key: String, translation: String) {
        memoryCache[key] = translation
        cachePrefs.edit().putString(key, translation).apply()
    }

    /**
     * Clear translation cache
     */
    fun clearCache() {
        memoryCache.clear()
        cachePrefs.edit().clear().apply()
    }
}
