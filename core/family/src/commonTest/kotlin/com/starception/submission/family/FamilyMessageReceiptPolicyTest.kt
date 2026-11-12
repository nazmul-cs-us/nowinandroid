package com.starception.submission.family

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FamilyMessageReceiptPolicyTest {
    private val policy = FamilyMessageReceiptPolicy()
    @Test fun receiptStatesUseRecipientEvidence() {
        assertEquals("sent", policy.status(0, 0))
        assertEquals("delivered", policy.status(100, 0))
        assertEquals("seen", policy.status(100, 200))
        assertEquals("Seen", policy.label(0, 200))
        assertEquals("Delivered", policy.label(100, 0))
        assertEquals("Sent", policy.label(0, 0))
    }
    @Test fun viewportExcludesPrefetchedAndBarelyVisibleRows() {
        assertFalse(policy.visible(-100f, 100f, 0f, 500f))
        assertFalse(policy.visible(500f, 100f, 0f, 500f))
        assertFalse(policy.visible(451f, 100f, 0f, 500f))
        assertTrue(policy.visible(450f, 100f, 0f, 500f))
        assertTrue(policy.visible(0f, 100f, 0f, 500f))
    }
    @Test fun tallMessagesCanBeSeenWithoutFittingTheScreen() {
        assertTrue(policy.visible(-100f, 1500f, 0f, 500f))
        assertFalse(policy.visible(0f, 0f, 0f, 500f))
        assertFalse(policy.visible(0f, 100f, 0f, 0f))
    }
}
