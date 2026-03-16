package com.starception.submission.feature.surah.tajweed

import android.content.Context
import android.util.Log
import com.starception.submission.download.AssetRepository
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parser for Tajweed JSON data.
 * Parses the tajweed.json file from assets and returns a map keyed by "surah:ayah".
 */
object TajweedParser {
    private const val TAG = "TajweedParser"
    private const val TAJWEED_FILE = "tajweed.json"

    /**
     * Parse the Tajweed JSON file and return a map of annotations.
     * Key format: "surahNumber:ayahNumber" (e.g., "1:1", "2:255")
     */
    fun parse(context: Context, assetRepository: AssetRepository? = null): Map<String, List<TajweedAnnotation>> {
        val startTime = System.currentTimeMillis()
        val resultMap = mutableMapOf<String, List<TajweedAnnotation>>()

        try {
            val inputStream = assetRepository?.openAsset("json/$TAJWEED_FILE")
                ?: context.assets.open(TAJWEED_FILE)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)

            Log.d(TAG, "📖 Parsing ${jsonArray.length()} ayahs from tajweed.json")

            for (i in 0 until jsonArray.length()) {
                val ayahObj = jsonArray.getJSONObject(i)
                val surahNumber = ayahObj.getInt("surah")
                val ayahNumber = ayahObj.getInt("ayah")
                val annotationsArray = ayahObj.getJSONArray("annotations")

                val annotations = mutableListOf<TajweedAnnotation>()
                for (j in 0 until annotationsArray.length()) {
                    val annObj = annotationsArray.getJSONObject(j)
                    val ruleKey = annObj.getString("rule")
                    val startIndex = annObj.getInt("start")
                    val endIndex = annObj.getInt("end")

                    val rule = TajweedRule.fromJsonKey(ruleKey)
                    if (rule != null) {
                        annotations.add(TajweedAnnotation(rule, startIndex, endIndex))
                    } else {
                        Log.w(TAG, "⚠️ Unknown Tajweed rule: $ruleKey")
                    }
                }

                if (annotations.isNotEmpty()) {
                    val key = "$surahNumber:$ayahNumber"
                    resultMap[key] = annotations
                }
            }

            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "✅ Parsed ${resultMap.size} ayahs with Tajweed annotations in ${elapsed}ms")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error parsing tajweed.json: ${e.message}", e)
        }

        return resultMap
    }

    /**
     * Parse Tajweed data for a single surah (memory-efficient for large files).
     */
    fun parseForSurah(context: Context, surahNumber: Int, assetRepository: AssetRepository? = null): Map<Int, List<TajweedAnnotation>> {
        val startTime = System.currentTimeMillis()
        val resultMap = mutableMapOf<Int, List<TajweedAnnotation>>()

        try {
            val inputStream = assetRepository?.openAsset("json/$TAJWEED_FILE")
                ?: context.assets.open(TAJWEED_FILE)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)

            for (i in 0 until jsonArray.length()) {
                val ayahObj = jsonArray.getJSONObject(i)
                val currentSurah = ayahObj.getInt("surah")

                // Skip if not the target surah
                if (currentSurah != surahNumber) continue

                val ayahNumber = ayahObj.getInt("ayah")
                val annotationsArray = ayahObj.getJSONArray("annotations")

                val annotations = mutableListOf<TajweedAnnotation>()
                for (j in 0 until annotationsArray.length()) {
                    val annObj = annotationsArray.getJSONObject(j)
                    val ruleKey = annObj.getString("rule")
                    val startIndex = annObj.getInt("start")
                    val endIndex = annObj.getInt("end")

                    val rule = TajweedRule.fromJsonKey(ruleKey)
                    if (rule != null) {
                        annotations.add(TajweedAnnotation(rule, startIndex, endIndex))
                    }
                }

                if (annotations.isNotEmpty()) {
                    resultMap[ayahNumber] = annotations
                }
            }

            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "📗 Parsed Surah $surahNumber: ${resultMap.size} ayahs in ${elapsed}ms")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error parsing tajweed for surah $surahNumber: ${e.message}", e)
        }

        return resultMap
    }
}
