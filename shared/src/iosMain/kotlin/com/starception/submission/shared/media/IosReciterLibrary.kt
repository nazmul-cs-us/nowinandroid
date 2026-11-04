package com.starception.submission.shared.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object IosReciterLibrary : ReciterLibrary {
    override suspend fun previewUrl(source: QuranReciterSource?): String? =
        if (source == null) resolveIosQuranAudioUrl(1, "ar", false)
        else source.urlForSurah(if (1 in source.availableSurahs) 1 else source.availableSurahs.minOrNull() ?: 1)
    override fun createPreviewPlayer(): ReciterPreviewPlayback = IosReciterPreviewPlayback()
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
    override suspend fun offline(catalog: List<QuranReciterSource>): List<QuranReciterSource> = withContext(Dispatchers.Default) {
        val store = com.starception.submission.shared.storage.platformKeyValueStore()
        val remembered = runCatching {
            kotlinx.serialization.json.Json.decodeFromString<List<QuranReciterSource>>(store.getString("quran_offline_reciters").orEmpty())
        }.getOrDefault(emptyList())
        val cached = runCatching {
            kotlinx.serialization.json.Json.decodeFromString<List<QuranReciterSource>>(store.getString("quran_reciter_catalog").orEmpty())
        }.getOrDefault(emptyList())
        (remembered + catalog + cached + listOfNotNull(QuranReciterPreferences().selectedSource()))
            .distinctBy { it.id }.filter { count(it) > 0 }.sortedBy { it.displayName }
            .also { available -> available.forEach(IosReciterAudioStore::rememberSource) }
    }
    override fun count(source: QuranReciterSource): Int = IosReciterAudioStore.downloadedCount(source)
    override fun bytes(source: QuranReciterSource): Long = IosReciterAudioStore.downloadedBytes(source)
    override fun delete(source: QuranReciterSource) { IosReciterAudioStore.deleteAll(source) }
}
