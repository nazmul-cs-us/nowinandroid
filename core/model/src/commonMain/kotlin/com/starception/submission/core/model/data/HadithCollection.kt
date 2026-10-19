/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.starception.submission.core.model.data

/** A canonical book/chapter entry for a downloadable hadith collection. */
data class HadithCollectionBookEntry(
    override val id: Int,
    override val nameEnglish: String,
    val nameArabic: String,
    override val firstHadithId: Int,
    override val lastHadithId: Int,
    override val hadithCount: Int,
) : HadithCollectionBook {
    override val nameSecondary: String get() = nameArabic
}

/**
 * One downloadable hadith collection: identity, database file, download
 * category, and — when a structured source exists — its canonical books with
 * the exact id ranges used by [com.starception.submission.core.hadithdatabase]'s
 * browsing queries (`getHadithsInRange`).
 *
 * Collections whose database edition has no matching structured source yet
 * carry an empty [books] list and fall back to flat browsing.
 */
data class HadithCollection(
    val name: String,
    val nameArabic: String,
    val author: String,
    val databaseFile: String,
    val downloadCategory: String,
    val hadithCount: Int,
    val books: List<HadithCollectionBook>,
) {
    val hasBooks: Boolean get() = books.isNotEmpty()
}

/** The registry of every hadith collection the app can download and browse. */
object HadithCollections {
    private val bukhari = HadithCollection(
        name = "Sahih al-Bukhari",
        nameArabic = "صحيح البخاري",
        author = "Imam Muhammad ibn Ismail al-Bukhari",
        databaseFile = "sahih_bukhari.db",
        downloadCategory = "hadith_sahih_bukhari",
        hadithCount = 7277,
        books = BukhariBooks.all,
    )

    private val muslim = HadithCollection(
        name = "Sahih Muslim",
        nameArabic = "صحيح مسلم",
        author = "Imam Muslim ibn al-Hajjaj al-Naysaburi",
        databaseFile = "sahih_muslim.db",
        downloadCategory = "hadith_sahih_muslim",
        hadithCount = 7459,
        books = MuslimBooks.all,
    )

    private val tirmidhi = HadithCollection(
        name = "Jami' at-Tirmidhi",
        nameArabic = "جامع الترمذي",
        author = "Imam Abu Isa Muhammad ibn Isa al-Tirmidhi",
        databaseFile = "sunan_tirmidhi.db",
        downloadCategory = "hadith_sunan_tirmidhi",
        hadithCount = 4053,
        books = TirmidhiBooks.all,
    )

    private val shamayel = HadithCollection(
        name = "Shamai'l At-Tirmidhi",
        nameArabic = "الشمائل المحمدية",
        author = "Imam Muhammad bin Isa At-Tirmidhi",
        databaseFile = "shamayele_tirmidhi_complete.db",
        downloadCategory = "hadith_shamayele_tirmidhi",
        hadithCount = 322,
        books = ShamayelBooks.all,
    )

    private val abuDawud = HadithCollection(
        name = "Sunan Abu Dawud",
        nameArabic = "سنن أبي داود",
        author = "Imam Sulayman ibn al-Ash'ath Abu Dawud al-Sijistani",
        databaseFile = "sunan_abu_dawud.db",
        downloadCategory = "hadith_sunan_abu_dawud",
        hadithCount = 5276,
        books = AbuDawudBooks.all,
    )

    private val nasai = HadithCollection(
        name = "Sunan an-Nasa'i",
        nameArabic = "سنن النسائي",
        author = "Imam Ahmad ibn Shu'ayb al-Nasa'i",
        databaseFile = "sunan_nasai.db",
        downloadCategory = "hadith_sunan_nasai",
        hadithCount = 5768,
        books = NasaiBooks.all,
    )

    private val ibnMajah = HadithCollection(
        name = "Sunan Ibn Majah",
        nameArabic = "سنن ابن ماجه",
        author = "Imam Muhammad ibn Yazid Ibn Majah al-Qazwini",
        databaseFile = "sunan_ibn_majah.db",
        downloadCategory = "hadith_sunan_ibn_majah",
        hadithCount = 4345,
        books = IbnMajahBooks.all,
    )

    private val malik = HadithCollection(
        name = "Muwatta Malik",
        nameArabic = "موطأ مالك",
        author = "Imam Malik ibn Anas",
        databaseFile = "muwatta_malik.db",
        downloadCategory = "hadith_muwatta_malik",
        hadithCount = 1985,
        books = MalikBooks.all,
    )

    /**
     * Bilingual (Arabic + English) corpus crawled from en.tohed.com — the only
     * source with a complete human-quality English Sunan ad-Darimi. See
     * scripts/fetch_tohed_collection.py + tohed_jsonl_to_hadith_json.py; the
     * database and these ranges follow the crawl's own numbering (3,534 rows;
     * the site skips one number mid-run).
     */
    private val darimi = HadithCollection(
        name = "Sunan ad-Darimi",
        nameArabic = "سنن الدارمي",
        author = "Imam al-Darimi",
        databaseFile = "sunan_darimi.db",
        downloadCategory = "hadith_sunan_darimi",
        hadithCount = 3534,
        books = DarimiBooks.all,
    )

    /**
     * Bilingual (Arabic + English) corpus crawled from en.tohed.com — the only
     * complete English Musnad Ahmad in existence (sunnah.com's translation
     * covers ~4%). See scripts/fetch_tohed_collection.py +
     * tohed_jsonl_to_hadith_json.py; the database and these ranges follow the
     * crawl's own numbering (27,642 rows; the site skips five numbers) and
     * the books are the traditional narrator sections of the Musnad.
     */
    private val ahmad = HadithCollection(
        name = "Musnad Ahmad",
        nameArabic = "مسند أحمد",
        author = "Imam Ahmad ibn Hanbal",
        databaseFile = "musnad_ahmad.db",
        downloadCategory = "hadith_musnad_ahmad",
        hadithCount = 27642,
        books = AhmadBooks.all,
    )

    /** Same order as HadithDatabase's collectionToFile registry. */
    val all: List<HadithCollection> = listOf(
        bukhari,
        muslim,
        tirmidhi,
        shamayel,
        abuDawud,
        nasai,
        ibnMajah,
        malik,
        ahmad,
        darimi,
    )

    fun byDatabaseFile(databaseFile: String): HadithCollection? =
        all.firstOrNull { it.databaseFile == databaseFile }

    /**
     * Lower-cased name tokens with Arabic/English articles removed, so
     * "Sahih Bukhari" (the topic row) matches "Sahih al-Bukhari" (the registry).
     */
    private fun nameTokens(value: String): List<String> =
        value.lowercase()
            .split(Regex("[^\\p{L}\\p{N}']+"))
            .filter { it.isNotEmpty() && it !in setOf("al", "an", "ad", "as", "at", "the") }

    /** Matches display names, topic names ("Sahih Bukhari"), and partial names. */
    fun byName(name: String): HadithCollection? {
        if (name.isBlank()) return null
        val needle = name.trim()
        val query = nameTokens(needle)
        if (query.isNotEmpty()) {
            all.firstOrNull { nameTokens(it.name) == query }?.let { return it }
            all.firstOrNull { collection ->
                val tokens = nameTokens(collection.name)
                tokens.isNotEmpty() &&
                    (tokens.all(query::contains) || query.all(tokens::contains))
            }?.let { return it }
        }
        return all.firstOrNull { it.nameArabic == needle }
    }

    /** The download category key in assets/manifest.json (e.g. "hadith_sahih_muslim"). */
    fun downloadCategoryFor(databaseFile: String): String =
        byDatabaseFile(databaseFile)?.downloadCategory
            ?: "hadith_${databaseFile.removeSuffix(".db")}"
}
