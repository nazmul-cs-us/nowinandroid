package com.starception.submission.core.model.data

/** A canonical top-level book (Kitab) in Sahih al-Bukhari. */
data class BukhariBook(
    val id: Int,
    val nameEnglish: String,
    val nameArabic: String,
    val firstHadithId: Int,
    val lastHadithId: Int,
    val hadithCount: Int,
)

/**
 * The 97 canonical books in the same order and ID ranges as sahih_bukhari.db.
 *
 * Keeping this small index in code makes category browsing available before the optional
 * Bukhari database is downloaded. The hadith text itself remains in the downloadable database.
 */
object BukhariBooks {
    val all: List<BukhariBook> = listOf(
        BukhariBook(1, "Revelation", "كتاب بدء الوحى", 1, 7, 7),
        BukhariBook(2, "Belief", "كتاب الإيمان", 8, 58, 51),
        BukhariBook(3, "Knowledge", "كتاب العلم", 59, 134, 76),
        BukhariBook(4, "Ablutions (Wudu')", "كتاب الوضوء", 135, 247, 113),
        BukhariBook(5, "Bathing (Ghusl)", "كتاب الغسل", 248, 292, 45),
        BukhariBook(6, "Menstrual Periods", "كتاب الحيض", 293, 329, 37),
        BukhariBook(7, "Rubbing hands and feet with dust (Tayammum)", "كتاب التيمم", 330, 344, 15),
        BukhariBook(8, "Prayers (Salat)", "كتاب الصلاة", 345, 511, 167),
        BukhariBook(9, "Times of the Prayers", "كتاب مواقيت الصلاة", 512, 588, 77),
        BukhariBook(10, "Call to Prayers (Adhaan)", "كتاب الأذان", 589, 854, 266),
        BukhariBook(11, "Friday Prayer", "كتاب الجمعة", 855, 919, 65),
        BukhariBook(12, "Fear Prayer", "كتاب صلاة الخوف", 920, 925, 6),
        BukhariBook(13, "The Two Festivals (Eids)", "كتاب العيدين", 926, 962, 37),
        BukhariBook(14, "Witr Prayer", "كتاب الوتر", 963, 977, 15),
        BukhariBook(15, "Invoking Allah for Rain (Istisqaa)", "كتاب الاستسقاء", 978, 1011, 34),
        BukhariBook(16, "Eclipses", "كتاب الكسوف", 1012, 1035, 24),
        BukhariBook(17, "Prostration During Recital of Qur'an", "كتاب سجود القرآن", 1036, 1048, 13),
        BukhariBook(18, "Shortening the Prayers (At-Taqseer)", "كتاب التقصير", 1049, 1087, 39),
        BukhariBook(19, "Prayer at Night (Tahajjud)", "كتاب التهجد", 1088, 1150, 63),
        BukhariBook(20, "Virtues of Prayer at Masjid Makkah and Madinah", "كتاب فضل الصلاة فى مسجد مكة والمدينة", 1151, 1159, 9),
        BukhariBook(21, "Actions while Praying", "كتاب العمل فى الصلاة", 1160, 1186, 27),
        BukhariBook(22, "Forgetfulness in Prayer", "كتاب السهو", 1187, 1200, 14),
        BukhariBook(23, "Funerals (Al-Janaa'iz)", "كتاب الجنائز", 1201, 1348, 148),
        BukhariBook(24, "Obligatory Charity Tax (Zakat)", "كتاب الزكاة", 1349, 1460, 112),
        BukhariBook(25, "Hajj (Pilgrimage)", "كتاب الحج", 1461, 1707, 247),
        BukhariBook(26, "`Umrah (Minor pilgrimage)", "كتاب العمرة", 1708, 1737, 30),
        BukhariBook(27, "Pilgrims Prevented from Completing the Pilgrimage", "كتاب المحصر", 1738, 1752, 15),
        BukhariBook(28, "Penalty of Hunting while on Pilgrimage", "كتاب جزاء الصيد", 1753, 1798, 46),
        BukhariBook(29, "Virtues of Madinah", "كتاب فضائل المدينة", 1799, 1822, 24),
        BukhariBook(30, "Fasting", "كتاب الصوم", 1823, 1934, 112),
        BukhariBook(31, "Praying at Night in Ramadaan (Taraweeh)", "كتاب صلاة التراويح", 1935, 1940, 6),
        BukhariBook(32, "Virtues of the Night of Qadr", "كتاب فضل ليلة القدر", 1941, 1951, 11),
        BukhariBook(33, "Retiring to a Mosque for Remembrance of Allah (I'tikaf)", "كتاب الاعتكاف", 1952, 1972, 21),
        BukhariBook(34, "Sales and Trade", "كتاب البيوع", 1973, 2156, 184),
        BukhariBook(35, "Sales with Deferred Delivery (As-Salam)", "كتاب السلم", 2157, 2172, 16),
        BukhariBook(36, "Shuf'a", "كتاب الشفعة", 2173, 2175, 3),
        BukhariBook(37, "Hiring", "كتاب الإجارة", 2176, 2200, 25),
        BukhariBook(38, "Transfer of Debt (Al-Hawaala)", "كتاب الحوالات", 2201, 2203, 3),
        BukhariBook(39, "Kafalah", "كتاب الكفالة", 2204, 2212, 9),
        BukhariBook(40, "Representation and Business by Proxy", "كتاب الوكالة", 2213, 2230, 18),
        BukhariBook(41, "Agriculture", "كتاب المزارعة", 2231, 2258, 28),
        BukhariBook(42, "Distribution of Water", "كتاب المساقاة", 2259, 2289, 31),
        BukhariBook(43, "Loans, Property and Bankruptcy", "كتاب فى الاستقراض", 2290, 2313, 24),
        BukhariBook(44, "Disputes (Khusoomaat)", "كتاب الخصومات", 2314, 2328, 15),
        BukhariBook(45, "Lost Things (Luqatah)", "كتاب فى اللقطة", 2329, 2343, 15),
        BukhariBook(46, "Oppressions", "كتاب المظالم", 2344, 2386, 43),
        BukhariBook(47, "Partnership", "كتاب الشركة", 2387, 2408, 22),
        BukhariBook(48, "Mortgaging", "كتاب الرهن", 2409, 2416, 8),
        BukhariBook(49, "Manumission of Slaves", "كتاب العتق", 2417, 2457, 41),
        BukhariBook(50, "Makaatib", "كتاب المكاتب", 2458, 2463, 6),
        BukhariBook(51, "Gifts", "كتاب الهبة وفضلها والتحريض عليها", 2464, 2531, 68),
        BukhariBook(52, "Witnesses", "كتاب الشهادات", 2532, 2581, 50),
        BukhariBook(53, "Peacemaking", "كتاب الصلح", 2582, 2601, 20),
        BukhariBook(54, "Conditions", "كتاب الشروط", 2602, 2625, 24),
        BukhariBook(55, "Wills and Testaments (Wasaayaa)", "كتاب الوصايا", 2626, 2669, 44),
        BukhariBook(56, "Fighting for the Cause of Allah (Jihaad)", "كتاب الجهاد والسير", 2670, 2963, 294),
        BukhariBook(57, "One-fifth of Booty (Khumus)", "كتاب فرض الخمس", 2964, 3026, 63),
        BukhariBook(58, "Jizyah and Treaties", "كتاب الجزية والموادعة", 3027, 3056, 30),
        BukhariBook(59, "Beginning of Creation", "كتاب بدء الخلق", 3057, 3187, 131),
        BukhariBook(60, "Prophets", "كتاب أحاديث الأنبياء", 3188, 3341, 154),
        BukhariBook(61, "Virtues of the Prophet and His Companions", "كتاب المناقب", 3342, 3492, 151),
        BukhariBook(62, "Companions of the Prophet", "كتاب فضائل أصحاب النبى صلى الله عليه وسلم", 3493, 3612, 120),
        BukhariBook(63, "Merits of the Helpers in Madinah (Ansaar)", "كتاب مناقب الأنصار", 3613, 3784, 172),
        BukhariBook(64, "Military Expeditions (Al-Maghaazi)", "كتاب المغازى", 3785, 4272, 488),
        BukhariBook(65, "Prophetic Commentary on the Qur'an (Tafseer)", "كتاب التفسير", 4273, 4771, 499),
        BukhariBook(66, "Virtues of the Qur'an", "كتاب فضائل القرآن", 4772, 4858, 87),
        BukhariBook(67, "Wedlock and Marriage (Nikaah)", "كتاب النكاح", 4859, 5041, 183),
        BukhariBook(68, "Divorce", "كتاب الطلاق", 5042, 5136, 95),
        BukhariBook(69, "Supporting the Family", "كتاب النفقات", 5137, 5158, 22),
        BukhariBook(70, "Food and Meals", "كتاب الأطعمة", 5159, 5253, 95),
        BukhariBook(71, "Sacrifice on Birth (`Aqiqa)", "كتاب العقيقة", 5254, 5262, 9),
        BukhariBook(72, "Hunting and Slaughtering", "كتاب الذبائح والصيد", 5263, 5331, 69),
        BukhariBook(73, "Al-Adha Sacrifice (Adaahi)", "كتاب الأضاحي", 5332, 5361, 30),
        BukhariBook(74, "Drinks", "كتاب الأشربة", 5362, 5426, 65),
        BukhariBook(75, "Patients", "كتاب المرضى", 5427, 5463, 37),
        BukhariBook(76, "Medicine", "كتاب الطب", 5464, 5556, 93),
        BukhariBook(77, "Dress", "كتاب اللباس", 5557, 5741, 185),
        BukhariBook(78, "Good Manners (Al-Adab)", "كتاب الأدب", 5742, 5991, 250),
        BukhariBook(79, "Asking Permission", "كتاب الاستئذان", 5992, 6066, 75),
        BukhariBook(80, "Invocations", "كتاب الدعوات", 6067, 6172, 106),
        BukhariBook(81, "Making the Heart Tender (Ar-Riqaq)", "كتاب الرقاق", 6173, 6353, 181),
        BukhariBook(82, "Divine Will (Al-Qadar)", "كتاب القدر", 6354, 6379, 26),
        BukhariBook(83, "Oaths and Vows", "كتاب الأيمان والنذور", 6380, 6463, 84),
        BukhariBook(84, "Expiation for Unfulfilled Oaths", "كتاب كفارات الأيمان", 6464, 6478, 15),
        BukhariBook(85, "Laws of Inheritance (Al-Faraa'id)", "كتاب الفرائض", 6479, 6525, 47),
        BukhariBook(86, "Limits and Punishments (Hudood)", "كتاب الحدود", 6526, 6606, 81),
        BukhariBook(87, "Blood Money (Ad-Diyat)", "كتاب الديات", 6607, 6661, 55),
        BukhariBook(88, "Apostates", "كتاب استتابة المرتدين والمعاندين وقتالهم", 6662, 6682, 21),
        BukhariBook(89, "Statements Made under Coercion", "كتاب الإكراه", 6683, 6695, 13),
        BukhariBook(90, "Tricks", "كتاب الحيل", 6696, 6723, 28),
        BukhariBook(91, "Interpretation of Dreams", "كتاب التعبير", 6724, 6784, 61),
        BukhariBook(92, "Afflictions and the End of the World", "كتاب الفتن", 6785, 6867, 83),
        BukhariBook(93, "Judgments (Ahkaam)", "كتاب الأحكام", 6868, 6951, 84),
        BukhariBook(94, "Wishes", "كتاب التمنى", 6952, 6971, 20),
        BukhariBook(95, "Information Given by a Truthful Person", "كتاب أخبار الآحاد", 6972, 6992, 21),
        BukhariBook(96, "Holding Fast to the Qur'an and Sunnah", "كتاب الاعتصام بالكتاب والسنة", 6993, 7089, 97),
        BukhariBook(97, "Oneness and Uniqueness of Allah (Tawheed)", "كتاب التوحيد", 7090, 7277, 188),
    )

    init {
        check(all.size == 97)
        check(all.sumOf(BukhariBook::hadithCount) == 7_277)
        check(all.first().firstHadithId == 1 && all.last().lastHadithId == 7_277)
        check(all.zipWithNext().all { (current, next) -> current.lastHadithId + 1 == next.firstHadithId })
        check(all.all { it.lastHadithId - it.firstHadithId + 1 == it.hadithCount })
    }

    fun find(id: Int): BukhariBook? = all.firstOrNull { it.id == id }

    /** Returns the canonical Bukhari book that contains this collection-wide hadith id. */
    fun findByHadithId(hadithId: Int): BukhariBook? =
        all.firstOrNull { hadithId in it.firstHadithId..it.lastHadithId }
}
