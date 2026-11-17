package com.starception.submission.shared.quran

import com.starception.submission.feature.quran.PublishedTafsirEdition
import com.starception.submission.feature.quran.PublishedTafsirRepository

// Android hosts its native SurahDetailScreen, whose context-backed factory uses the same corpus/cache.
internal actual fun createPublishedTafsirRepository(edition: PublishedTafsirEdition): PublishedTafsirRepository =
    PublishedTafsirRepository { error("Use the native Android tafsir repository") }
