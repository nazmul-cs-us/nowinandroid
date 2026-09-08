package com.starception.submission.prayer.service

import kotlin.test.Test
import kotlin.test.assertEquals

class PrayerTimeZoneResolverTest {

    @Test
    fun bangladeshCoordinatesUseUtcPlusSix() {
        assertEquals(6.0, resolvePrayerTimeZoneOffset(23.8103, 90.4125, "BD")) // Dhaka
        assertEquals(6.0, resolvePrayerTimeZoneOffset(22.8714837, 91.2704086, "BD")) // Basurhat
    }

    @Test
    fun nearbyIndiaCoordinatesStillUseUtcPlusFiveThirty() {
        assertEquals(5.5, resolvePrayerTimeZoneOffset(22.5726, 88.3639, "IN")) // Kolkata
    }

    @Test
    fun existingCountryMappingsRemainUnchanged() {
        assertEquals(4.0, resolvePrayerTimeZoneOffset(25.2048, 55.2708, "AE"))
        assertEquals(3.0, resolvePrayerTimeZoneOffset(24.7136, 46.6753, "SA"))
        assertEquals(5.0, resolvePrayerTimeZoneOffset(24.8607, 67.0011, "PK"))
        assertEquals(2.0, resolvePrayerTimeZoneOffset(30.0444, 31.2357, "EG"))
        assertEquals(8.0, resolvePrayerTimeZoneOffset(3.1390, 101.6869, "MY"))
    }
}
