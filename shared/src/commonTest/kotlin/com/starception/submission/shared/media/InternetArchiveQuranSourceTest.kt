package com.starception.submission.shared.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InternetArchiveQuranSourceTest {
    @Test
    fun mapsOnlyValidSurahAudioFiles() {
        val tracks = InternetArchiveQuranSource.tracks(
            "quran-recordings",
            listOf("114.mp3", "001.mp3", "115.mp3", "cover.jpg", "notes.txt", "001.ogg")
        )
        assertEquals(listOf(1, 114), tracks.map { it.surahNumber })
        assertEquals(
            "https://archive.org/download/quran-recordings/001.mp3",
            tracks.first().downloadUrl
        )
    }

    @Test
    fun encodesNestedFilenames() {
        val tracks = InternetArchiveQuranSource.tracks(
            "quran-recordings",
            listOf("Recitations/002.mp3")
        )
        assertEquals(
            "https://archive.org/download/quran-recordings/Recitations/002.mp3",
            tracks.single().downloadUrl
        )
    }

    @Test
    fun rejectsInvalidIdentifier() {
        assertFailsWith<IllegalArgumentException> {
            InternetArchiveQuranSource.tracks("../invalid", listOf("001.mp3"))
        }
    }
}
