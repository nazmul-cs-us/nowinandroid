package com.starception.submission.core.qurandatabase

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

data class AyahTiming(
    val ayahNumber: Int,
    val startMs: Long,
    val endMs: Long,
)

/**
 * Fetches per-ayah timing data from the quran.com QDC API and caches it per
 * (surah, reciter) so the surah-detail screen can highlight the ayah that is
 * currently being recited.
 *
 * Default reciter id 7 = Mishary Rashid al-`Afasy, matching the app's audio.
 */
object AyahTimingRepository {

    private const val TAG = "AyahTiming"
    private const val DEFAULT_RECITER_ID = 7

    private val cache = HashMap<Long, List<AyahTiming>>()

    suspend fun getTimings(
        surahNumber: Int,
        reciterId: Int = DEFAULT_RECITER_ID,
    ): List<AyahTiming> = withContext(Dispatchers.IO) {
        val key = (reciterId.toLong() shl 32) or surahNumber.toLong()
        cache[key]?.let { return@withContext it }
        try {
            val urlStr = "https://api.qurancdn.com/api/qdc/audio/reciters/" +
                "$reciterId/audio_files?chapter=$surahNumber&segments=false"
            val json = URL(urlStr).readText()
            val files = JSONObject(json).optJSONArray("audio_files")
            if (files == null || files.length() == 0) return@withContext emptyList()
            val verseTimings = files.getJSONObject(0).optJSONArray("verse_timings")
                ?: return@withContext emptyList()
            val parsed = ArrayList<AyahTiming>(verseTimings.length())
            for (i in 0 until verseTimings.length()) {
                val t = verseTimings.getJSONObject(i)
                val ayahNum = t.optString("verse_key").substringAfter(":").toIntOrNull()
                    ?: continue
                parsed += AyahTiming(
                    ayahNumber = ayahNum,
                    startMs = t.optLong("timestamp_from", 0L),
                    endMs = t.optLong("timestamp_to", 0L),
                )
            }
            cache[key] = parsed
            Log.d(TAG, "Loaded ${parsed.size} ayah timings for surah=$surahNumber reciter=$reciterId")
            parsed
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch timings for surah=$surahNumber", e)
            emptyList()
        }
    }

    /** Binary search for the ayah covering [positionMs], or null if none. */
    fun findAyahAt(timings: List<AyahTiming>, positionMs: Long): Int? {
        if (timings.isEmpty()) return null
        // Linear search is fine here (≤ ~290 ayahs in Al-Baqarah).
        for (t in timings) {
            if (positionMs in t.startMs until t.endMs) return t.ayahNumber
        }
        // Past end → return last ayah's number while audio is finishing.
        return if (positionMs >= timings.last().endMs) timings.last().ayahNumber else null
    }
}
