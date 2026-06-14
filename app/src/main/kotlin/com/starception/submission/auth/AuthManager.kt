package com.starception.submission.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Sign-in engine backed by Firebase Authentication. Google goes through Credential
 * Manager; Microsoft/Facebook/Apple use Firebase's hosted OAuth ("web") flow, which
 * handles the browser round-trip internally — so no custom redirect scheme, App Links,
 * or assetlinks.json are needed. App-wide singleton.
 *
 * Requires a real Firebase project: replace the placeholder app/google-services.json with
 * the one from console.firebase.google.com and enable the providers there.
 */
@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    // Null when no real Firebase project is configured (the repo ships a placeholder
    // google-services.json). Guarding here keeps the app from crashing on launch.
    private val auth: FirebaseAuth? = runCatching { FirebaseAuth.getInstance() }.getOrNull()

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Loading)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    /** Transient human-readable errors for the UI to surface (e.g. a toast). */
    private val _messages = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        auth?.addAuthStateListener { emitState(it.currentUser) }
        emitState(auth?.currentUser)
    }

    /** Microsoft (`microsoft.com`), Facebook (`facebook.com`), Apple (`apple.com`). */
    suspend fun signInWithProvider(activity: Activity, providerId: String) {
        val firebaseAuth = auth ?: run {
            _messages.tryEmit(NOT_CONFIGURED)
            return
        }
        try {
            val pending = firebaseAuth.pendingAuthResult
            if (pending != null) {
                pending.await()
            } else {
                val provider = OAuthProvider.newBuilder(providerId).build()
                firebaseAuth.startActivityForSignInWithProvider(activity, provider).await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Provider sign-in failed: $providerId", e)
            _messages.tryEmit("Sign-in failed: ${e.localizedMessage ?: providerId}")
        }
    }

    /** Google via Credential Manager → Firebase. */
    suspend fun signInWithGoogle(activity: Activity) {
        val firebaseAuth = auth ?: run {
            _messages.tryEmit(NOT_CONFIGURED)
            return
        }
        val webClientId = resolveWebClientId()
        if (webClientId == null) {
            _messages.tryEmit("Google sign-in isn't configured yet (enable Google in Firebase and add the app SHA-1).")
            return
        }
        try {
            // Explicit "Continue with Google" button → show the full account picker.
            // (GetGoogleIdOption is the stricter one-tap variant and returns
            // NoCredentialException when no account has been authorized yet.)
            val option = GetSignInWithGoogleOption.Builder(webClientId).build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val response = CredentialManager.create(context).getCredential(activity, request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleId = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCredential = GoogleAuthProvider.getCredential(googleId.idToken, null)
                firebaseAuth.signInWithCredential(firebaseCredential).await()
            } else {
                _messages.tryEmit("Unexpected Google credential type.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Google sign-in failed", e)
            _messages.tryEmit("Google sign-in failed: ${e.localizedMessage ?: "unknown error"}")
        }
    }

    fun signOut() {
        auth?.signOut()
        emitState(null)
    }

    /** Firebase UID of the signed-in user, or null when signed out / unconfigured. */
    fun currentUid(): String? = auth?.currentUser?.uid

    private fun emitState(user: FirebaseUser?) {
        _uiState.value = if (user == null) {
            AuthUiState.LoggedOut
        } else {
            AuthUiState.LoggedIn(
                displayName = user.displayName,
                email = user.email,
                avatarUrl = user.photoUrl?.toString(),
            )
        }
    }

    /**
     * The google-services plugin generates `default_web_client_id` only when the
     * google-services.json has a web OAuth client (i.e. Google sign-in is enabled).
     * Resolve it by name so the app still compiles with the placeholder config.
     */
    private fun resolveWebClientId(): String? {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (id != 0) context.getString(id).takeIf { it.isNotBlank() } else null
    }

    companion object {
        private const val TAG = "AuthManager"
        private const val NOT_CONFIGURED =
            "Sign-in unavailable: add a real google-services.json from your Firebase project."
    }
}

/** Suspends until a Play Services [Task] completes. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
