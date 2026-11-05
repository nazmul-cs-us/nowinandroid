package com.starception.submission.family

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FamilyPresenceCacheTest {
    @Test fun lateSnapshotsCannotOverwriteLivePresence() {
        val cache = FamilyPresenceCache()
        cache.beginEpoch("one")
        assertTrue(cache.apply("alice", "online", 10))
        assertFalse(cache.apply("alice", "offline", 9))
        assertEquals("online", cache.status("alice"))
        assertTrue(cache.apply("alice", "offline", 11))
        assertFalse(cache.apply("alice", "online", 10))
        assertEquals("offline", cache.status("alice"))
    }
    @Test fun reconnectAndServerRestartDiscardOldAvailability() {
        val cache = FamilyPresenceCache()
        cache.beginEpoch("one"); cache.apply("alice", "online", 100)
        cache.beginEpoch("two")
        assertEquals("unknown", cache.status("alice"))
        assertTrue(cache.apply("alice", "offline", 1))
        cache.clear()
        assertEquals("unknown", cache.status("alice"))
    }
    @Test fun familyRemovalAndInvalidUpdatesCannotExposeAvailability() {
        val cache = FamilyPresenceCache()
        cache.apply("alice", "online", 1)
        cache.retain(emptyList())
        assertEquals("unknown", cache.status("alice"))
        assertFalse(cache.apply("alice", "online", 0))
        assertFalse(cache.apply("alice", "unrecognized", 2))
        assertEquals("unknown", cache.status("alice"))
    }
}
