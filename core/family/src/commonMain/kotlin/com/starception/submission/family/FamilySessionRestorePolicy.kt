package com.starception.submission.family

/** Sign-out and rejected credentials suppress automatic retry until manual sign-in. */
class FamilySessionRestorePolicy {
    private var blocked = false
    fun canRestore(connected: Boolean, busy: Boolean): Boolean = !blocked && !connected && !busy
    fun retryDelayMillis(attempt: Int): Long = listOf(1000L, 2000L, 4000L, 8000L, 15000L, 30000L)[attempt.coerceIn(0, 5)]
    fun explicitSignOut() { blocked = true }
    fun authenticationRejected() { blocked = true }
    fun manualSignIn() { blocked = false }
}
