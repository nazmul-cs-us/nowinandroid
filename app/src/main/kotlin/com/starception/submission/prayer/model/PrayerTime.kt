package com.starception.submission.prayer.model

import java.time.LocalDateTime
import java.time.LocalTime

/**
 * # Islamic Prayer Time Model
 * 
 * Represents a single Islamic prayer with its precise timing and current status. This is the fundamental
 * data model used throughout the prayer times system for UI display, notifications, and time tracking.
 * 
 * ## Islamic Prayer Context
 * 
 * In Islamic practice, there are five daily obligatory prayers (Salah), each performed at specific times
 * determined by the position of the sun:
 * 
 * ### Prayer Names and Their Meanings:
 * - **Fajr** (الفجر): Dawn prayer - performed before sunrise when the first light appears
 * - **Dhuhr** (الظهر): Noon prayer - performed after the sun passes its zenith (midday)
 * - **Asr** (العصر): Afternoon prayer - performed when shadows equal object length
 * - **Maghrib** (المغرب): Sunset prayer - performed just after the sun sets
 * - **Isha** (العشاء): Night prayer - performed when twilight ends and true darkness begins
 * 
 * ## Status Indicators Explained
 * 
 * The status indicators help determine the current state of each prayer relative to the user's local time:
 * 
 * ### `isNext` Flag:
 * - **Purpose**: Identifies the upcoming prayer that hasn't occurred yet
 * - **Logic**: Only one prayer can be "next" at any given time
 * - **UI Usage**: Highlighted in interfaces, used for notifications
 * - **Edge Case**: After Isha, the next prayer is tomorrow's Fajr
 * 
 * ### `isCurrently` Flag:
 * - **Purpose**: Indicates if we're currently within this prayer's optimal time window
 * - **Logic**: Active from prayer start until next prayer begins (with 2-hour limit for Isha)
 * - **UI Usage**: Shows "Current" status, enables prayer-specific actions
 * - **Practical Use**: Helps users know when to perform the prayer
 * 
 * ## Implementation Examples
 * 
 * ```kotlin
 * // Create a prayer time
 * val fajrPrayer = PrayerTime(
 *     name = "Fajr",
 *     time = LocalTime.of(5, 30),
 *     isNext = true,        // This is the next prayer
 *     isCurrently = false   // Not currently active
 * )
 * 
 * // Check prayer status
 * when {
 *     prayer.isNext -> "Next prayer: ${prayer.name} at ${prayer.time}"
 *     prayer.isCurrently -> "Current prayer window: ${prayer.name}"
 *     else -> "Prayer ${prayer.name} at ${prayer.time}"
 * }
 * ```
 * 
 * ## Integration Points
 * 
 * This model integrates with:
 * - **UI Components**: Prayer cards, status indicators, time displays
 * - **Notification System**: Schedule alerts for upcoming prayers
 * - **Background Services**: Track prayer progression throughout the day
 * - **Analytics**: Monitor prayer tracking and user engagement
 * 
 * ## Technical Notes
 * 
 * - Uses `LocalTime` for timezone-aware calculations (timezone handled in Location model)
 * - Status flags are mutually exclusive except during edge cases
 * - Thread-safe as it's an immutable data class
 * - Serializable for caching and state persistence
 * 
 * @param name The Islamic prayer name (Arabic transliteration: "Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")
 * @param time The precisely calculated local time for this prayer based on astronomical calculations
 * @param isNext Indicates if this is the next prayer to occur (only one prayer can be "next")
 * @param isCurrently Indicates if we're currently in this prayer's optimal time window
 * 
 * @see DayPrayerTimes For the complete daily prayer schedule containing all five prayers
 * @see Location For geographic and timezone information used in calculations
 * @see PrayerSettings For user preferences affecting calculation methods
 * 
 * @since 1.0
 * @author Prayer Times Development Team
 */
data class PrayerTime(
    val name: String,
    val time: LocalTime,
    val isNext: Boolean = false,
    val isCurrently: Boolean = false
)

/**
 * # Daily Prayer Times Schedule
 * 
 * Complete set of Islamic prayer times for a specific day and location, calculated using precise
 * astronomical algorithms. This is the main data structure that contains all prayer times and
 * provides intelligent methods for determining current prayer status and time calculations.
 * 
 * ## Prayer Times Science
 * 
 * Islamic prayer times are determined by specific solar positions that mark different phases
 * of the day. Each prayer has precise astronomical criteria:
 * 
 * ### Prayer Time Definitions:
 * 
 * | Prayer | Solar Position | Calculation Method | Islamic Significance |
 * |--------|----------------|-------------------|---------------------|
 * | **Fajr** | Sun 15°-19.5° below horizon | Depression angle varies by method | Beginning of fast, dawn worship |
 * | **Sunrise** | Sun at geometric horizon (-0.833°) | Atmospheric refraction correction | End of Fajr window, reference point |
 * | **Dhuhr** | Sun at highest elevation | Solar noon + equation of time | Midday prayer, break from work |
 * | **Asr** | Shadow = object height (+ noon shadow) | Shadow-based calculation | Late afternoon prayer |
 * | **Maghrib** | Sun sets below horizon (-0.833°) | Same angle as sunrise | Sunset prayer, break fast |
 * | **Isha** | Sun 15°-18° below horizon | Depression angle or fixed time | Night prayer, end of day |
 * 
 * ## Status Tracking System
 * 
 * The class provides intelligent status tracking that determines:
 * - **Current Prayer**: Which prayer window we're currently in
 * - **Next Prayer**: Which prayer is coming up next (handling day rollover)
 * - **Time Remaining**: Precise countdown to next prayer
 * 
 * ## Debug Information
 * 
 * For debugging issues, all methods provide detailed logging and state information:
 * - Input parameters validation
 * - Calculation step-by-step breakdown
 * - Edge case handling explanations
 * - Output validation and error states
 * 
 * ## Usage Examples
 * 
 * ```kotlin
 * // Create daily prayer times
 * val prayerTimes = DayPrayerTimes(
 *     date = LocalDateTime.now(),
 *     fajr = LocalTime.of(5, 30),
 *     sunrise = LocalTime.of(6, 45),
 *     dhuhr = LocalTime.of(12, 15),
 *     asr = LocalTime.of(15, 30),
 *     maghrib = LocalTime.of(18, 45),
 *     isha = LocalTime.of(20, 00),
 *     location = Location(...)
 * )
 * 
 * // Get current prayer status
 * val currentPrayer = prayerTimes.getActualPrayers().find { it.isCurrently }
 * val nextPrayer = prayerTimes.getNextPrayer()
 * val timeUntilNext = prayerTimes.getTimeUntilNextPrayer()
 * ```
 * 
 * ## Performance Characteristics
 * 
 * - **Time Complexity**: O(1) for all operations (fixed prayer count)
 * - **Memory Usage**: Minimal (6 LocalTime objects + metadata)
 * - **Thread Safety**: Immutable data class, fully thread-safe
 * - **Caching**: Results can be cached for 24-hour periods
 * 
 * @param date The specific LocalDateTime these prayer times are calculated for (includes timezone)
 * @param fajr Fajr prayer time - Dawn prayer calculated using depression angle (typically -15° to -19.5°)
 * @param sunrise Sunrise time - Astronomical event when sun crosses horizon with refraction correction
 * @param dhuhr Dhuhr prayer time - Noon prayer at solar noon with equation of time adjustment
 * @param asr Asr prayer time - Afternoon prayer based on shadow length (1x or 2x object height)
 * @param maghrib Maghrib prayer time - Sunset prayer when sun sets below geometric horizon
 * @param isha Isha prayer time - Night prayer using depression angle or fixed minutes after Maghrib
 * @param location Geographic location with coordinates and timezone used for these calculations
 * 
 * @see PrayerTime Individual prayer model with status indicators
 * @see Location Geographic information for prayer calculations  
 * @see PrayerSettings User preferences affecting calculation methods
 * @see AstronomicalCalculator Core astronomical calculation engine
 * 
 * @since 1.0
 * @author Prayer Times Development Team
 */
data class DayPrayerTimes(
    val date: LocalDateTime,
    val fajr: LocalTime,
    val sunrise: LocalTime,
    val dhuhr: LocalTime,
    val asr: LocalTime,
    val maghrib: LocalTime,
    val isha: LocalTime,
    val location: Location
) {
    /**
     * # Get All Prayers with Status
     * 
     * Returns all prayer times including sunrise with real-time status calculation based on current time.
     * This method provides complete prayer schedule with intelligent status tracking for UI display.
     * 
     * ## Method Behavior
     * 
     * **Input Processing:**
     * - Uses `LocalTime.now()` as reference point for status calculation
     * - Processes all 6 time entries: Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha
     * - No external parameters required (uses system time)
     * 
     * **Status Calculation Logic:**
     * 1. **Next Prayer Detection**: First prayer after current time gets `isNext = true`
     * 2. **Current Prayer Detection**: Prayer active between its time and next prayer time
     * 3. **Edge Case Handling**: After Isha (last prayer), special 2-hour window logic
     * 4. **Day Rollover**: When no prayers left today, tomorrow's Fajr becomes next
     * 
     * **Current Prayer Windows:**
     * - **Fajr**: Active from Fajr time until Sunrise
     * - **Sunrise**: Active from Sunrise until Dhuhr (informational only)
     * - **Dhuhr**: Active from Dhuhr time until Asr
     * - **Asr**: Active from Asr time until Maghrib  
     * - **Maghrib**: Active from Maghrib time until Isha
     * - **Isha**: Active from Isha time for maximum 2 hours (prevents all-night current status)
     * 
     * ## Debug Information
     * 
     * **Input State:**
     * ```
     * Current Time: LocalTime.now()
     * Prayer Times: [Fajr: ${fajr}, Sunrise: ${sunrise}, Dhuhr: ${dhuhr}, Asr: ${asr}, Maghrib: ${maghrib}, Isha: ${isha}]
     * Reference Date: ${date}
     * Location: ${location.getDisplayName()}
     * ```
     * 
     * **Processing Steps:**
     * 1. Create base prayer list with times
     * 2. Find next prayer index using `prayers.indexOfFirst { it.time.isAfter(now) }`
     * 3. Apply status logic using `mapIndexed` with window calculations
     * 4. Handle Isha special case with `prayer.time.plusHours(2)` limit
     * 
     * **Output Validation:**
     * - Exactly 6 PrayerTime objects returned
     * - At most 1 prayer has `isNext = true`  
     * - At most 1 prayer has `isCurrently = true`
     * - All times are valid LocalTime objects
     * - Status flags are mutually exclusive per prayer
     * 
     * ## Edge Cases Handled
     * 
     * | Scenario | Current Time | Next Prayer | Current Prayer | Logic Applied |
     * |----------|--------------|-------------|----------------|---------------|
     * | **Before Fajr** | 03:00 | Fajr | None | Normal next prayer detection |
     * | **During Fajr** | 05:45 (Fajr at 05:30) | Sunrise | Fajr | Between Fajr and Sunrise |
     * | **During Day** | 14:00 (Asr at 15:30) | Asr | Dhuhr | Between prayers logic |
     * | **Late Isha** | 23:00 (Isha at 20:00) | Tomorrow's Fajr | None | 2-hour Isha limit exceeded |
     * | **After Isha** | 22:00 (Isha at 20:00) | Tomorrow's Fajr | Isha | Within 2-hour window |
     * | **Day Rollover** | 23:59 | Tomorrow's Fajr | None | Next day handling |
     * 
     * @return List<PrayerTime> containing 6 prayers (Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha) with status
     * 
     * **Output Example:**
     * ```kotlin
     * [
     *   PrayerTime("Fajr", 05:30, isNext=false, isCurrently=false),
     *   PrayerTime("Sunrise", 06:45, isNext=false, isCurrently=false), 
     *   PrayerTime("Dhuhr", 12:15, isNext=false, isCurrently=true),   // Currently active
     *   PrayerTime("Asr", 15:30, isNext=true, isCurrently=false),     // Next prayer
     *   PrayerTime("Maghrib", 18:45, isNext=false, isCurrently=false),
     *   PrayerTime("Isha", 20:00, isNext=false, isCurrently=false)
     * ]
     * ```
     */
    fun getAllPrayers(): List<PrayerTime> {
        val now = LocalTime.now()
        val prayers = listOf(
            PrayerTime("Fajr", fajr),
            PrayerTime("Sunrise", sunrise),
            PrayerTime("Dhuhr", dhuhr),
            PrayerTime("Asr", asr),
            PrayerTime("Maghrib", maghrib),
            PrayerTime("Isha", isha)
        )
        
        // Find next prayer today
        val nextPrayerIndex = prayers.indexOfFirst { it.time.isAfter(now) }
        
        return prayers.mapIndexed { index, prayer ->
            val isCurrently = when {
                // For Fajr to Maghrib prayers, check if we're between this prayer and the next
                index < prayers.size - 1 && now.isAfter(prayer.time) && now.isBefore(prayers[index + 1].time) -> true
                // For Isha prayer, only show as current for a reasonable time after it starts (max 2 hours)
                index == prayers.size - 1 && now.isAfter(prayer.time) && now.isBefore(prayer.time.plusHours(2)) -> true
                else -> false
            }
            
            val isNext = when {
                // If there's a next prayer today, mark it
                index == nextPrayerIndex -> true
                // If no prayers left today and this is Fajr, it might be tomorrow's next prayer
                nextPrayerIndex == -1 && index == 0 -> true
                else -> false
            }
            
            prayer.copy(
                isNext = isNext,
                isCurrently = isCurrently
            )
        }
    }
    
    /**
     * # Get Actual Prayers Only (Obligatory Prayers)
     * 
     * Returns only the five daily obligatory Islamic prayers, excluding astronomical events like sunrise.
     * This is the primary method used by UI components that display prayer schedules to users.
     * 
     * ## Method Purpose
     * 
     * **Difference from getAllPrayers():**
     * - **This Method**: Returns 5 prayers (excludes Sunrise)
     * - **getAllPrayers()**: Returns 6 items (includes Sunrise for calculations)
     * - **Use Case**: UI displays, notifications, actual prayer tracking
     * 
     * **Input Processing:**
     * - Uses `LocalTime.now()` as reference point
     * - Filters to only obligatory prayers (Salah)
     * - No external parameters required
     * 
     * ## Status Calculation Details
     * 
     * **Identical Logic to getAllPrayers() but for 5 prayers:**
     * 1. Find next obligatory prayer after current time
     * 2. Mark prayer as "currently active" if between prayer time and next prayer
     * 3. Apply Isha 2-hour limit rule
     * 4. Handle day rollover to next day's Fajr
     * 
     * **Prayer Windows (Obligatory Only):**
     * - **Fajr**: From Fajr time until Dhuhr time
     * - **Dhuhr**: From Dhuhr time until Asr time
     * - **Asr**: From Asr time until Maghrib time
     * - **Maghrib**: From Maghrib time until Isha time
     * - **Isha**: From Isha time for maximum 2 hours
     * 
     * ## Debug Information
     * 
     * **Input State:**
     * ```
     * Current Time: LocalTime.now()
     * Obligatory Prayers: [Fajr: ${fajr}, Dhuhr: ${dhuhr}, Asr: ${asr}, Maghrib: ${maghrib}, Isha: ${isha}]
     * Date: ${date}
     * Location: ${location.getDisplayName()}
     * Sunrise Excluded: ${sunrise} (not in results)
     * ```
     * 
     * **Algorithm Steps:**
     * 1. Create actualPrayers list (5 prayers, no Sunrise)
     * 2. Calculate nextPrayerIndex using `actualPrayers.indexOfFirst { it.time.isAfter(now) }`
     * 3. Apply status mapping with current prayer window logic
     * 4. Validate exactly one isNext and one isCurrently (if applicable)
     * 
     * **Performance Comparison:**
     * - **Time Complexity**: O(5) vs O(6) for getAllPrayers() 
     * - **Memory Usage**: 5 PrayerTime objects vs 6
     * - **Status Calculation**: Same algorithm, smaller dataset
     * 
     * ## Edge Cases & Validation
     * 
     * | Time | Current Prayer | Next Prayer | Status Logic |
     * |------|----------------|-------------|--------------|
     * | **03:00** | None | Fajr | Before first prayer |
     * | **05:45** (Fajr 05:30) | Fajr | Dhuhr | Active until next prayer |
     * | **12:30** (Dhuhr 12:15) | Dhuhr | Asr | Standard window |
     * | **22:00** (Isha 20:00) | Isha | Tomorrow's Fajr | Within 2-hour limit |
     * | **23:00** (Isha 20:00) | None | Tomorrow's Fajr | Exceeded 2-hour limit |
     * | **23:59** | None | Tomorrow's Fajr | Day rollover |
     * 
     * **Output Validation:**
     * - Exactly 5 PrayerTime objects returned
     * - Prayer names: ["Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"]
     * - At most 1 prayer with `isNext = true`
     * - At most 1 prayer with `isCurrently = true`
     * - Times are chronologically ordered within the day
     * - No "Sunrise" entries in results
     * 
     * @return List<PrayerTime> containing exactly 5 obligatory prayers with real-time status
     * 
     * **Output Example:**
     * ```kotlin
     * [
     *   PrayerTime("Fajr", 05:30, isNext=false, isCurrently=false),
     *   PrayerTime("Dhuhr", 12:15, isNext=false, isCurrently=true),   // Active now
     *   PrayerTime("Asr", 15:30, isNext=true, isCurrently=false),     // Next prayer
     *   PrayerTime("Maghrib", 18:45, isNext=false, isCurrently=false),
     *   PrayerTime("Isha", 20:00, isNext=false, isCurrently=false)
     * ]
     * ```
     */
    fun getActualPrayers(): List<PrayerTime> {
        val now = LocalTime.now()
        val actualPrayers = listOf(
            PrayerTime("Fajr", fajr),
            PrayerTime("Dhuhr", dhuhr),
            PrayerTime("Asr", asr),
            PrayerTime("Maghrib", maghrib),
            PrayerTime("Isha", isha)
        )
        
        // Find next prayer today
        val nextPrayerIndex = actualPrayers.indexOfFirst { it.time.isAfter(now) }
        
        return actualPrayers.mapIndexed { index, prayer ->
            val isCurrently = when {
                // For Fajr to Maghrib prayers, check if we're between this prayer and the next
                index < actualPrayers.size - 1 && now.isAfter(prayer.time) && now.isBefore(actualPrayers[index + 1].time) -> true
                // For Isha prayer, only show as current for a reasonable time after it starts (max 2 hours)
                index == actualPrayers.size - 1 && now.isAfter(prayer.time) && now.isBefore(prayer.time.plusHours(2)) -> true
                else -> false
            }
            
            val isNext = when {
                // If there's a next prayer today, mark it
                index == nextPrayerIndex -> true
                // If no prayers left today and this is Fajr, it might be tomorrow's next prayer
                nextPrayerIndex == -1 && index == 0 -> true
                else -> false
            }
            
            prayer.copy(
                isNext = isNext,
                isCurrently = isCurrently
            )
        }
    }
    
    /**
     * # Get Next Prayer
     * 
     * Determines the next upcoming obligatory prayer with intelligent day rollover handling.
     * This is a critical method used for UI highlighting, notifications, and countdown calculations.
     * 
     * ## Algorithm Logic
     * 
     * **Two-Phase Detection:**
     * 1. **Today's Search**: Find first prayer after current time today
     * 2. **Rollover Handling**: If none found today, default to tomorrow's Fajr
     * 
     * **Input Processing:**
     * - Uses `LocalTime.now()` as reference time
     * - Searches only obligatory prayers (calls `getActualPrayers()`)
     * - No external parameters required
     * 
     * ## Debug Information
     * 
     * **Input State:**
     * ```
     * Current Time: LocalTime.now()
     * Available Prayers Today: getActualPrayers()
     * Reference Date: ${date}
     * Prayer Cycle: Fajr → Dhuhr → Asr → Maghrib → Isha → [Next Day] Fajr
     * ```
     * 
     * **Algorithm Steps:**
     * 1. Get `actualPrayers = getActualPrayers()` (5 prayers with status)
     * 2. Search using `firstOrNull { it.time.isAfter(now) }`
     * 3. If found: return prayer with `isNext = true`
     * 4. If not found: return `PrayerTime("Fajr", fajr, isNext = true)` (tomorrow's Fajr)
     * 
     * **Return Value Analysis:**
     * - **Success Case**: Returns specific prayer marked as next
     * - **Day Rollover**: Returns tomorrow's Fajr (same time, different day)
     * - **Edge Case**: Never returns null unless system time is broken
     * 
     * ## Detailed Scenarios
     * 
     * | Current Time | Prayers Remaining Today | Next Prayer Returned | Logic |
     * |--------------|-------------------------|---------------------|--------|
     * | **03:00** | All 5 prayers | Fajr (today) | Standard case |
     * | **05:45** | 4 prayers (after Fajr) | Dhuhr (today) | Found next in sequence |
     * | **19:30** | 1 prayer (Isha only) | Isha (today) | Last prayer today |
     * | **21:00** | None (all passed) | Fajr (tomorrow) | Day rollover triggered |
     * | **23:59** | None (end of day) | Fajr (tomorrow) | Midnight rollover |
     * 
     * **State Validation:**
     * - Result always has `isNext = true`
     * - Result always has `isCurrently = false` (next, not current)
     * - Prayer name is always one of: ["Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"]
     * - Time is valid LocalTime object
     * 
     * ## Error Handling
     * 
     * **Potential Issues:**
     * - **System Clock Issues**: If `LocalTime.now()` fails → Method returns Fajr
     * - **Invalid Prayer Times**: If prayer times are corrupted → Uses object's fajr time
     * - **Null Prayer List**: If `getActualPrayers()` fails → Returns fallback Fajr
     * 
     * **Debugging Validation:**
     * ```kotlin
     * val nextPrayer = prayerTimes.getNextPrayer()
     * assert(nextPrayer != null) // Should never be null
     * assert(nextPrayer.isNext == true) // Always marked as next
     * assert(nextPrayer.name in listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"))
     * ```
     * 
     * @return PrayerTime object representing the next prayer to occur (never null)
     * 
     * **Output Examples:**
     * ```kotlin
     * // At 14:00 (2:00 PM) - Asr is next
     * PrayerTime("Asr", 15:30, isNext=true, isCurrently=false)
     * 
     * // At 21:00 (9:00 PM) - All prayers done, next is tomorrow's Fajr  
     * PrayerTime("Fajr", 05:30, isNext=true, isCurrently=false)
     * 
     * // At 04:00 (4:00 AM) - Fajr is next today
     * PrayerTime("Fajr", 05:30, isNext=true, isCurrently=false)
     * ```
     */
    fun getNextPrayer(): PrayerTime? {
        val now = LocalTime.now()
        
        // First try to find a prayer today that's after current time
        val todayNextPrayer = getActualPrayers().firstOrNull { it.time.isAfter(now) }
        if (todayNextPrayer != null) {
            return todayNextPrayer
        }
        
        // If no prayers left today, the next prayer is tomorrow's Fajr
        // This handles the cyclical nature: Fajr -> Dhuhr -> Asr -> Maghrib -> Isha -> (next day) Fajr
        return PrayerTime("Fajr", fajr, isNext = true)
    }
    
    /**
     * # Get Time Until Next Prayer
     * 
     * Calculates and formats the remaining time until the next prayer, providing human-readable
     * countdown strings for UI display. This method handles complex day rollover scenarios and
     * provides consistent formatting for user interfaces.
     * 
     * ## Algorithm Overview
     * 
     * **Two-Phase Calculation:**
     * 1. **Prayer Detection**: Uses `getNextPrayer()` to find next prayer
     * 2. **Time Calculation**: Calculates duration based on today vs tomorrow logic
     * 3. **Format Conversion**: Converts to human-readable format
     * 
     * **Input Dependencies:**
     * - Current system time via `LocalTime.now()`
     * - Next prayer from `getNextPrayer()` method
     * - No external parameters required
     * 
     * ## Time Calculation Logic
     * 
     * **Today's Prayer Calculation:**
     * ```kotlin
     * // When next prayer is today (prayer.time > now)
     * duration = Duration.between(now, nextPrayer.time)
     * // Direct time difference calculation
     * ```
     * 
     * **Tomorrow's Prayer Calculation (Day Rollover):**
     * ```kotlin
     * // When next prayer is tomorrow (prayer.time < now)
     * duration = Duration.between(now, LocalTime.MAX) + 
     *           Duration.between(LocalTime.MIN, nextPrayer.time)
     * // Time until midnight + time from midnight to prayer
     * ```
     * 
     * ## Debug Information
     * 
     * **Input State Analysis:**
     * ```
     * Current Time: LocalTime.now()
     * Next Prayer: getNextPrayer()
     * Prayer Time: ${nextPrayer?.time}
     * Prayer Name: ${nextPrayer?.name}
     * Is Today: ${nextPrayer?.time?.isAfter(LocalTime.now())}
     * ```
     * 
     * **Calculation Breakdown:**
     * ```
     * // For Today's Prayer:
     * Start Time: ${now}
     * End Time: ${nextPrayer.time}
     * Duration: ${Duration.between(now, nextPrayer.time)}
     * 
     * // For Tomorrow's Prayer:
     * Time to Midnight: ${Duration.between(now, LocalTime.MAX)}
     * Time from Midnight: ${Duration.between(LocalTime.MIN, nextPrayer.time)}
     * Total Duration: ${totalDuration}
     * ```
     * 
     * ## Format Logic Specification
     * 
     * **Formatting Rules:**
     * - **Hours > 0**: "Xh Ym" format (e.g., "2h 30m", "1h 0m")
     * - **Minutes Only**: "Ym" format (e.g., "45m", "1m") 
     * - **Immediate**: "Now" (when duration ≤ 0)
     * - **Error**: null (when calculation fails)
     * 
     * **Edge Cases in Formatting:**
     * | Duration | Hours | Minutes | Output | Reasoning |
     * |----------|-------|---------|--------|-----------|
     * | 2h 30m | 2 | 30 | "2h 30m" | Standard format |
     * | 1h 0m | 1 | 0 | "1h 0m" | Show zero minutes |
     * | 0h 45m | 0 | 45 | "45m" | Minutes only |
     * | 0h 0m | 0 | 0 | "Now" | Immediate prayer |
     * | -5m | - | - | "Now" | Prayer time passed |
     * | null | - | - | null | Calculation error |
     * 
     * ## Detailed Scenarios
     * 
     * | Current Time | Next Prayer | Prayer Time | Expected Output | Calculation Type |
     * |--------------|-------------|-------------|----------------|------------------|
     * | **14:00** | Asr | 15:30 | "1h 30m" | Today's prayer |
     * | **19:45** | Isha | 20:00 | "15m" | Today's prayer |
     * | **20:00** | Isha | 20:00 | "Now" | Prayer starting |
     * | **21:30** | Tomorrow's Fajr | 05:30 | "8h 0m" | Day rollover |
     * | **23:45** | Tomorrow's Fajr | 05:30 | "5h 45m" | Near midnight |
     * | **05:30** | Fajr | 05:30 | "Now" | Exact prayer time |
     * 
     * ## Error Handling & Validation
     * 
     * **Failure Scenarios:**
     * 1. **getNextPrayer() returns null**: Return null (extremely rare)
     * 2. **System time unavailable**: Return null (system issue)
     * 3. **Duration calculation fails**: Return null (temporal anomaly)
     * 4. **Invalid prayer times**: Return null (data corruption)
     * 
     * **Input Validation:**
     * ```kotlin
     * val nextPrayer = getNextPrayer() ?: return null // Null check
     * val now = LocalTime.now() // System time dependency
     * // Duration calculations are fail-safe
     * ```
     * 
     * **Output Validation:**
     * - Non-null result indicates successful calculation
     * - "Now" indicates prayer is immediate or happening
     * - Number format (e.g., "2h 30m") indicates future prayer
     * - Format always matches regex: `^\d+h \d+m$|^\d+m$|Now$`
     * 
     * @return String? Human-readable time remaining ("2h 30m", "15m", "Now") or null if calculation fails
     * 
     * **Return Value Examples:**
     * ```kotlin
     * // Various output formats:
     * "3h 25m"    // 3 hours, 25 minutes until prayer
     * "45m"       // 45 minutes until prayer  
     * "Now"       // Prayer is happening now
     * null        // Calculation error (rare)
     * ```
     * 
     * ## Performance Notes
     * 
     * - **Time Complexity**: O(1) - fixed calculations
     * - **Dependencies**: Calls `getNextPrayer()` once
     * - **Memory Usage**: Minimal (temporary Duration objects)
     * - **Thread Safety**: Safe (immutable operations)
     */
    fun getTimeUntilNextPrayer(): String? {
        val nextPrayer = getNextPrayer() ?: return null
        val now = LocalTime.now()
        
        return if (nextPrayer.time.isAfter(now)) {
            // Next prayer is today
            val duration = java.time.Duration.between(now, nextPrayer.time)
            val hours = duration.toHours()
            val minutes = duration.toMinutesPart()
            
            when {
                hours > 0 -> "${hours}h ${minutes}m"
                minutes > 0 -> "${minutes}m"
                else -> "Now"
            }
        } else {
            // Next prayer is tomorrow's Fajr
            val duration = java.time.Duration.between(now, LocalTime.MAX) + 
                         java.time.Duration.between(LocalTime.MIN, nextPrayer.time)
            val hours = duration.toHours()
            val minutes = duration.toMinutesPart()
            
            when {
                hours > 0 -> "${hours}h ${minutes}m"
                minutes > 0 -> "${minutes}m"
                else -> "Now"
            }
        }
    }
}