package com.starception.submission.shared.media

/**
 * Supplemental Quran audio sources. A source is only exposed after its actual
 * Archive metadata confirms an audio file for the requested surah.
 *
 * This does not assume 001.mp3..114.mp3 exist, nor that Archive uploads
 * grant redistribution rights.
 */
object InternetArchiveQuranSource {
    private val audioExtensions = setOf("mp3", "ogg", "flac", "m4a")

    data class Track(
        val archiveIdentifier: String,
        val filename: String,
        val surahNumber: Int,
        val downloadUrl: String
    )

    /** Parse an Archive item file listing after fetching /metadata/{identifier}. */
    fun tracks(
        identifier: String,
        filenames: List<String>
    ): List<Track> {
        require(identifier.matches(Regex("[A-Za-z0-9_.-]+"))) {
            "Invalid Internet Archive identifier"
        }
        return filenames.mapNotNull { filename ->
            val extension = filename.substringAfterLast('.', "").lowercase()
            if (extension !in audioExtensions) return@mapNotNull null
            val base = filename.substringAfterLast('/').substringBeforeLast('.')
            // Only unambiguous 001-114 file names are automatically mapped.
            val number = base.toIntOrNull() ?: return@mapNotNull null
            if (number !in 1..114) return@mapNotNull null
            Track(
                archiveIdentifier = identifier,
                filename = filename,
                surahNumber = number,
                downloadUrl = "https://archive.org/download/" +
                    encodePathSegment(identifier) + "/" +
                    filename.split('/').joinToString("/") { encodePathSegment(it) }
            )
        }.distinctBy { it.surahNumber }
            .sortedBy { it.surahNumber }
    }

    private fun encodePathSegment(value: String): String =
        value.encodeToByteArray().joinToString("") { byte ->
            val n = byte.toInt() and 0xff
            if ((n in 65..90) || (n in 97..122) ||
                (n in 48..57) || n == 45 || n == 46 ||
                n == 95 || n == 126) n.toChar().toString()
            else "%" + n.toString(16).uppercase().padStart(2, '0')
        }
}
