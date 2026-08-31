package com.starception.submission.shared.quran

actual fun createQuranVerseRepository(): QuranVerseRepository = AndroidQuranVerseRepository

private object AndroidQuranVerseRepository : QuranVerseRepository {
    override suspend fun getVersesBySurah(surahNumber: Int): List<QuranVerse> {
        error("The shared Quran database is packaged by the iOS host only")
    }
}
