package com.starception.dua.prayer.model

/**
 * User preferences for prayer time calculations
 */
data class PrayerSettings(
    val calculationMethod: CalculationMethod = CalculationMethod.MUSLIM_WORLD_LEAGUE,
    val asrMadhhab: AsrMadhhab = AsrMadhhab.STANDARD,
    val highLatitudeAdjustment: HighLatitudeAdjustment = HighLatitudeAdjustment.NONE,
    val customFajrAngle: Double? = null,
    val customIshaAngle: Double? = null,
    val customIshaDelay: Int? = null,
    val timeOffsets: PrayerTimeOffsets = PrayerTimeOffsets(),
    val location: Location? = null,
    val useGpsLocation: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val notificationSound: String = "default",
    val vibrationEnabled: Boolean = true
) {
    /**
     * Gets the effective Fajr angle considering custom settings
     */
    fun getEffectiveFajrAngle(): Double {
        return customFajrAngle ?: calculationMethod.fajrAngle
    }
    
    /**
     * Gets the effective Isha angle considering custom settings
     */
    fun getEffectiveIshaAngle(): Double? {
        return customIshaAngle ?: calculationMethod.ishaAngle
    }
    
    /**
     * Gets the effective Isha delay considering custom settings
     */
    fun getEffectiveIshaDelay(): Int? {
        return customIshaDelay ?: calculationMethod.ishaDelay
    }
}

/**
 * Time offsets for each prayer in minutes
 */
data class PrayerTimeOffsets(
    val fajr: Int = 0,
    val sunrise: Int = 0,
    val dhuhr: Int = 0,
    val asr: Int = 0,
    val maghrib: Int = 0,
    val isha: Int = 0
) {
    fun getOffset(prayer: String): Int {
        return when (prayer.lowercase()) {
            "fajr" -> fajr
            "sunrise" -> sunrise
            "dhuhr" -> dhuhr
            "asr" -> asr
            "maghrib" -> maghrib
            "isha" -> isha
            else -> 0
        }
    }
}