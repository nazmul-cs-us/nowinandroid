package com.starception.submission.family

import kotlin.test.*

class FamilySessionRestorePolicyTest {
    @Test fun launchCanRestoreButDoesNotDuplicateAnExistingLogin() {
        val policy = FamilySessionRestorePolicy()
        assertTrue(policy.canRestore(false, false))
        assertFalse(policy.canRestore(true, false))
        assertFalse(policy.canRestore(false, true))
    }
    @Test fun signOutBlocksForegroundRestorationUntilManualSignIn() {
        val policy = FamilySessionRestorePolicy()
        policy.explicitSignOut()
        assertFalse(policy.canRestore(false, false))
        policy.manualSignIn()
        assertTrue(policy.canRestore(false, false))
    }
    @Test fun rejectedCredentialsAreNotRetriedOnEveryResume() {
        val policy = FamilySessionRestorePolicy()
        policy.authenticationRejected()
        repeat(5) { assertFalse(policy.canRestore(false, false)) }
        policy.manualSignIn()
        assertTrue(policy.canRestore(false, false))
    }
}
