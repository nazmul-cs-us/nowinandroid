package com.starception.submission.feature.surah.tajweed

import android.content.Context
import android.util.Log
import com.starception.submission.download.AssetRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for Tajweed annotations.
 * Provides O(1) lookup for ayah Tajweed data with lazy loading.
 */
@Singleton
class TajweedRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val assetRepository: AssetRepository,
) {
    private val TAG = "TajweedRepository"

    // Full Tajweed data keyed by "surah:ayah"
    private var tajweedData: Map<String, List<TajweedAnnotation>>? = null

    // Per-surah cache for efficient access
    private val surahCache = mutableMapOf<Int, Map<Int, List<TajweedAnnotation>>>()

    // Mutex for thread-safe lazy loading
    private val mutex = Mutex()

    /**
     * Initialize and load all Tajweed data.
     * Call this once at app startup or when needed.
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (tajweedData == null) {
                Log.d(TAG, "🚀 Initializing Tajweed data...")
                tajweedData = TajweedParser.parse(context, assetRepository)
                Log.d(TAG, "✅ Tajweed data initialized with ${tajweedData?.size ?: 0} entries")
            }
        }
    }

    /**
     * Get Tajweed annotations for a specific ayah.
     * Returns null if no annotations exist for this ayah.
     */
    suspend fun getAnnotationsForAyah(surahNumber: Int, ayahNumber: Int): List<TajweedAnnotation>? {
        // Ensure data is loaded
        if (tajweedData == null) {
            initialize()
        }

        val key = "$surahNumber:$ayahNumber"
        return tajweedData?.get(key)
    }

    /**
     * Get all Tajweed annotations for a specific surah.
     * Returns a map of ayahNumber to list of annotations.
     */
    suspend fun getAnnotationsForSurah(surahNumber: Int): Map<Int, List<TajweedAnnotation>> {
        // Check cache first
        surahCache[surahNumber]?.let { return it }

        // Ensure data is loaded
        if (tajweedData == null) {
            initialize()
        }

        // Extract surah-specific data
        val surahData = mutableMapOf<Int, List<TajweedAnnotation>>()
        tajweedData?.forEach { (key, annotations) ->
            val parts = key.split(":")
            if (parts.size == 2 && parts[0].toIntOrNull() == surahNumber) {
                parts[1].toIntOrNull()?.let { ayahNumber ->
                    surahData[ayahNumber] = annotations
                }
            }
        }

        // Cache the result
        surahCache[surahNumber] = surahData
        Log.d(TAG, "📚 Cached Surah $surahNumber with ${surahData.size} annotated ayahs")

        return surahData
    }

    /**
     * Preload Tajweed data for a specific surah (call from background).
     */
    suspend fun preloadSurah(surahNumber: Int) = withContext(Dispatchers.IO) {
        getAnnotationsForSurah(surahNumber)
    }

    /**
     * Check if Tajweed data is loaded.
     */
    fun isLoaded(): Boolean = tajweedData != null

    /**
     * Get the total number of annotated ayahs.
     */
    fun getTotalAnnotatedAyahs(): Int = tajweedData?.size ?: 0
}
