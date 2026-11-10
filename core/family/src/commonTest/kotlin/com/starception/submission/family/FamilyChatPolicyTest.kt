package com.starception.submission.family

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class FamilyChatPolicyTest {
    private val policy = FamilyChatPolicy()
    @Test fun acceptsArabicEmojiAndLineBreaks() { assertTrue(policy.valid("السلام عليكم 👋\nHow are you?")) }
    @Test fun limitsBytesRatherThanCharacterCount() {
        assertTrue(policy.valid("a".repeat(4096)))
        assertFalse(policy.valid("a".repeat(4097)))
        assertFalse(policy.valid("س".repeat(2049)))
    }
    @Test fun rejectsEmptyAndControlMessages() {
        listOf("", "   \n", "x\u0000", "x\u007f").forEach { assertFalse(policy.valid(it)) }
    }
}
