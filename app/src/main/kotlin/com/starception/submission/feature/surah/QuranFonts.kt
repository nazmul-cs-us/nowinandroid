package com.starception.submission.feature.surah

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.starception.submission.R

/**
 * Font families for Quran display
 * Most commonly used Arabic fonts for Quranic text rendering
 *
 * - PDMSSaleem (Lateef): Traditional Quranic style, widely used in printed Qurans
 * - NoorEHidayat (Noto Naskh Arabic): Clean, modern Naskh style for excellent readability
 * - Thabit (Aref Ruqaa): Traditional Ruq'ah script style
 * - UthmanicScript (Scheherazade): Classical Uthmanic Hafs script
 * - Amiri: Classical Arabic typeface for Quranic text
 * - Poppins: Modern, clean font for English UI text
 */
object QuranFonts {
    /**
     * PDMS Saleem - Traditional Quranic font
     * Uses Lateef font (SIL Open Font License)
     * Professional Arabic typeface designed for Quranic text with full diacritical support
     */
    val PDMSSaleem = FontFamily(
        Font(R.font.pdms_saleem_quran, FontWeight.Normal),
        Font(R.font.pdms_saleem_quran_bold, FontWeight.Bold)
    )

    /**
     * Noor-e-Hidayat - Clean, readable Naskh style
     * Uses Noto Naskh Arabic (Google Fonts)
     * Modern, professional Arabic Naskh font optimized for digital Quran display
     */
    val NoorEHidayat = FontFamily(
        Font(R.font.noor_hidayat_quran, FontWeight.Normal),
        Font(R.font.noor_hidayat_quran_bold, FontWeight.Bold)
    )

    /**
     * Thabit - Traditional Arabic script
     * Uses Lateef Medium/SemiBold (SIL Open Font License)
     * Professional Arabic font with excellent readability for Quranic text
     */
    val Thabit = FontFamily(
        Font(R.font.thabit_quran, FontWeight.Normal),
        Font(R.font.thabit_quran_bold, FontWeight.Bold)
    )

    /**
     * Uthmanic Script - Authentic Quranic manuscript style
     * Uses Amiri Quran (SIL Open Font License)
     * Official font designed by Khaled Hosny specifically for Quranic text
     * Matches the traditional Mushaf Uthmani style used in most printed Qurans
     */
    val UthmanicScript = FontFamily(
        Font(R.font.amiri_quran, FontWeight.Normal)
    )

    /**
     * Amiri - Classical Arabic typeface
     * Elegant font for Quranic and classical Arabic text
     */
    val Amiri = FontFamily(
        Font(R.font.amiri_regular, FontWeight.Normal),
        Font(R.font.amiri_bold, FontWeight.Bold)
    )

    /**
     * Scheherazade - New Testament style Arabic font
     * Alternative name for UthmanicScript
     */
    val Scheherazade = FontFamily(
        Font(R.font.scheherazade_regular, FontWeight.Normal),
        Font(R.font.scheherazade_bold, FontWeight.Bold)
    )

    /**
     * IndoPak Script - Traditional Indo-Pakistani Quran style
     * Uses Naskh-Nastaleeq-IndoPak-QWBW (Islamic Studies Font)
     * Authentic Indo-Pakistani Mushaf style combining Naskh and Nastaliq scripts
     * Professional Quranic font optimized for word-by-word (QWBW) layouts
     * Source: https://islamicstudies.info/quran/arabic/fonts/
     */
    val IndoPakScript = FontFamily(
        Font(R.font.indopak_quran, FontWeight.Normal)
    )

    /**
     * Poppins - Modern UI font for English text
     * Used for UI elements, buttons, and English translations
     */
    val Poppins = FontFamily(
        Font(R.font.poppins_regular, FontWeight.Normal),
        Font(R.font.poppins_medium, FontWeight.Medium),
        Font(R.font.poppins_semibold, FontWeight.SemiBold),
        Font(R.font.poppins_bold, FontWeight.Bold)
    )
}
