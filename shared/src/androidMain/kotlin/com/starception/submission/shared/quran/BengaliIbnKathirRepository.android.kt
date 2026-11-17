package com.starception.submission.shared.quran

import com.starception.submission.feature.quran.BengaliIbnKathirRepository

// Android hosts its native SurahDetailScreen, whose context-backed factory uses the same corpus/cache.
internal actual fun createBengaliIbnKathirRepository(): BengaliIbnKathirRepository =
    BengaliIbnKathirRepository { error("Use the native Android tafsir repository") }
