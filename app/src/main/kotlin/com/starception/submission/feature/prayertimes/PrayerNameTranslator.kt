package com.starception.submission.feature.prayertimes

/**
 * Prayer Name Translator - Location-based prayer name translations
 * 
 * This utility provides prayer name translations based on the user's current location.
 * It automatically detects the country code and returns appropriate local language names.
 * 
 * Supported Languages:
 * - Arabic (Middle East countries)
 * - Turkish (Turkey)
 * - Urdu (Pakistan)
 * - Persian (Iran)
 * - Malay (Malaysia, Indonesia, Brunei)
 * - Bengali (Bangladesh)
 * 
 * Usage:
 * val localName = getPrayerNameInLocalLanguage("Dhuhr", "AE") // Returns "الظهر"
 */

/**
 * Get prayer names in local language based on location country code
 * 
 * @param englishName The English prayer name (Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha)
 * @param countryCode The ISO 3166-1 alpha-2 country code (e.g., "AE", "TR", "PK")
 * @return The prayer name in the local language, or Arabic as default
 */
fun getPrayerNameInLocalLanguage(englishName: String, countryCode: String?): String {
    return when (countryCode?.uppercase()) {
        // Arabic-speaking countries
        "AE", "SA", "EG", "JO", "LB", "SY", "IQ", "KW", "QA", "BH", "OM", "YE", "LY", "TN", "DZ", "MA", "SD" -> {
            when (englishName) {
                "Fajr" -> "الفجر"
                "Sunrise" -> "الشروق" 
                "Dhuhr" -> "الظهر"
                "Asr" -> "العصر"
                "Maghrib" -> "المغرب"
                "Isha" -> "العشاء"
                else -> ""
            }
        }
        // Turkish
        "TR" -> {
            when (englishName) {
                "Fajr" -> "İmsak"
                "Sunrise" -> "Güneş"
                "Dhuhr" -> "Öğle"
                "Asr" -> "İkindi"
                "Maghrib" -> "Akşam"
                "Isha" -> "Yatsı"
                else -> ""
            }
        }
        // Urdu (Pakistan)
        "PK" -> {
            when (englishName) {
                "Fajr" -> "فجر"
                "Sunrise" -> "طلوع آفتاب"
                "Dhuhr" -> "ظہر"
                "Asr" -> "عصر"
                "Maghrib" -> "مغرب"
                "Isha" -> "عشاء"
                else -> ""
            }
        }
        // Persian (Iran)
        "IR" -> {
            when (englishName) {
                "Fajr" -> "صبح"
                "Sunrise" -> "طلوع آفتاب"
                "Dhuhr" -> "ظهر"
                "Asr" -> "عصر"
                "Maghrib" -> "مغرب"
                "Isha" -> "عشاء"
                else -> ""
            }
        }
        // Malay (Malaysia, Indonesia, Brunei)
        "MY", "ID", "BN" -> {
            when (englishName) {
                "Fajr" -> "Subuh"
                "Sunrise" -> "Syuruk"
                "Dhuhr" -> "Zohor"
                "Asr" -> "Asar"
                "Maghrib" -> "Maghrib"
                "Isha" -> "Isyak"
                else -> ""
            }
        }
        // Bengali (Bangladesh)
        "BD" -> {
            when (englishName) {
                "Fajr" -> "ফজর"
                "Sunrise" -> "সূর্যোদয়"
                "Dhuhr" -> "যোহর"
                "Asr" -> "আসর"
                "Maghrib" -> "মাগরিব"
                "Isha" -> "এশা"
                else -> ""
            }
        }
        // Default to Arabic for other Muslim-majority countries or if country is unknown
        else -> {
            when (englishName) {
                "Fajr" -> "الفجر"
                "Sunrise" -> "الشروق"
                "Dhuhr" -> "الظهر"
                "Asr" -> "العصر"
                "Maghrib" -> "المغرب"
                "Isha" -> "العشاء"
                else -> ""
            }
        }
    }
}