package com.starception.submission.shared.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object IosReciterLibrary : ReciterLibrary {
    override suspend fun catalog(): List<QuranReciterSource> = IosReciterCatalog.fetch()
    override suspend fun download(source: QuranReciterSource, onCompleted: (Int, Int) -> Unit) {
        val expected = source.availableSurahs.size
        val completed = withContext(Dispatchers.Default) {
            IosReciterAudioStore.downloadAll(source, onCompleted)
        }
        check(completed == expected) {
            "Download interrupted. Retry to download the remaining surahs."
        }
    }
    override fun count(source: QuranReciterSource): Int = IosReciterAudioStore.downloadedCount(source)
    override fun bytes(source: QuranReciterSource): Long = IosReciterAudioStore.downloadedBytes(source)
    override fun delete(source: QuranReciterSource) { IosReciterAudioStore.deleteAll(source) }
}
