package com.starception.submission.shared.audio

/** Platform storage operations; Android's native reader uses its own download manager. */
interface ReciterLibrary {
    suspend fun previewUrl(source: QuranReciterSource?): String? = source?.let { it.urlForSurah(if (1 in it.availableSurahs) 1 else it.availableSurahs.minOrNull() ?: 1) }
    fun createPreviewPlayer(): ReciterPreviewPlayback? = null
    suspend fun catalog(): List<QuranReciterSource>
    suspend fun download(source: QuranReciterSource, onCompleted: (Int, Int) -> Unit)
    suspend fun offline(catalog: List<QuranReciterSource>): List<QuranReciterSource> = catalog.filter { count(it) > 0 }
    fun count(source: QuranReciterSource): Int
    fun bytes(source: QuranReciterSource): Long
    fun delete(source: QuranReciterSource)
}
object QuranReciterLibrary {
    var platform: ReciterLibrary? = null
}

interface ReciterPreviewPlayback {
    enum class Phase { BUFFERING, PLAYING, ENDED, FAILED }
    fun play(url: String): Boolean
    fun phase(): Phase
    fun stop()
}
