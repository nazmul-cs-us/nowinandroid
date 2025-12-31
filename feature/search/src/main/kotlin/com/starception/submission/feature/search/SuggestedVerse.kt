/*
 * Copyright 2024 The Android Open Source Project
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

package com.starception.submission.feature.search

/**
 * Represents a suggested verse for quick access in search.
 * These are popular/important ayahs that users frequently search for.
 */
data class SuggestedVerse(
    val name: String,
    val arabicName: String,
    val surahNumber: Int,
    val ayahNumber: Int,
    val description: String,
    val category: String
)

/**
 * Predefined list of suggested verses for user convenience.
 * Curated from most searched and popular Quran verses.
 */
object SuggestedVerses {
    val verses = listOf(
        // ==================== MOST POPULAR ====================
        SuggestedVerse(
            name = "Ayatul Kursi",
            arabicName = "آية الكرسي",
            surahNumber = 2,
            ayahNumber = 255,
            description = "The Throne Verse - Greatest verse in the Quran",
            category = "Protection"
        ),
        SuggestedVerse(
            name = "Al-Fatiha",
            arabicName = "الفاتحة",
            surahNumber = 1,
            ayahNumber = 1,
            description = "The Opening - Essential prayer surah",
            category = "Prayer"
        ),

        // ==================== LAST 3 SURAHS (MU'AWWIDHAT) ====================
        SuggestedVerse(
            name = "Al-Ikhlas",
            arabicName = "الإخلاص",
            surahNumber = 112,
            ayahNumber = 1,
            description = "The Sincerity - Equal to 1/3 of Quran",
            category = "Tawheed"
        ),
        SuggestedVerse(
            name = "Al-Falaq",
            arabicName = "الفلق",
            surahNumber = 113,
            ayahNumber = 1,
            description = "The Daybreak - Protection from evil",
            category = "Protection"
        ),
        SuggestedVerse(
            name = "An-Nas",
            arabicName = "الناس",
            surahNumber = 114,
            ayahNumber = 1,
            description = "Mankind - Protection from whispers",
            category = "Protection"
        ),

        // ==================== COMFORT & EASE ====================
        SuggestedVerse(
            name = "With Hardship Comes Ease",
            arabicName = "إن مع العسر يسرا",
            surahNumber = 94,
            ayahNumber = 5,
            description = "Verily, with hardship comes ease",
            category = "Comfort"
        ),
        SuggestedVerse(
            name = "Allah Does Not Burden",
            arabicName = "لا يكلف الله نفسا",
            surahNumber = 2,
            ayahNumber = 286,
            description = "Allah does not burden a soul beyond its capacity",
            category = "Comfort"
        ),
        SuggestedVerse(
            name = "Hearts Find Peace",
            arabicName = "ألا بذكر الله",
            surahNumber = 13,
            ayahNumber = 28,
            description = "Verily, in the remembrance of Allah do hearts find rest",
            category = "Peace"
        ),
        SuggestedVerse(
            name = "Quran as Healing",
            arabicName = "شفاء ورحمة",
            surahNumber = 17,
            ayahNumber = 82,
            description = "We send down the Quran as healing and mercy",
            category = "Healing"
        ),
        SuggestedVerse(
            name = "Allah Will Not Forsake",
            arabicName = "ما ودعك ربك",
            surahNumber = 93,
            ayahNumber = 3,
            description = "Your Lord has not forsaken you nor is He displeased",
            category = "Comfort"
        ),

        // ==================== MERCY & FORGIVENESS ====================
        SuggestedVerse(
            name = "Do Not Despair of Mercy",
            arabicName = "لا تقنطوا من رحمة الله",
            surahNumber = 39,
            ayahNumber = 53,
            description = "Do not despair of Allah's mercy - He forgives all sins",
            category = "Mercy"
        ),
        SuggestedVerse(
            name = "Mercy Encompasses All",
            arabicName = "رحمتي وسعت",
            surahNumber = 7,
            ayahNumber = 156,
            description = "My mercy encompasses all things",
            category = "Mercy"
        ),
        SuggestedVerse(
            name = "Peace Upon Repentant",
            arabicName = "سلام عليكم",
            surahNumber = 6,
            ayahNumber = 54,
            description = "Peace be upon you - Lord has decreed mercy upon Himself",
            category = "Mercy"
        ),
        SuggestedVerse(
            name = "Allah Pardons Much",
            arabicName = "ويعفو عن كثير",
            surahNumber = 42,
            ayahNumber = 30,
            description = "Whatever misfortune befalls you is from yourself, and He pardons much",
            category = "Forgiveness"
        ),

        // ==================== PATIENCE & PERSEVERANCE ====================
        SuggestedVerse(
            name = "Allah With The Patient",
            arabicName = "إن الله مع الصابرين",
            surahNumber = 2,
            ayahNumber = 153,
            description = "Indeed, Allah is with the patient",
            category = "Patience"
        ),
        SuggestedVerse(
            name = "Blessings on Patient",
            arabicName = "صلوات من ربهم",
            surahNumber = 2,
            ayahNumber = 157,
            description = "Upon them are blessings and mercy from their Lord",
            category = "Patience"
        ),
        SuggestedVerse(
            name = "Unlimited Reward",
            arabicName = "أجرهم بغير حساب",
            surahNumber = 39,
            ayahNumber = 10,
            description = "The patient will be given their reward without account",
            category = "Patience"
        ),
        SuggestedVerse(
            name = "Divine Help Is Near",
            arabicName = "إن نصر الله قريب",
            surahNumber = 2,
            ayahNumber = 214,
            description = "Indeed, the help of Allah is near",
            category = "Hope"
        ),

        // ==================== TRUST & RELIANCE ====================
        SuggestedVerse(
            name = "Allah Is Sufficient",
            arabicName = "فهو حسبه",
            surahNumber = 65,
            ayahNumber = 3,
            description = "Whoever relies upon Allah - He is sufficient for him",
            category = "Trust"
        ),
        SuggestedVerse(
            name = "Don't Despair of Relief",
            arabicName = "لا تيأسوا من روح الله",
            surahNumber = 12,
            ayahNumber = 87,
            description = "Do not despair of relief from Allah",
            category = "Hope"
        ),
        SuggestedVerse(
            name = "Success From Allah",
            arabicName = "وما توفيقي إلا بالله",
            surahNumber = 11,
            ayahNumber = 88,
            description = "My success is only through Allah",
            category = "Trust"
        ),

        // ==================== DIVINE RESPONSE ====================
        SuggestedVerse(
            name = "Allah Responds to Dua",
            arabicName = "أجيب دعوة الداع",
            surahNumber = 2,
            ayahNumber = 186,
            description = "I am near. I respond to the invocation of the supplicant",
            category = "Dua"
        ),
        SuggestedVerse(
            name = "Call Upon Me",
            arabicName = "ادعوني أستجب لكم",
            surahNumber = 40,
            ayahNumber = 60,
            description = "Call upon Me; I will respond to you",
            category = "Dua"
        ),
        SuggestedVerse(
            name = "Allah Is With You",
            arabicName = "إني معكما",
            surahNumber = 20,
            ayahNumber = 46,
            description = "Fear not. Indeed, I am with you both",
            category = "Comfort"
        ),

        // ==================== GRATITUDE ====================
        SuggestedVerse(
            name = "Gratitude Increases",
            arabicName = "لئن شكرتم لأزيدنكم",
            surahNumber = 14,
            ayahNumber = 7,
            description = "If you are grateful, I will surely increase you",
            category = "Gratitude"
        ),

        // ==================== PERSONAL CHANGE ====================
        SuggestedVerse(
            name = "Change Within Yourself",
            arabicName = "حتى يغيروا ما بأنفسهم",
            surahNumber = 13,
            ayahNumber = 11,
            description = "Allah will not change a people until they change themselves",
            category = "Change"
        ),

        // ==================== IMPORTANT SURAHS ====================
        SuggestedVerse(
            name = "Verse of Light",
            arabicName = "آية النور",
            surahNumber = 24,
            ayahNumber = 35,
            description = "Allah is the Light of the heavens and earth",
            category = "Reflection"
        ),
        SuggestedVerse(
            name = "Last 2 Ayahs of Baqarah",
            arabicName = "خواتيم البقرة",
            surahNumber = 2,
            ayahNumber = 285,
            description = "Protection when recited at night",
            category = "Protection"
        ),
        SuggestedVerse(
            name = "Surah Al-Mulk",
            arabicName = "الملك",
            surahNumber = 67,
            ayahNumber = 1,
            description = "The Sovereignty - Protection in grave",
            category = "Protection"
        ),
        SuggestedVerse(
            name = "Surah Yasin",
            arabicName = "يس",
            surahNumber = 36,
            ayahNumber = 1,
            description = "The Heart of the Quran",
            category = "Blessings"
        ),
        SuggestedVerse(
            name = "Surah Ar-Rahman",
            arabicName = "الرحمن",
            surahNumber = 55,
            ayahNumber = 1,
            description = "The Most Merciful - Beauty of Allah's blessings",
            category = "Gratitude"
        ),
        SuggestedVerse(
            name = "Surah Al-Kahf",
            arabicName = "الكهف",
            surahNumber = 18,
            ayahNumber = 1,
            description = "The Cave - Protection from Dajjal (Friday)",
            category = "Friday"
        ),
        SuggestedVerse(
            name = "Surah Al-Waqiah",
            arabicName = "الواقعة",
            surahNumber = 56,
            ayahNumber = 1,
            description = "Protection from poverty when recited daily",
            category = "Provision"
        ),

        // ==================== PARADISE ====================
        SuggestedVerse(
            name = "The Reassured Soul",
            arabicName = "يا أيتها النفس المطمئنة",
            surahNumber = 89,
            ayahNumber = 27,
            description = "O reassured soul, return to your Lord well-pleased",
            category = "Paradise"
        ),
        SuggestedVerse(
            name = "Eternal Paradise",
            arabicName = "خالدين فيها",
            surahNumber = 2,
            ayahNumber = 82,
            description = "Those who believe shall be companions of Paradise",
            category = "Paradise"
        ),
        SuggestedVerse(
            name = "Two Gardens",
            arabicName = "جنتان",
            surahNumber = 55,
            ayahNumber = 46,
            description = "For those who fear their Lord are two gardens",
            category = "Paradise"
        ),

        // ==================== PROTECTION & STRENGTH ====================
        SuggestedVerse(
            name = "Allah Is Guardian",
            arabicName = "الله مولاكم",
            surahNumber = 3,
            ayahNumber = 150,
            description = "Allah is your protector, and He is the best of helpers",
            category = "Protection"
        ),
        SuggestedVerse(
            name = "Believers Prevail",
            arabicName = "أنتم الأعلون",
            surahNumber = 3,
            ayahNumber = 139,
            description = "Do not weaken or grieve - you will be superior",
            category = "Strength"
        ),
        SuggestedVerse(
            name = "Allah Protects Believers",
            arabicName = "الله مولى الذين آمنوا",
            surahNumber = 47,
            ayahNumber = 11,
            description = "Allah is the protector of those who believe",
            category = "Protection"
        ),

        // ==================== TRIALS & TESTS ====================
        SuggestedVerse(
            name = "Disliked May Be Good",
            arabicName = "عسى أن تكرهوا",
            surahNumber = 2,
            ayahNumber = 216,
            description = "Perhaps you hate something that is good for you",
            category = "Wisdom"
        ),
        SuggestedVerse(
            name = "Testing Through All",
            arabicName = "نبلوكم بالشر والخير",
            surahNumber = 21,
            ayahNumber = 35,
            description = "We test you with evil and good as trial",
            category = "Tests"
        ),

        // ==================== GOOD DEEDS ====================
        SuggestedVerse(
            name = "Good Deeds With Allah",
            arabicName = "تجدوه عند الله",
            surahNumber = 2,
            ayahNumber = 110,
            description = "Whatever good you send forth, you will find it with Allah",
            category = "Deeds"
        ),
        SuggestedVerse(
            name = "Reward Not Wasted",
            arabicName = "لا يضيع أجر المحسنين",
            surahNumber = 11,
            ayahNumber = 115,
            description = "Allah does not waste the reward of those who do good",
            category = "Deeds"
        ),

        // ==================== MORNING & EVENING ====================
        SuggestedVerse(
            name = "Morning Remembrance",
            arabicName = "أذكار الصباح",
            surahNumber = 3,
            ayahNumber = 26,
            description = "Say: O Allah, Owner of Sovereignty...",
            category = "Dhikr"
        ),

        // ==================== PROVISION ====================
        SuggestedVerse(
            name = "Allah Provides",
            arabicName = "ويرزقه من حيث لا يحتسب",
            surahNumber = 65,
            ayahNumber = 3,
            description = "He will provide for him from where he does not expect",
            category = "Provision"
        ),

        // ==================== TAWBAH (REPENTANCE) ====================
        SuggestedVerse(
            name = "Allah Loves Repentant",
            arabicName = "يحب التوابين",
            surahNumber = 2,
            ayahNumber = 222,
            description = "Indeed, Allah loves those who repent constantly",
            category = "Repentance"
        ),

        // ==================== FEAR OF ALLAH ====================
        SuggestedVerse(
            name = "Taqwa Brings Relief",
            arabicName = "ومن يتق الله",
            surahNumber = 65,
            ayahNumber = 2,
            description = "Whoever fears Allah - He will make a way out for him",
            category = "Taqwa"
        ),
    )

    /**
     * Filter verses by search query
     */
    fun search(query: String): List<SuggestedVerse> {
        if (query.isBlank()) return emptyList()
        val lowerQuery = query.lowercase()
        return verses.filter { verse ->
            verse.name.lowercase().contains(lowerQuery) ||
            verse.arabicName.contains(query) ||
            verse.description.lowercase().contains(lowerQuery) ||
            verse.category.lowercase().contains(lowerQuery) ||
            "${verse.surahNumber}:${verse.ayahNumber}".contains(query)
        }
    }
}
