package com.starception.submission.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authManager: AuthManager,
) : ViewModel() {

    val uiState: StateFlow<AuthUiState> = authManager.uiState
    val messages: SharedFlow<String> = authManager.messages

    /** Provider key from the profile sheet → the right Firebase flow. */
    fun signIn(activity: Activity, provider: String) {
        viewModelScope.launch {
            if (provider == PROVIDER_GOOGLE) {
                authManager.signInWithGoogle(activity)
            } else {
                authManager.signInWithProvider(activity, provider)
            }
        }
    }

    fun signOut() = authManager.signOut()

    companion object {
        const val PROVIDER_GOOGLE = "google"
        const val PROVIDER_MICROSOFT = "microsoft.com"
        const val PROVIDER_FACEBOOK = "facebook.com"
    }
}
