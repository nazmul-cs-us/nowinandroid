package com.starception.submission.auth

/** Sign-in state surfaced to the profile UI. */
sealed interface AuthUiState {
    /** Initial state before persisted tokens have been read. */
    data object Loading : AuthUiState

    /** No valid session. */
    data object LoggedOut : AuthUiState

    /** Authenticated; fields come from the ID-token claims. */
    data class LoggedIn(
        val displayName: String?,
        val email: String?,
        val avatarUrl: String?,
    ) : AuthUiState
}
