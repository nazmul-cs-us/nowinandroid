package com.starception.submission.islamic.shared.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Islamic Date and Calendar Utilities
 * 
 * Provides Islamic calendar support and date formatting utilities
 * for the Islamic prayer times application.
 */
object IslamicDateUtil {
    
    /**
     * Standard Gregorian date formatter
     */
    private val gregorianFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH)
    
    /**
     * Islamic date formatter (approximation)
     */
    private val islamicFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
    
    /**
     * Format Gregorian date for prayer times display
     * 
     * @param date The date to format
     * @return Formatted Gregorian date string
     */
    fun formatGregorianDate(date: LocalDateTime): String {
        return date.format(gregorianFormatter)
    }
    
    /**
     * Format date for Islamic context
     * 
     * @param date The date to format
     * @return Formatted date string with Islamic context
     */
    fun formatIslamicDate(date: LocalDateTime): String {
        return date.format(islamicFormatter)
    }
    
    /**
     * Get day of week in Arabic
     * 
     * @param date The date to get day name for
     * @return Arabic day name
     */
    fun getArabicDayName(date: LocalDate): String {
        return when (date.dayOfWeek.value) {
            1 -> "الاثنين"    // Monday - Al-Ithnayn
            2 -> "الثلاثاء"   // Tuesday - Ath-Thulatha
            3 -> "الأربعاء"  // Wednesday - Al-Arba'a
            4 -> "الخميس"    // Thursday - Al-Khamees
            5 -> "الجمعة"    // Friday - Al-Jumu'ah (most important day)
            6 -> "السبت"     // Saturday - As-Sabt
            7 -> "الأحد"     // Sunday - Al-Ahad
            else -> "يوم"    // Day
        }
    }
    
    /**
     * Get Islamic month names (Hijri calendar approximation)
     * 
     * @param monthNumber Month number (1-12)
     * @return Arabic month name
     */
    fun getIslamicMonthName(monthNumber: Int): String {
        return when (monthNumber) {
            1 -> "محرم"           // Muharram
            2 -> "صفر"           // Safar
            3 -> "ربيع الأول"     // Rabi' al-Awwal
            4 -> "ربيع الثاني"    // Rabi' al-Thani
            5 -> "جمادى الأولى"   // Jumada al-Ula
            6 -> "جمادى الثانية"  // Jumada al-Thani
            7 -> "رجب"          // Rajab
            8 -> "شعبان"        // Sha'ban
            9 -> "رمضان"        // Ramadan (holy month)
            10 -> "شوال"        // Shawwal
            11 -> "ذو القعدة"    // Dhu al-Qi'dah
            12 -> "ذو الحجة"     // Dhu al-Hijjah (pilgrimage month)
            else -> "شهر"       // Month
        }
    }
    
    /**
     * Check if it's Friday (Jumu'ah day)
     * 
     * @param date Date to check
     * @return True if it's Friday
     */
    fun isJumuahDay(date: LocalDate = LocalDate.now()): Boolean {
        return date.dayOfWeek.value == 5 // Friday
    }
    
    /**
     * Check if it's a significant Islamic day
     * 
     * @param date Date to check
     * @return Description of significance or null
     */
    fun getIslamicDaySignificance(date: LocalDate): String? {
        // This is a simplified check - in reality, Islamic dates follow lunar calendar
        return when {
            isJumuahDay(date) -> "يوم الجمعة المبارك" // Blessed Friday
            date.dayOfMonth == 1 -> "بداية الشهر" // Beginning of month
            date.dayOfMonth in 10..12 -> "أيام البيض" // White days (recommended fasting)
            else -> null
        }
    }
    
    /**
     * Format date with Islamic context
     * 
     * @param dateTime DateTime to format
     * @param includeArabic Whether to include Arabic day name
     * @return Formatted date string with Islamic context
     */
    fun formatWithIslamicContext(
        dateTime: LocalDateTime, 
        includeArabic: Boolean = true
    ): String {
        val gregorian = formatGregorianDate(dateTime)
        val arabicDay = if (includeArabic) getArabicDayName(dateTime.toLocalDate()) else null
        val significance = getIslamicDaySignificance(dateTime.toLocalDate())
        
        return buildString {
            append(gregorian)
            if (arabicDay != null) {
                append(" • $arabicDay")
            }
            if (significance != null) {
                append(" • $significance")
            }
        }
    }
    
    /**
     * Get Islamic prayer day summary
     * 
     * @param date Date for summary
     * @return Summary text for prayer times display
     */
    fun getPrayerDaySummary(date: LocalDateTime): String {
        val day = getArabicDayName(date.toLocalDate())
        val significance = getIslamicDaySignificance(date.toLocalDate())
        
        return when {
            significance != null -> "$day • $significance"
            isJumuahDay(date.toLocalDate()) -> "$day • جمعة مباركة" // Blessed Friday
            else -> day
        }
    }
}