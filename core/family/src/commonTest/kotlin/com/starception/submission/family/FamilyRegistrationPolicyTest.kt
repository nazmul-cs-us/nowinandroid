package com.starception.submission.family

import kotlin.test.*

class FamilyRegistrationPolicyTest {
    private val policy = FamilyRegistrationPolicy()
    @Test fun normalizesOnlyValidLocalUsernames() {
        assertEquals("amina.khan", policy.username(" Amina.Khan "))
        listOf("ab", "x".repeat(33), "-amina", "a@other.test", "a/b", "a b", "admin", "ios", "android")
            .forEach { assertNull(policy.username(it)) }
    }
    @Test fun validatesConfirmationAndPasswordWithoutChangingItsContents() {
        assertNull(policy.validate("amina", "a secure password", "a secure password"))
        assertEquals("password_mismatch", policy.validate("amina", "a secure password", "other password"))
        listOf("short", " ".repeat(12), "x".repeat(129), "line-break\npassword", "control\u0000password")
            .forEach { assertEquals("invalid_password", policy.validate("amina", it, it)) }
    }
    @Test fun providesDistinctActionableServerErrors() {
        assertTrue(policy.message("conflict").contains("already taken"))
        assertTrue(policy.message("resource-constraint").contains("Wait a minute"))
        assertTrue(policy.message("not-allowed").contains("unavailable"))
    }
}
