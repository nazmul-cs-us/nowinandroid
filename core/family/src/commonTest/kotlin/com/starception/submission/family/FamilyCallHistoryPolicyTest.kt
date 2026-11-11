package com.starception.submission.family

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FamilyCallHistoryPolicyTest {
    private val policy = FamilyCallHistoryPolicy()

    @Test fun cancelledAndExpiredCallsHaveParticipantSpecificLabels() {
        assertEquals("Cancelled", policy.status("cancelled", true))
        assertEquals("Missed", policy.status("cancelled", false))
        assertEquals("No answer", policy.status("missed", true))
        assertEquals("Missed", policy.status("missed", false))
        assertFalse(policy.missed("declined", false))
        assertTrue(policy.missed("cancelled", false))
    }

    @Test fun formatsCallDurationWithoutInventingATimer() {
        assertEquals("0s", policy.duration(0))
        assertEquals("59s", policy.duration(59))
        assertEquals("1m 34s", policy.duration(94))
        assertEquals("1h 2m", policy.duration(3720))
    }

    @Test fun acceptsOnlyTheSupportedRecordTypes() {
        assertTrue(policy.valid("voice", "answered"))
        assertTrue(policy.valid("video", "interrupted"))
        assertFalse(policy.valid("text", "answered"))
        assertFalse(policy.valid("voice", "unknown"))
    }
}
