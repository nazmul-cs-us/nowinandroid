package com.starception.submission.shared.quran

import com.starception.submission.feature.quran.*

internal expect fun createPublishedTafsirRepository(edition: PublishedTafsirEdition): PublishedTafsirRepository
internal fun createBengaliIbnKathirRepository(): BengaliIbnKathirRepository =
    createPublishedTafsirRepository(PublishedTafsirs.bengali)
