package com.starception.submission.feature.surah.tajweed

import androidx.compose.ui.graphics.Color

/**
 * Tajweed rules for Quranic recitation with their associated colors.
 * Colors are chosen to be visually distinct and follow traditional Tajweed color-coding.
 */
enum class TajweedRule(val jsonKey: String, val arabicName: String, val color: Color) {
    // Ghunnah - Nasalization (2 beats)
    GHUNNAH("ghunnah", "غنة", Color(0xFFFF7E1E)),  // Orange

    // Hamzat Wasl - Connecting hamza (silent at connection)
    HAMZAT_WASL("hamzat_wasl", "همزة الوصل", Color(0xFF808080)),  // Gray

    // Idghaam types - Assimilation/Merging
    IDGHAAM_GHUNNAH("idghaam_ghunnah", "إدغام بغنة", Color(0xFF9400D3)),  // Purple
    IDGHAAM_NO_GHUNNAH("idghaam_no_ghunnah", "إدغام بلا غنة", Color(0xFF00008B)),  // Dark Blue
    IDGHAAM_MUTAJANISAYN("idghaam_mutajanisayn", "إدغام متجانسين", Color(0xFF8B4513)),  // Brown
    IDGHAAM_MUTAQARIBAYN("idghaam_mutaqaribayn", "إدغام متقاربين", Color(0xFF006400)),  // Dark Green
    IDGHAAM_SHAFAWI("idghaam_shafawi", "إدغام شفوي", Color(0xFFFF1493)),  // Deep Pink

    // Ikhfa types - Concealment
    IKHFA("ikhfa", "إخفاء", Color(0xFF00CED1)),  // Cyan/Turquoise
    IKHFA_SHAFAWI("ikhfa_shafawi", "إخفاء شفوي", Color(0xFF32CD32)),  // Lime Green

    // Iqlab - Conversion (Noon to Meem)
    IQLAB("iqlab", "إقلاب", Color(0xFF4169E1)),  // Royal Blue

    // Lam Shamsiyyah - Sun letter assimilation
    LAM_SHAMSIYYAH("lam_shamsiyyah", "لام شمسية", Color(0xFFDAA520)),  // Goldenrod

    // Madd types - Elongation/Extension
    MADD_2("madd_2", "مد 2 حركات", Color(0xFFE57373)),  // Light Red
    MADD_6("madd_6", "مد 6 حركات", Color(0xFFFF0000)),  // Red
    MADD_246("madd_246", "مد 2-4-6 حركات", Color(0xFFDC143C)),  // Crimson
    MADD_MUTTASIL("madd_muttasil", "مد متصل", Color(0xFFFF4500)),  // Orange Red
    MADD_MUNFASIL("madd_munfasil", "مد منفصل", Color(0xFFFF6347)),  // Tomato

    // Qalqalah - Echoing sound
    QALQALAH("qalqalah", "قلقلة", Color(0xFF228B22)),  // Forest Green

    // Silent letters
    SILENT("silent", "سكت", Color(0xFF696969));  // Dim Gray

    companion object {
        private val ruleMap = entries.associateBy { it.jsonKey }

        fun fromJsonKey(key: String): TajweedRule? = ruleMap[key]
    }
}

/**
 * Represents a single Tajweed annotation with character positions.
 */
data class TajweedAnnotation(
    val rule: TajweedRule,
    val startIndex: Int,
    val endIndex: Int
)

/**
 * Represents all Tajweed annotations for a specific ayah.
 */
data class AyahTajweed(
    val surahNumber: Int,
    val ayahNumber: Int,
    val annotations: List<TajweedAnnotation>
)
