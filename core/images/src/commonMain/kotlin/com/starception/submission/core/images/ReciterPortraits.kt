package com.starception.submission.core.images

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.starception.submission.core.images.resources.*
import org.jetbrains.compose.resources.painterResource

// Source-backed full names; numeric catalog IDs are never used as image IDs.
private val additionalPortraitAliases = mapOf(
    "abdallahalmatroud" to "catalog-59",
    "abdelbarialtoubayti" to "catalog-49",
    "abdulbarithobaity" to "catalog-49",
    "abdulbasitabdulsamad" to "abdul-basit",
    "abdullahalmattrod" to "catalog-59",
    "abdullahbasfar" to "abdullah-basfar",
    "abdullahbasfer" to "abdullah-basfar",
    "abdullahibnalibasfar" to "abdullah-basfar",
    "abdulrahmanalsudaes" to "sudais",
    "abdulrasheedsoufi" to "catalog-64",
    "abdulrashidsufi" to "catalog-64",
    "abedalbase6abedalsamad" to "abdul-basit",
    "abedalrahmanelsudays" to "sudais",
    "adelrayan" to "catalog-48",
    "adelryyan" to "catalog-48",
    "ahmadalhawashi" to "catalog-6",
    "ahmadalhuthaifi" to "ahmed-hudhaifi",
    "ahmadnauina" to "catalog-9",
    "ahmedalhodaifi" to "ahmed-hudhaifi",
    "ahmedalialhudhaifi" to "ahmed-hudhaifi",
    "akramalalaqmi" to "catalog-10",
    "aymansuwaid" to "ayman-suwaid",
    "aymanswed" to "ayman-suwaid",
    "faresabbad" to "catalog-81",
    "haniarrifai" to "hani-rifai",
    "ibrahimalakdar" to "ibrahim-akhdar",
    "ibrahimalakhdar" to "ibrahim-akhdar",
    "jamaanalosaimi" to "catalog-181",
    "khaledalqahtani" to "catalog-21",
    "maheralmeaqli" to "maher",
    "mahiralmuayqali" to "maher",
    "mahmoodalsheimy" to "catalog-151",
    "mahmoudalialbanna" to "catalog-121",
    "mahmoudalshaymi" to "catalog-151",
    "misharyalafasi" to "alafasy",
    "moftahassultany" to "catalog-129",
    "mohamedayoub" to "muhammad-ayyub",
    "mohamedtablawi" to "catalog-106",
    "mohammadabdullkarem" to "catalog-115",
    "mohammadaltablaway" to "catalog-106",
    "mohammedayyoub" to "muhammad-ayyub",
    "mohammedjibreel" to "muhammad-jibril",
    "mousabilal" to "catalog-161",
    "msharyel3afasy" to "alafasy",
    "muftahalsaltany" to "catalog-129",
    "muhammadabdulkareem" to "catalog-115",
    "muhammadayyub" to "muhammad-ayyub",
    "muhammadjibreel" to "muhammad-jibril",
    "muhammadjibril" to "muhammad-jibril",
    "musabelal" to "catalog-161",
    "mustafaallahoni" to "catalog-126",
    "mustafaallahuni" to "catalog-126",
    "nabilalrifay" to "catalog-87",
    "nabilarrifai" to "catalog-87",
    "nasseralqatami" to "catalog-86",
    "saadalghamdi" to "catalog-30",
    "saadalghamdy" to "catalog-30",
    "saberabdulhakam" to "catalog-39",
    "saberabdulhakm" to "catalog-39",
    "sahilyassin" to "catalog-32",
    "sahlyassin" to "catalog-32",
    "salahalbudair" to "catalog-43",
    "salahalhashem" to "catalog-44",
    "salahalhashim" to "catalog-44",
    "salahbukhater" to "catalog-46",
    "saudalshuraim" to "shuraim",
    "shahriarparhizgar" to "shahriar-parhizgar",
    "shahryarparhizgar" to "shahriar-parhizgar",
    "slaahbukhatir" to "catalog-46",
    "soudelshreem" to "shuraim",
    "tawfeeqassayegh" to "catalog-17",
    "yasseraldosari" to "catalog-92",
    "zakidaghistani" to "catalog-33",
    "أحمدالحذيفي" to "ahmed-hudhaifi",
    "أحمدالحواشي" to "catalog-6",
    "أحمدنعينع" to "catalog-9",
    "أكرمالعلاقمي" to "catalog-10",
    "أيمنسويد" to "ayman-suwaid",
    "إبراهيمالأخضر" to "ibrahim-akhdar",
    "احمدالحواشي" to "catalog-6",
    "توفيقالصايغ" to "catalog-17",
    "جمعانالعصيمي" to "catalog-181",
    "خالدالقحطاني" to "catalog-21",
    "زكيداغستاني" to "catalog-33",
    "سعدالغامدي" to "catalog-30",
    "سعودالشريم" to "shuraim",
    "سهلياسين" to "catalog-32",
    "شهرياربرهيزكار" to "shahriar-parhizgar",
    "صابرعبدالحكم" to "catalog-39",
    "صلاحالبدير" to "catalog-43",
    "صلاحالهاشم" to "catalog-44",
    "صلاحبوخاطر" to "catalog-46",
    "عادلريان" to "catalog-48",
    "عبدالبارئالثبيتي" to "catalog-49",
    "عبدالباسطعبدالصمد" to "abdul-basit",
    "عبدالرحمنالسديس" to "sudais",
    "عبدالرشيدصوفي" to "catalog-64",
    "عبداللهالمطرود" to "catalog-59",
    "عبداللهبصفر" to "abdullah-basfar",
    "عبداللهبنعليبصفر" to "abdullah-basfar",
    "فارسعباد" to "catalog-81",
    "ماهرالمعيقلي" to "maher",
    "محمدأيوب" to "muhammad-ayyub",
    "محمدالطبلاوي" to "catalog-106",
    "محمدجبريل" to "muhammad-jibril",
    "محمدعبدالكريم" to "catalog-115",
    "محمودالشيمي" to "catalog-151",
    "محمودعلىالبنا" to "catalog-121",
    "محمودعليالبنا" to "catalog-121",
    "مشاريالعفاسي" to "alafasy",
    "مصطفىاللاهوني" to "catalog-126",
    "مفتاحالسلطني" to "catalog-129",
    "موسىبلال" to "catalog-161",
    "ناصرالقطامي" to "catalog-86",
    "نبيلالرفاعي" to "catalog-87",
    "هانيالرفاعي" to "hani-rifai",
    "ياسرالدوسري" to "catalog-92",
)

/** Explicit aliases only: never assign a stranger's portrait using a fuzzy name search. */
fun reciterPersonKey(name: String): String {
    val key = name.lowercase().filter(Char::isLetterOrDigit)
    additionalPortraitAliases[key]?.let { return it }
    verifiedPortraitAliases[key]?.let { return it }
    return when {
        listOf("mishary", "mishari", "mshari").any(key::contains) &&
            listOf("afasy", "afassi", "afasi").any(key::contains) -> "alafasy"
        listOf("abdulbasit", "abdulbaset", "abdelbasset", "abdulbasset").any(key::contains) &&
            listOf("abdessamad", "abdulsamad", "abdussamad", "abdelsamad").any(key::contains) -> "abdul-basit"
        listOf("abdulrahman", "abdurrahman", "abdelrahman", "abderrahman").any(key::contains) &&
            listOf("sudais", "sudays", "sudaes").any(key::contains) -> "sudais"
        key.contains("mahmoud") && listOf("husary", "hussary", "hosary", "hossary").any(key::contains) -> "husary"
        listOf("muhammad", "mohamed", "mohammed").any(key::contains) &&
            listOf("minshawi", "menshawi", "menchaoui", "minchaoui").any(key::contains) -> "minshawi"
        listOf("saud", "saoud").any(key::contains) &&
            listOf("shuraim", "shuraym", "shoraim", "shoraym").any(key::contains) -> "shuraim"
        listOf("maher", "mahir").any(key::contains) &&
            listOf("muaiqly", "muaiqli", "muayqly", "mueaqly", "maikli").any(key::contains) -> "maher"
        key.contains("hani") && listOf("rifai", "refai").any(key::contains) -> "hani-rifai"
        listOf("abubakr", "aboubakr", "abobakr").any(key::contains) && key.contains("shatri") -> "abu-bakr-shatri"
        else -> key
    }
}

/** Small bundled portraits remain available while offline. Unknown names use initials in the UI. */
@Composable
fun reciterPortrait(name: String): Painter? = when (reciterPersonKey(name)) {
    "alafasy" -> painterResource(Res.drawable.reciter_alafasy)
    "abdul-basit" -> painterResource(Res.drawable.reciter_abdul_basit)
    "sudais" -> painterResource(Res.drawable.reciter_sudais)
    "husary" -> painterResource(Res.drawable.reciter_husary)
    "minshawi" -> painterResource(Res.drawable.reciter_minshawi)
    "shuraim" -> painterResource(Res.drawable.reciter_shuraim)
    "maher" -> painterResource(Res.drawable.reciter_maher)
    "hani-rifai" -> painterResource(Res.drawable.reciter_hani_rifai)
    "abu-bakr-shatri" -> painterResource(Res.drawable.reciter_abu_bakr_shatri)
    "catalog-6" -> painterResource(Res.drawable.reciter_catalog_6)
    "catalog-9" -> painterResource(Res.drawable.reciter_catalog_9)
    "catalog-10" -> painterResource(Res.drawable.reciter_catalog_10)
    "catalog-17" -> painterResource(Res.drawable.reciter_catalog_17)
    "catalog-21" -> painterResource(Res.drawable.reciter_catalog_21)
    "catalog-30" -> painterResource(Res.drawable.reciter_catalog_30)
    "catalog-32" -> painterResource(Res.drawable.reciter_catalog_32)
    "catalog-33" -> painterResource(Res.drawable.reciter_catalog_33)
    "catalog-39" -> painterResource(Res.drawable.reciter_catalog_39)
    "catalog-43" -> painterResource(Res.drawable.reciter_catalog_43)
    "catalog-44" -> painterResource(Res.drawable.reciter_catalog_44)
    "catalog-46" -> painterResource(Res.drawable.reciter_catalog_46)
    "catalog-48" -> painterResource(Res.drawable.reciter_catalog_48)
    "catalog-49" -> painterResource(Res.drawable.reciter_catalog_49)
    "catalog-59" -> painterResource(Res.drawable.reciter_catalog_59)
    "catalog-64" -> painterResource(Res.drawable.reciter_catalog_64)
    "catalog-81" -> painterResource(Res.drawable.reciter_catalog_81)
    "catalog-86" -> painterResource(Res.drawable.reciter_catalog_86)
    "catalog-87" -> painterResource(Res.drawable.reciter_catalog_87)
    "catalog-92" -> painterResource(Res.drawable.reciter_catalog_92)
    "catalog-106" -> painterResource(Res.drawable.reciter_catalog_106)
    "catalog-115" -> painterResource(Res.drawable.reciter_catalog_115)
    "catalog-121" -> painterResource(Res.drawable.reciter_catalog_121)
    "catalog-126" -> painterResource(Res.drawable.reciter_catalog_126)
    "catalog-129" -> painterResource(Res.drawable.reciter_catalog_129)
    "catalog-151" -> painterResource(Res.drawable.reciter_catalog_151)
    "catalog-161" -> painterResource(Res.drawable.reciter_catalog_161)
    "catalog-181" -> painterResource(Res.drawable.reciter_catalog_181)
    "ibrahim-akhdar" -> painterResource(Res.drawable.reciter_ibrahim_akhdar)
    "abdullah-basfar" -> painterResource(Res.drawable.reciter_abdullah_basfar)
    "muhammad-ayyub" -> painterResource(Res.drawable.reciter_muhammad_ayyub)
    "muhammad-jibril" -> painterResource(Res.drawable.reciter_muhammad_jibril)
    "ayman-suwaid" -> painterResource(Res.drawable.reciter_ayman_suwaid)
    "ahmed-hudhaifi" -> painterResource(Res.drawable.reciter_ahmed_hudhaifi)
    "shahriar-parhizgar" -> painterResource(Res.drawable.reciter_shahriar_parhizgar)
    "catalog-107" -> painterResource(Res.drawable.reciter_catalog_107)
    "catalog-110" -> painterResource(Res.drawable.reciter_catalog_110)
    "catalog-116" -> painterResource(Res.drawable.reciter_catalog_116)
    "catalog-12" -> painterResource(Res.drawable.reciter_catalog_12)
    "catalog-125" -> painterResource(Res.drawable.reciter_catalog_125)
    "catalog-127" -> painterResource(Res.drawable.reciter_catalog_127)
    "catalog-13" -> painterResource(Res.drawable.reciter_catalog_13)
    "catalog-139" -> painterResource(Res.drawable.reciter_catalog_139)
    "catalog-152" -> painterResource(Res.drawable.reciter_catalog_152)
    "catalog-159" -> painterResource(Res.drawable.reciter_catalog_159)
    "catalog-16" -> painterResource(Res.drawable.reciter_catalog_16)
    "catalog-160" -> painterResource(Res.drawable.reciter_catalog_160)
    "catalog-163" -> painterResource(Res.drawable.reciter_catalog_163)
    "catalog-164" -> painterResource(Res.drawable.reciter_catalog_164)
    "catalog-165" -> painterResource(Res.drawable.reciter_catalog_165)
    "catalog-18" -> painterResource(Res.drawable.reciter_catalog_18)
    "catalog-198" -> painterResource(Res.drawable.reciter_catalog_198)
    "catalog-20" -> painterResource(Res.drawable.reciter_catalog_20)
    "catalog-203" -> painterResource(Res.drawable.reciter_catalog_203)
    "catalog-211" -> painterResource(Res.drawable.reciter_catalog_211)
    "catalog-21204" -> painterResource(Res.drawable.reciter_catalog_21204)
    "catalog-217" -> painterResource(Res.drawable.reciter_catalog_217)
    "catalog-22" -> painterResource(Res.drawable.reciter_catalog_22)
    "catalog-24" -> painterResource(Res.drawable.reciter_catalog_24)
    "catalog-267" -> painterResource(Res.drawable.reciter_catalog_267)
    "catalog-274" -> painterResource(Res.drawable.reciter_catalog_274)
    "catalog-277" -> painterResource(Res.drawable.reciter_catalog_277)
    "catalog-278" -> painterResource(Res.drawable.reciter_catalog_278)
    "catalog-279" -> painterResource(Res.drawable.reciter_catalog_279)
    "catalog-286" -> painterResource(Res.drawable.reciter_catalog_286)
    "catalog-287" -> painterResource(Res.drawable.reciter_catalog_287)
    "catalog-302" -> painterResource(Res.drawable.reciter_catalog_302)
    "catalog-38" -> painterResource(Res.drawable.reciter_catalog_38)
    "catalog-42" -> painterResource(Res.drawable.reciter_catalog_42)
    "catalog-5" -> painterResource(Res.drawable.reciter_catalog_5)
    "catalog-50" -> painterResource(Res.drawable.reciter_catalog_50)
    "catalog-55" -> painterResource(Res.drawable.reciter_catalog_55)
    "catalog-62" -> painterResource(Res.drawable.reciter_catalog_62)
    "catalog-63" -> painterResource(Res.drawable.reciter_catalog_63)
    "catalog-66" -> painterResource(Res.drawable.reciter_catalog_66)
    "catalog-67" -> painterResource(Res.drawable.reciter_catalog_67)
    "catalog-69" -> painterResource(Res.drawable.reciter_catalog_69)
    "catalog-71" -> painterResource(Res.drawable.reciter_catalog_71)
    "catalog-72" -> painterResource(Res.drawable.reciter_catalog_72)
    "catalog-76" -> painterResource(Res.drawable.reciter_catalog_76)
    "catalog-77" -> painterResource(Res.drawable.reciter_catalog_77)
    "catalog-78" -> painterResource(Res.drawable.reciter_catalog_78)
    "catalog-8" -> painterResource(Res.drawable.reciter_catalog_8)
    "catalog-80" -> painterResource(Res.drawable.reciter_catalog_80)
    "catalog-93" -> painterResource(Res.drawable.reciter_catalog_93)
    "catalog-61" -> painterResource(Res.drawable.reciter_catalog_61)
    "catalog-70" -> painterResource(Res.drawable.reciter_catalog_70)
    "catalog-74" -> painterResource(Res.drawable.reciter_catalog_74)
    "catalog-96" -> painterResource(Res.drawable.reciter_catalog_96)
    "catalog-108" -> painterResource(Res.drawable.reciter_catalog_108)
    "catalog-134" -> painterResource(Res.drawable.reciter_catalog_134)
    "catalog-201" -> painterResource(Res.drawable.reciter_catalog_201)
    "catalog-21183" -> painterResource(Res.drawable.reciter_catalog_21183)
    "catalog-256" -> painterResource(Res.drawable.reciter_catalog_256)
    "catalog-265" -> painterResource(Res.drawable.reciter_catalog_265)
    "catalog-3" -> painterResource(Res.drawable.reciter_catalog_3)
    else -> null
}

fun reciterDisplayName(name: String): String = when (reciterPersonKey(name)) {
    "alafasy" -> "Mishary Rashid Al-Alafasy"
    "abdul-basit" -> "Abdul Basit Abdul Samad"
    "sudais" -> "Abdul Rahman Al-Sudais"
    "husary" -> "Mahmoud Khalil Al-Husary"
    "minshawi" -> "Muhammad Siddiq Al-Minshawi"
    "shuraim" -> "Saud Al-Shuraim"
    "maher" -> "Maher Al-Muaiqly"
    "hani-rifai" -> "Hani Ar-Rifai"
    "abu-bakr-shatri" -> "Abu Bakr Al-Shatri"
    else -> name.trim().replace(Regex("\\s+"), " ")
}
