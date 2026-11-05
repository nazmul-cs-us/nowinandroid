package com.starception.submission.family

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** User-initiated answer opens an Activity before requesting microphone/camera. */
@AndroidEntryPoint
class FamilyIncomingActivity : ComponentActivity() {
    @Inject lateinit var client: FamilyClient
    private var answerRequested by mutableStateOf(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true); setTurnScreenOn(true)
        answerRequested = intent.getBooleanExtra("answer", false)
        setContent {
            MaterialTheme {
                val state by client.state.collectAsStateWithLifecycle()
                val permission = permissionRequest()
                LaunchedEffect(state.call?.id, answerRequested) {
                    val call = state.call
                    if (call == null) finish()
                    else if (answerRequested && call.id == intent.getStringExtra("callId") && call.stage == "ringing") {
                        answerRequested = false
                        permission(call.kind == "video") { client.acceptCall() }
                    }
                }
                FamilyCallOverlay(client)
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        answerRequested = intent.getBooleanExtra("answer", false)
    }
}
