package com.starception.submission.core.model.data

/** Shared index fields used by downloadable Hadith collection book browsers. */
interface HadithCollectionBook {
    val id: Int
    val nameEnglish: String
    val nameSecondary: String
    val firstHadithId: Int
    val lastHadithId: Int
    val hadithCount: Int
}
