# Islamic Prayer Times Calculation Methodology

This document provides a comprehensive explanation of how the Starception Submission app calculates Islamic prayer times using precise astronomical algorithms.

## Table of Contents

1. [Overview](#overview)
2. [Astronomical Foundation](#astronomical-foundation)
3. [Prayer Time Definitions](#prayer-time-definitions)
4. [Calculation Methods](#calculation-methods)
5. [Step-by-Step Calculation Process](#step-by-step-calculation-process)
6. [Advanced Features](#advanced-features)
7. [Implementation Details](#implementation-details)

## Overview

Islamic prayer times are based on the position of the sun relative to the Earth and the observer's geographic location. Our implementation uses modern astronomical algorithms to achieve high precision while following traditional Islamic jurisprudence principles.

### Core Principles

1. **Astronomical Accuracy**: All calculations use precise astronomical formulas
2. **Geographic Precision**: Accounts for exact coordinates, altitude, and timezone
3. **Method Flexibility**: Supports multiple Islamic calculation methods
4. **Edge Case Handling**: Robust handling of polar regions and calculation failures

## Astronomical Foundation

### Julian Day Number System

We use the Julian Day Number (JD) system for astronomical calculations:

```
Julian Day = Continuous day count since January 1, 4713 BCE (proleptic Julian calendar)
```

**Key Benefits:**
- Eliminates calendar system complexities
- Provides consistent astronomical time reference
- Simplifies leap year and month-length calculations

**Implementation:** `AstronomicalCalculator.calculateJulianDay()`

### Solar Position Calculations

#### 1. Solar Declination (δ)
The angle between the sun's rays and the Earth's equatorial plane:

```
δ = arcsin(sin(ε) × sin(λ))
```

Where:
- ε = obliquity of the ecliptic (≈ 23.439°)
- λ = apparent longitude of the sun

#### 2. Equation of Time (EoT)
Correction for Earth's elliptical orbit and axial tilt:

```
EoT = (Right Ascension - Mean Solar Longitude) × 4 minutes
```

This accounts for the sun's apparent "fast" and "slow" periods throughout the year.

#### 3. Hour Angle (H)
The angular displacement of the sun east or west of the local meridian:

```
cos(H) = (sin(altitude) - sin(φ) × sin(δ)) / (cos(φ) × cos(δ))
```

Where:
- φ = observer's latitude
- altitude = required sun elevation angle for specific prayer

## Prayer Time Definitions

### 1. Fajr (Pre-dawn Prayer)
**Definition:** When the sun is between 15° and 19.5° below the horizon (varies by method)

**Astronomical Criteria:**
- Begin of astronomical dawn (twilight begins)
- Sun's upper limb is at the specified depression angle
- Calculated using negative altitude angle

**Formula:**
```
Fajr Time = Solar Noon - (Hour Angle for Fajr Depression Angle) / 15°
```

### 2. Sunrise (Shurūq)
**Definition:** When the sun's upper limb appears above the geometric horizon

**Astronomical Criteria:**
- Sun's center at -0.833° (geometric horizon)
- Includes atmospheric refraction correction
- Additional correction for observer's altitude above sea level

**Formula:**
```
Sunrise Time = Solar Noon - (Hour Angle for -0.833°) / 15°
Altitude Correction = -0.0347 × √(altitude in meters)
```

### 3. Dhuhr (Noon Prayer)
**Definition:** When the sun reaches its highest point in the sky

**Astronomical Criteria:**
- Solar noon (sun crosses local meridian)
- Corrected by Equation of Time
- Adjusted for longitude and timezone

**Formula:**
```
Dhuhr Time = 12:00 + TimeZoneOffset - (Longitude / 15°) - (EoT / 60)
```

### 4. Asr (Afternoon Prayer)
**Definition:** When shadow length equals object height plus noon shadow

**Two Methods:**
- **Standard (Shafi'i, Maliki, Hanbali):** Shadow factor = 1
- **Hanafi:** Shadow factor = 2

**Astronomical Calculation:**
```
Asr Altitude = arctan(1 / (Shadow Factor + tan(|φ - δ|)))
Asr Time = Solar Noon + (Hour Angle for Asr Altitude) / 15°
```

### 5. Maghrib (Sunset Prayer)
**Definition:** When the sun's upper limb disappears below the geometric horizon

**Astronomical Criteria:**
- Same angle as sunrise (-0.833°)
- Includes atmospheric refraction and altitude corrections
- Some methods add 4-minute offset

**Formula:**
```
Maghrib Time = Solar Noon + (Hour Angle for -0.833°) / 15°
```

### 6. Isha (Night Prayer)
**Definition:** When the sun is sufficiently below horizon for complete darkness

**Two Calculation Methods:**

#### A. Angle-Based Methods
```
Isha Time = Solar Noon + (Hour Angle for Isha Depression Angle) / 15°
```

#### B. Fixed Interval Methods
```
Isha Time = Maghrib Time + Fixed Minutes
```

Common angles: -15° to -18°, Common intervals: 60-120 minutes

## Calculation Methods

### Comparison Table

| Method | Fajr Angle | Isha Angle/Interval | Maghrib Offset | Primary Regions |
|--------|------------|-------------------|----------------|-----------------|
| **Muslim World League** | -18.0° | -17.0° | 0 min | Europe, Far East, parts of US |
| **ISNA** | -15.0° | -15.0° | 0 min | North America |
| **Umm al-Qura** | -18.5° | 90 min after Maghrib | 0 min | Saudi Arabia |
| **Egyptian Authority** | -19.5° | -17.5° | 0 min | Egypt, Syria, Iraq, Lebanon |
| **University of Karachi** | -18.0° | -18.0° | 0 min | Pakistan, Bangladesh, India |
| **MUIS (Singapore)** | -20.0° | -18.0° | 0 min | Singapore, Malaysia, Brunei |
| **Shia Ithna Ashari** | -16.0° | -14.0° | +4 min | Shia communities |
| **Tehran University** | -17.7° | -14.0° | +4 min | Iran |

### Method Selection Logic

The app automatically suggests the most appropriate method based on the user's location:

```kotlin
fun getMethodForCountry(countryCode: String): CalculationMethod {
    return when (countryCode.uppercase()) {
        "SA", "AE", "KW", "QA", "BH", "OM" -> UMM_AL_QURA
        "EG", "SY", "IQ", "LB", "JO" -> EGYPTIAN_AUTHORITY
        "PK", "BD", "IN", "AF" -> UNIVERSITY_OF_ISLAMIC_SCIENCES
        "US", "CA", "MX" -> ISNA
        "SG", "MY", "BN" -> MUIS
        "IR" -> INSTITUTE_OF_GEOPHYSICS_TEHRAN
        else -> MUSLIM_WORLD_LEAGUE // Global default
    }
}
```

## Step-by-Step Calculation Process

### Phase 1: Input Preparation
```
1. Validate geographic coordinates (-90° ≤ lat ≤ 90°, -180° ≤ lng ≤ 180°)
2. Convert date to Julian Day Number
3. Load user's calculation method and preferences
```

### Phase 2: Fundamental Solar Calculations
```
4. Calculate solar declination for the date
5. Calculate equation of time correction
6. Determine solar noon time for the location
7. Calculate sunrise and sunset times (geometric horizon)
```

### Phase 3: Prayer-Specific Calculations
```
8. Fajr: Calculate hour angle for method's depression angle
9. Asr: Determine shadow-based altitude and calculate time
10. Isha: Apply either angle-based or interval-based calculation
```

### Phase 4: Adjustments and Finalization
```
11. Apply user-defined time offsets for each prayer
12. Handle high-latitude adjustments if necessary
13. Convert decimal hours to LocalTime objects
14. Validate all times are within 24-hour range
```

### Phase 5: Error Handling and Fallbacks
```
15. Check for NaN/invalid results
16. Apply high-latitude adjustments if standard calculation fails
17. Use previous day's times with warnings if all calculations fail
```

## Advanced Features

### High Latitude Adjustments

For locations above ~48° latitude where the sun may not reach required depression angles:

#### 1. Middle of the Night Method
```
Night Duration = Sunset to Sunrise
Fajr = Sunset + (Night Duration × 1/7)
Isha = Sunset + (Night Duration × 6/7)
```

#### 2. One-Seventh of Night Method
```
Fajr = Sunrise - (Night Duration × 1/7)
Isha = Sunset + (Night Duration × 1/7)
```

#### 3. Angle-Based Method
```
Use proportional angle adjustments based on available daylight
```

#### 4. Nearest Latitude Method
```
Use calculations from the nearest latitude where normal calculation works
```

### Atmospheric Refraction Corrections

Standard atmospheric refraction correction for horizon calculations:
```
Refraction Correction = -0.833° (for sea level)
Additional Altitude Correction = -0.0347 × √(altitude_meters)
```

### Custom Time Offsets

Users can apply individual adjustments to each prayer time:
```
Final Prayer Time = Calculated Time + User Offset (in minutes)
```

### Timezone Handling

Automatic timezone detection and manual override support:
```
Local Solar Time = UTC Time + Timezone Offset - Longitude Correction
```

## Implementation Details

### Core Classes

1. **`AstronomicalCalculator`**: Low-level astronomical calculations
   - Julian Day conversion
   - Solar position algorithms
   - Hour angle calculations

2. **`PrayerTimeCalculatorService`**: Main coordination logic
   - Orchestrates complete calculation flow
   - Applies Islamic prayer rules
   - Handles user preferences

3. **`CalculationMethod`**: Method definitions and parameters
   - Defines depression angles for each method
   - Regional method recommendations
   - Madhab variations

### Performance Optimizations

1. **Caching Strategy**: Store calculated times for instant app startup
2. **Background Processing**: All calculations run on background threads
3. **Smart Updates**: Only recalculate when location or date changes significantly

### Accuracy Validation

Our calculations have been validated against:
- Islamic finder prayer times
- Major mosque schedules worldwide
- Astronomical calculation references
- Government religious authority times

### Error Handling

Comprehensive error handling ensures the app never crashes:
1. Input validation prevents invalid coordinates
2. Calculation timeouts prevent infinite loops
3. Fallback mechanisms provide reasonable defaults
4. High-latitude adjustments handle edge cases

---

## References

1. **Astronomical Algorithms** by Jean Meeus
2. **Islamic Society of North America (ISNA)** calculation guidelines
3. **University of Islamic Sciences, Karachi** methodology
4. **Umm al-Qura University** official calculation method
5. **International astronomical standards** for solar position calculations

This methodology ensures accurate, reliable prayer times for Muslims worldwide while maintaining compatibility with traditional Islamic jurisprudence principles.