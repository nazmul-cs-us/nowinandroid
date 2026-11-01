package com.starception.submission.shared.audio

/** Platform storage operations; Android's native reader uses its own download manager. */
interface ReciterLibrary {
    suspend fun catalog(): List<QuranReciterSource>
    suspend fun download(source: QuranReciterSource, onCompleted: (Int, Int) -> Unit)
    fun count(source: QuranReciterSource): Int
    fun bytes(source: QuranReciterSource): Long
    fun delete(source: QuranReciterSource)
}
object QuranReciterLibrary {
    var platform: ReciterLibrary? = null
}
