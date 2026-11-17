package com.starception.submission.feature.quran

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int

object BengaliIbnKathir {
    const val BOOK_KEY = "ibn_kathir_bn"
    const val LABEL = "Ibn Kathir · বাংলা"
    const val TITLE = "তাফসীর ইবনে কাসীর"
    const val PUBLISHER = "Tawheed Publication"
    const val SOURCE_URL = "https://qul.tarteel.ai/resources/tafsir/31"
    const val CATEGORY = "quran_tafsir_bn"
    const val CDN_PREFIX = "tafsir/bn-ibn-kathir/v1"
    val ayahCounts = listOf(7,286,200,176,120,165,206,75,129,109,123,111,43,52,99,128,111,110,98,135,112,78,118,64,77,227,93,88,69,60,34,30,73,54,45,83,182,88,75,85,54,53,89,59,37,35,38,29,18,45,60,49,62,55,78,96,29,22,24,13,14,11,11,18,12,12,30,52,52,44,28,28,20,56,40,31,50,40,46,42,29,19,36,25,22,17,19,26,30,20,15,21,11,8,8,19,5,8,8,11,11,8,3,9,5,4,7,3,6,3,5,4,5,6)
    fun chapterKey(surah: Int): String {
        require(surah in 1..114) { "Invalid surah" }
        return "$CDN_PREFIX/$surah.json"
    }
}

/** The platform reader resolves a SHA-256-verified CDN file, reusing its persistent offline cache. */
class BengaliIbnKathirRepository(private val readChapter: suspend (Int) -> String) {
    private val mutex = Mutex()
    private val chapters = linkedMapOf<Int, List<String>>()

    suspend fun text(surah: Int, ayah: Int): String = withContext(Dispatchers.Default) {
        require(surah in 1..114 && ayah in 1..BengaliIbnKathir.ayahCounts[surah - 1]) { "Invalid ayah" }
        mutex.withLock {
            val chapter = chapters[surah] ?: decode(surah, readChapter(surah)).also {
                chapters[surah] = it
                // Keep large commentaries bounded; the platform persists the complete files on disk.
                if (chapters.size > 2) chapters.remove(chapters.keys.first())
            }
            chapter[ayah - 1]
        }
    }

    suspend fun downloadAll(onProgress: (Int, Int) -> Unit) {
        for (surah in 1..114) {
            text(surah, 1)
            onProgress(surah, 114)
        }
    }

    private fun decode(surah: Int, source: String): List<String> {
        val root = Json.parseToJsonElement(source).jsonObject
        require(root.getValue("surah").jsonPrimitive.int == surah) { "Wrong tafsir chapter" }
        val expected = BengaliIbnKathir.ayahCounts[surah - 1]
        require(root.getValue("ayahCount").jsonPrimitive.int == expected) { "Incomplete tafsir chapter" }
        val passages = root.getValue("passages").jsonArray.map { it.jsonPrimitive.content }
        val refs = root.getValue("ayahPassage").jsonArray.map { it.jsonPrimitive.int }
        require(refs.size == expected && refs.all { it in passages.indices }) { "Invalid ayah index" }
        require(passages.all(String::isNotBlank)) { "Empty tafsir passage" }
        return refs.map(passages::get)
    }
}
