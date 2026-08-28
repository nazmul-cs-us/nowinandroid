package com.starception.submission.util

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TravelDuaPolicyTest {

    @Test
    fun trafficStopShorterThanConfiguredGap_isSameTrip() {
        assertTrue(
            TravelDuaPolicy.isWithinTripGap(
                drivingStopTimeMillis = 1_000L,
                nowMillis = 300_999L,
                gapToleranceMillis = 300_000L,
            ),
        )
    }

    @Test
    fun configuredGapBoundary_startsNewTrip() {
        assertFalse(
            TravelDuaPolicy.isWithinTripGap(
                drivingStopTimeMillis = 1_000L,
                nowMillis = 301_000L,
                gapToleranceMillis = 300_000L,
            ),
        )
    }

    @Test
    fun recentReliableSpeed_allowsPlayback() {
        assertTrue(
            TravelDuaPolicy.hasRecentDrivingEvidence(
                nowElapsedMillis = 100_000L,
                lastEvidenceElapsedMillis = 55_000L,
            ),
        )
    }

    @Test
    fun staleOrFutureSpeedEvidence_blocksPlayback() {
        assertFalse(
            TravelDuaPolicy.hasRecentDrivingEvidence(
                nowElapsedMillis = 100_001L,
                lastEvidenceElapsedMillis = 55_000L,
            ),
        )
        assertFalse(
            TravelDuaPolicy.hasRecentDrivingEvidence(
                nowElapsedMillis = 10_000L,
                lastEvidenceElapsedMillis = 20_000L,
            ),
        )
    }

    @Test
    fun activeGoogleDrivingConfirmation_allowsPlaybackWithoutRecentGps() {
        assertTrue(
            TravelDuaPolicy.hasActiveGoogleDrivingEvidence(
                nowElapsedMillis = 200_000L,
                googleDrivingConfirmed = true,
                confirmationElapsedMillis = 20_000L,
            ),
        )
    }

    @Test
    fun exitedOrStaleGoogleDrivingConfirmation_blocksPlayback() {
        assertFalse(
            TravelDuaPolicy.hasActiveGoogleDrivingEvidence(
                nowElapsedMillis = 200_000L,
                googleDrivingConfirmed = false,
                confirmationElapsedMillis = 20_000L,
            ),
        )
        assertFalse(
            TravelDuaPolicy.hasActiveGoogleDrivingEvidence(
                nowElapsedMillis = 320_001L,
                googleDrivingConfirmed = true,
                confirmationElapsedMillis = 20_000L,
            ),
        )
    }
}
