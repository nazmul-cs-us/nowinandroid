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

data class ReciterPerson<T>(val key: String, val name: String, val recordings: List<T>)

/** One visible person, with every recording kept under the original stable ID. */
fun <T> groupReciters(entries: List<T>, id: (T) -> String, name: (T) -> String): List<ReciterPerson<T>> =
    entries.distinctBy(id).groupBy { reciterPersonKey(name(it)) }.map { (key, recordings) ->
        ReciterPerson(key, com.starception.submission.core.images.reciterDisplayName(name(recordings.first())), recordings)
    }

fun reciterRecordingLabel(style: String): String = style.trim().removePrefix("Rewayat ")
    .replace("Almushaf Al Mojawwad", "Mujawwad", ignoreCase = true)
    .replace("Almusshaf Al Mojawwad", "Mujawwad", ignoreCase = true)
    .ifBlank { "Arabic recitation" }

/** Catalog coverage, independent of the number of files already downloaded on this device. */
fun hasCompleteQuran(surahs: Set<Int>): Boolean = (1..114).all { it in surahs }
