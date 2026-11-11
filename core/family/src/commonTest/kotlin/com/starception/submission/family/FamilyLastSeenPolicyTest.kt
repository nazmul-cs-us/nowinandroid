package com.starception.submission.family

import kotlin.test.*

class FamilyLastSeenPolicyTest {
    private val policy = FamilyLastSeenPolicy()
    @Test fun displaysOnlyKnownOfflineTimesWhileTheServiceIsAvailable() {
        assertEquals("Status unavailable", policy.label("offline", 1000, 121000, false))
        assertEquals("Offline", policy.label("offline", 0, 121000, true))
        assertEquals("Online", policy.label("online", 1000, 121000, true))
        assertEquals("Last seen 2 minutes ago", policy.label("offline", 1000, 121000, true))
        assertEquals("Last seen 1 hour ago", policy.label("offline", 1000, 3601000, true))
        assertEquals("Last seen 2 days ago", policy.label("offline", 1000, 172801000, true))
    }
    @Test fun cacheRejectsStaleTimestampsAndDropsThemWhenFamilyAccessIsRemoved() {
        val cache = FamilyPresenceCache()
        cache.beginEpoch("one")
        assertTrue(cache.apply("alice", "offline", 2, 1000))
        assertFalse(cache.apply("alice", "offline", 1, 500))
        assertEquals(1000, cache.lastSeenAt("alice"))
        cache.apply("alice", "online", 3)
        assertEquals(0, cache.lastSeenAt("alice"))
        cache.apply("alice", "offline", 4, 2000)
        cache.retain(emptyList())
        assertEquals(0, cache.lastSeenAt("alice"))
    }
}
