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

/** A canonical book/chapter in Shama'il At-Tirmidhi. */
data class ShamayelBook(
    override val id: Int,
    override val nameEnglish: String,
    val nameBengali: String,
    override val firstHadithId: Int,
    override val lastHadithId: Int,
    override val hadithCount: Int,
) : HadithCollectionBook {
    override val nameSecondary: String get() = nameBengali
}

/** The 56 books and their exact ranges in shamayele_tirmidhi_complete.db. */
object ShamayelBooks {
    val all: List<ShamayelBook> = listOf(
        ShamayelBook(1, "Physical Description of Rasoolullah (ﷺ)", "রাসূলুল্লাহ (সাঃ) এর দৈহিক গঠন", 1, 11, 11),
        ShamayelBook(2, "Seal of Prophethood (Khatam an-Nabuwwah)", "রাসুলুল্লাহ (সাঃ) এর মোহরে নবুওয়াত", 12, 18, 7),
        ShamayelBook(3, "Hair of Rasoolullah (ﷺ)", "রাসূলুল্লাহ (সাঃ) এর চুল", 19, 26, 8),
        ShamayelBook(4, "Combing the Hair of Rasoolullah (ﷺ)", "রাসূলুল্লাহ (সাঃ) এর মাথার চুল বিন্যাস করা", 27, 29, 3),
        ShamayelBook(5, "Gray Hair / Aging of Rasoolullah (ﷺ)", "রাসূলুল্লাহ (সাঃ) এর বার্ধক্য", 30, 37, 8),
        ShamayelBook(6, "Use of Henna / Dye by Rasoolullah (ﷺ)", "রাসূলুল্লাহ (সাঃ) এর খিযাব লাগানো", 38, 40, 3),
        ShamayelBook(7, "Use of Kohl (Kuhl) by Rasoolullah (ﷺ)", "রাসূলুল্লাহ (সাঃ) এর সুরমা ব্যবহার", 41, 43, 3),
        ShamayelBook(8, "Clothing and Dress of Rasoolullah (ﷺ)", "রাসূলুল্লাহ (সাঃ)-এর পোশাক-পরিচ্ছদ", 44, 55, 12),
        ShamayelBook(9, "Living and Lifestyle of Rasoolullah (ﷺ)", "রাসূলুল্লাহ (সাঃ) এর জীবন-যাপন", 56, 57, 2),
        ShamayelBook(10, "Leather Socks (Khuffain) of Rasoolullah (ﷺ)", "রাসূলুল্লাহ (সাঃ) এর মোজা ব্যবহার", 58, 58, 1),
        ShamayelBook(11, "Sandals and Footwear of Rasoolullah (ﷺ)", "রাসূলুল্লাহ (সাঃ) এর জুতা", 59, 68, 10),
        ShamayelBook(12, "Signet Ring of Rasoolullah (ﷺ)", "রাসূলুল্লাহ (সাঃ) এর আংটির বিবরণ", 69, 74, 6),
        ShamayelBook(13, "Wearing Ring on the Right Hand", "নবী (সাঃ) ডান হাতে আংটি পরিধান করতেন", 75, 80, 6),
        ShamayelBook(14, "Sword of Rasoolullah (ﷺ)", "রাসূলুল্লাহ (সাঃ) এর তরবারির বিবরণ", 81, 81, 1),
        ShamayelBook(15, "Armor and Battle Dress of Rasoolullah (ﷺ)", "রাসূলুল্লাহ (সাঃ) এর যুদ্ধের পোশাকের বিবরণ", 82, 83, 2),
        ShamayelBook(16, "Helmet (Mighfar) of Rasoolullah (ﷺ)", "রাসূলুল্লাহ (সাঃ) এর হেলমেট (শিরস্ত্রাণ) এর বিবরণ", 84, 85, 2),
        ShamayelBook(17, "Turban (Imamah) of Rasoolullah (ﷺ)", "নবী (সাঃ) এর পাগড়ি", 86, 89, 4),
        ShamayelBook(18, "Waist Wrapper (Izar) of Rasoolullah (ﷺ)", "রাসূলুল্লাহ (সাঃ) এর লুঙ্গির বিবরণ", 90, 92, 3),
        ShamayelBook(19, "Gait and Walking of Rasoolullah (ﷺ)", "রাসূলুল্লাহ (সাঃ) এর হাঁটা-চলা", 93, 94, 2),
        ShamayelBook(20, "Head Covering of Rasoolullah (ﷺ)", "রাসূলুল্লাহ (সাঃ) এর মস্তকাবরণ ব্যবহার", 95, 95, 1),
        ShamayelBook(21, "Manner of Sitting of Rasoolullah (ﷺ)", "রাসূলুল্লাহ (সাঃ) এর উঠা-বসা", 96, 97, 2),
        ShamayelBook(22, "Reclining on a Pillow", "রাসূলুল্লাহ (সাঃ) এর বালিশে হেলান দেয়ার বিবরণ", 98, 101, 4),
        ShamayelBook(23, "Reclining and Leaning", "রাসূলুল্লাহ (সাঃ)-এর ঠেস দেয়া", 102, 102, 1),
        ShamayelBook(24, "Manner of Eating of Rasoolullah (ﷺ)", "রাসূলুল্লাহ (সাঃ) এর পানাহারের নিয়ম পদ্ধতি", 103, 106, 4),
        ShamayelBook(25, "Bread of Rasoolullah (ﷺ)", "রাসূলুল্লাহ (সাঃ) এর রুটির বিবরণ", 107, 112, 6),
        ShamayelBook(26, "Curry and Condiments of Rasoolullah (ﷺ)", "রাসূলুল্লাহ (সাঃ) এর তরকারীর বর্ণনা", 113, 136, 24),
        ShamayelBook(27, "Wudu Before and After Eating", "আহার গ্রহণকালে রাসূলুল্লাহ (সাঃ) এর ওযূ", 137, 138, 2),
        ShamayelBook(28, "Supplications Before and After Meals", "খাওয়ার পূর্বে ও পরে রাসূলুল্লাহ (সাঃ) এর দুআ", 139, 143, 5),
        ShamayelBook(29, "Drinking Cup of Rasoolullah (ﷺ)", "রাসূলুল্লাহ (সাঃ) এর পানপাত্র", 144, 145, 2),
        ShamayelBook(30, "Fruits Consumed by Rasoolullah (ﷺ)", "রাসূলুল্লাহ (সাঃ) এর ফলমূলের বিবরণ", 146, 149, 4),
        ShamayelBook(31, "Drinks Consumed by Rasoolullah (ﷺ)", "রাসূলুল্লাহ (সাঃ) এর পানীয় বস্তুর বিবরণ", 150, 151, 2),
        ShamayelBook(32, "Manner of Drinking of Rasoolullah (ﷺ)", "রাসূলুল্লাহ (সাঃ) এর পান করার পদ্ধতি", 152, 160, 9),
        ShamayelBook(33, "Use of Perfume (Itar) by Rasoolullah (ﷺ)", "রাসূলুল্লাহ (সাঃ) এর সুগদ্ধি ব্যবহার", 161, 164, 4),
        ShamayelBook(34, "Speech and Manner of Speaking of Rasoolullah (ﷺ)", "রাসূলুল্লাহ (সাঃ) এর বাচনভঙ্গি", 165, 167, 3),
        ShamayelBook(35, "Laughter and Smile of Rasoolullah (ﷺ)", "রাসূলুল্লাহ (সাঃ) এর হাসি", 168, 173, 6),
        ShamayelBook(36, "Humor and Joking of Rasoolullah (ﷺ)", "রাসূলুল্লাহ (সাঃ) এর কৌতুক", 174, 179, 6),
        ShamayelBook(37, "Recitation of Poetry by Rasoolullah (ﷺ)", "কাব্যিক ছন্দে রাসূলুল্লাহ (সাঃ) এর কথা", 180, 187, 8),
        ShamayelBook(38, "Nighttime Conversations and Stories", "রাসূলুল্লাহ (সাঃ) এর রাত্রে গল্প বলা", 188, 188, 1),
        ShamayelBook(39, "Sleep Routine and Supplications of Rasoolullah (ﷺ)", "রাসূলুল্লাহ (সাঃ) এর নিদ্রা", 189, 194, 6),
        ShamayelBook(40, "Worship and Night Prayers of Rasoolullah (ﷺ)", "রাসূল (সাঃ) এর ইবাদাত", 195, 215, 21),
        ShamayelBook(41, "Duha (Forenoon) Prayer of Rasoolullah (ﷺ)", "রাসূলুল্লাহ (সাঃ) এর দ্বোহার সালাত", 216, 222, 7),
        ShamayelBook(42, "Voluntary (Nawafil) Prayers at Home", "ঘরে নফল সালাত", 223, 223, 1),
        ShamayelBook(43, "Fasting Routine of Rasoolullah (ﷺ)", "রাসূলুল্লাহ (সাঃ) এর রোযা", 224, 239, 16),
        ShamayelBook(44, "Quranic Recitation of Rasoolullah (ﷺ)", "রাসূলুল্লাহ (সাঃ) এর কিরাআত", 240, 246, 7),
        ShamayelBook(45, "Weeping and Tears of Rasoolullah (ﷺ)", "রাসূলুল্লাহ (সাঃ) এর ক্ৰন্দন", 247, 252, 6),
        ShamayelBook(46, "Bed and Bedding of Rasoolullah (ﷺ)", "রাসূলুল্লাহ (সাঃ) এর বিছানা", 253, 253, 1),
        ShamayelBook(47, "Humility and Modesty of Rasoolullah (ﷺ)", "রাসূলুল্লাহ (সাঃ) এর বিনয়", 254, 264, 11),
        ShamayelBook(48, "Sublime Character and Noble Disposition", "রাসূলুল্লাহ (সাঃ) এর চরিত্র (মাধুর্য)", 265, 276, 12),
        ShamayelBook(49, "Modesty and Shyness of Rasoolullah (ﷺ)", "রাসূলুল্লাহ (সাঃ) এর লজ্জাবোধ", 277, 277, 1),
        ShamayelBook(50, "Cupping (Hijama) Practices of Rasoolullah (ﷺ)", "রাসূলুল্লাহ (সাঃ) এর শিঙ্গা লাগানো", 278, 283, 6),
        ShamayelBook(51, "Blessed Names of Rasoolullah (ﷺ)", "রাসূলুল্লাহ (সাঃ) এর নাম", 284, 285, 2),
        ShamayelBook(52, "Livelihood and Subsistence of Rasoolullah (ﷺ)", "রাসূলুল্লাহ (সাঃ) এর জীবিকা", 286, 291, 6),
        ShamayelBook(53, "Blessed Age of Rasoolullah (ﷺ)", "রাসূলুল্লাহ (সাঃ) এর বয়স", 292, 296, 5),
        ShamayelBook(54, "Passing Away (Wafat) of Rasoolullah (ﷺ)", "রাসূলুল্লাহ (সাঃ) এর ওফাত", 297, 307, 11),
        ShamayelBook(55, "Estate and Inheritance of Rasoolullah (ﷺ)", "রাসূলুল্লাহ (সাঃ) এর মীরাস", 308, 314, 7),
        ShamayelBook(56, "Seeing Rasoolullah (ﷺ) in a Dream", "রাসূলুল্লাহ (সাঃ) কে স্বপ্নযোগে দর্শন", 315, 322, 8),
    )

    init {
        check(all.size == 56)
        check(all.sumOf(ShamayelBook::hadithCount) == 322)
        check(all.first().firstHadithId == 1 && all.last().lastHadithId == 322)
        check(all.zipWithNext().all { (current, next) -> current.lastHadithId + 1 == next.firstHadithId })
        check(all.all { it.lastHadithId - it.firstHadithId + 1 == it.hadithCount })
    }

    fun find(id: Int): ShamayelBook? = all.firstOrNull { it.id == id }

    fun findByHadithId(hadithId: Int): ShamayelBook? =
        all.firstOrNull { hadithId in it.firstHadithId..it.lastHadithId }
}
