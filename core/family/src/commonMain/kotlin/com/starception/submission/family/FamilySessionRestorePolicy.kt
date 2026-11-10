package com.starception.submission.family

/** Sign-out and rejected credentials suppress automatic retry until manual sign-in. */
class FamilySessionRestorePolicy {
    private var blocked = false
    fun canRestore(connected: Boolean, busy: Boolean): Boolean = !blocked && !connected && !busy
    fun explicitSignOut() { blocked = true }
    fun authenticationRejected() { blocked = true }
    fun manualSignIn() { blocked = false }
}
