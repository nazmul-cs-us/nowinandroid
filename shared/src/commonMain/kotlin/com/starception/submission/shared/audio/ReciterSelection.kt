package com.starception.submission.shared.audio

import com.starception.submission.shared.storage.KeyValueStore
import com.starception.submission.shared.storage.platformKeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Cross-platform Quran reciter preference and URL resolution.
 * The built-in CDN remains the default and is resolved by the existing
 * resolveQuranAudioUrl() implementation on each platform.
 */
@kotlinx.serialization.Serializable
data class QuranReciterSource(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val availableSurahs: Set<Int>,
    val riwaya: String = "",
) {
    fun urlForSurah(surah: Int): String? {
        if (surah !in 1..114 || surah !in availableSurahs) return null
        if (!baseUrl.startsWith("https://")) return null
        return baseUrl.trimEnd('/') + "/" + surah.toString().padStart(3, '0') + ".mp3"
    }
}

class QuranReciterPreferences(
    private val store: KeyValueStore = platformKeyValueStore(),
) {
    companion object {
        private val revision = MutableStateFlow(0L)
        val changes = revision.asStateFlow()
    }
    fun selectedSource(): QuranReciterSource? = runCatching {
        kotlinx.serialization.json.Json.decodeFromString<QuranReciterSource>(
            store.getString("quran_reciter_source").orEmpty())
    }.getOrNull()

    fun selectSource(source: QuranReciterSource?) {
        store.putString("quran_reciter_source", source?.let {
            kotlinx.serialization.json.Json.encodeToString(QuranReciterSource.serializer(), it)
        }.orEmpty())
        select(source?.id)
    }

    fun selectedReciterId(): String? =
        store.getString("quran_selected_reciter_id")?.takeIf { it.isNotBlank() }

    fun select(reciterId: String?) {
        if (selectedSource()?.id != reciterId) store.putString("quran_reciter_source", "")
        store.putString("quran_selected_reciter_id", reciterId.orEmpty())
        revision.update { it + 1 }
    }
}

/** Shared catalog semantics for Android and iOS reciter settings. */
fun selectableQuranReciters(
    surah: Int,
    sources: List<QuranReciterSource>,
): List<QuranReciterSource> =
    if (surah !in 1..114) emptyList()
    else sources.filter { surah in it.availableSurahs && it.baseUrl.startsWith("https://") }
        .distinctBy { it.id }
        .sortedBy { it.displayName }
