package com.starception.submission.feature.quran

import com.starception.submission.core.images.reciterPersonKey

/** Deduplicate recordings, never just people: Hafs/Warsh and Murattal/Mujawwad remain distinct.
 * Prefer an existing selection or download so its stable storage ID stays usable.
 */
fun <T> distinctReciterRecordings(
    entries: List<T>,
    id: (T) -> String,
    name: (T) -> String,
    server: (T) -> String,
    style: (T) -> String,
    surahs: (T) -> Set<Int>,
    selectedId: String? = null,
    downloadedIds: Set<String> = emptySet(),
): List<T> {
    val byId = entries.distinctBy(id)
    data class Key(val person: String, val server: String, val style: String, val surahs: Set<Int>)
    return byId.groupBy {
        Key(reciterPersonKey(name(it)), server(it).trim().trimEnd('/'),
            style(it).lowercase().filter(Char::isLetterOrDigit), surahs(it))
    }.values.flatMap { matches ->
        // Keep every downloaded identity accessible to offline management.
        val saved = matches.filter { id(it) == selectedId || id(it) in downloadedIds }
        saved.ifEmpty { listOf(matches.first()) }
    }
}
