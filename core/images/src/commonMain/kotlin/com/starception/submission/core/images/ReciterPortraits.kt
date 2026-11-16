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
