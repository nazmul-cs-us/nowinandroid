package com.starception.submission.family

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FamilyCallLinkTest {
    private val link = FamilyCallLink()
    private val id = "11111111-2222-3333-4444-555555555555"
    @Test fun acceptsOnlyTheCanonicalCallLink() {
        assertEquals(id, link.callId("deenly://family-call?callId=$id"))
        assertEquals("abcdefab-cdef-abcd-efab-cdefabcdefab", link.callId("deenly://family-call?callId=ABCDEFAB-CDEF-ABCD-EFAB-CDEFABCDEFAB"))
        listOf("https://attacker.test?callId=$id", "deenly:opaque", "deenly://family-call?callId=bad",
            "deenly://family-call?callId=$id&answer=true", "deenly://family-call?answer=true&callId=$id",
            "deenly://family-call?callId=$id#answer", "deenly://family-call").forEach { assertNull(link.callId(it)) }
    }
}
