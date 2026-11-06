package com.starception.submission.family

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FamilyPolicyTest {
    @Test fun emptySnapshotStartedBeforeIncomingCallCannotDismissIt() {
        val reducer = FamilyCallReducer()
        val request = reducer.snapshotRevision
        assertTrue(reducer.apply("incoming", 1, "ringing", 100, 0))
        assertFalse(reducer.canClearFromSnapshot(request))
    }

    @Test fun emptySnapshotCannotUndoAnAnswerReceivedWhileItWasInFlight() {
        val reducer = FamilyCallReducer()
        reducer.apply("incoming", 1, "ringing", 100, 0)
        val request = reducer.snapshotRevision
        reducer.apply("incoming", 2, "active", 100, 1)
        assertFalse(reducer.canClearFromSnapshot(request))
        assertTrue(reducer.canClearFromSnapshot(reducer.snapshotRevision))
    }

    @Test fun currentEmptySnapshotCanRecoverAMissedEndEvent() {
        val reducer = FamilyCallReducer()
        reducer.apply("incoming", 1, "ringing", 100, 0)
        val request = reducer.snapshotRevision
        assertTrue(reducer.canClearFromSnapshot(request))
        reducer.finishCall("incoming")
        assertFalse(reducer.canClearFromSnapshot(request))
        reducer.clear()
        reducer.apply("next-login", 1, "ringing", 100, 0)
        assertFalse(reducer.canClearFromSnapshot(request))
    }

    @Test fun invitationsDoNotGrantCallingRights() {
        val policy = FamilyPolicy()
        listOf("incoming", "outgoing", "removed", "blocked", "").forEach { assertFalse(policy.canCall(it)) }
        assertTrue(policy.canCall("accepted"))
    }

    @Test fun validatesBareJids() {
        val policy = FamilyPolicy()
        assertEquals("ios-test@xmpp.starception.com", policy.normalizeJid(" IOS-test@xmpp.starception.com "))
        listOf("a@b/device", "a@@b", "@b", "a b@c", "a<b@c").forEach { assertNull(policy.normalizeJid(it)) }
    }

    @Test fun terminalStateRejectsDelayedEvents() {
        val reducer = FamilyCallReducer()
        assertTrue(reducer.apply("one", 1, "ringing", 100, 0))
        assertTrue(reducer.apply("one", 2, "ended", 100, 1))
        assertFalse(reducer.apply("one", 3, "active", 100, 2))
        assertFalse(reducer.apply("one", 1, "ringing", 100, 2))
        assertTrue(reducer.apply("two", 1, "ringing", 100, 2))
    }

    @Test fun expiresRingingAndRejectsConcurrentCalls() {
        val reducer = FamilyCallReducer()
        assertTrue(reducer.apply("one", 1, "ringing", 100, 0))
        assertFalse(reducer.apply("two", 1, "ringing", 100, 0))
        val expired = FamilyCallReducer()
        assertTrue(expired.apply("old", 1, "ringing", 10, 20))
        assertEquals("ended", expired.stage)
    }
    @Test fun finishingAnUnselectedSnapshotReleasesTheCallSlot() {
        val reducer = FamilyCallReducer()
        assertTrue(reducer.apply("answered-elsewhere", 2, "active", 100, 0))
        reducer.finishCall("answered-elsewhere")
        assertTrue(reducer.apply("new-call", 1, "ringing", 100, 1))
    }
}
