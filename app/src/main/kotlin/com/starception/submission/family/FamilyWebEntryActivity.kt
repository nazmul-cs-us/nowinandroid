package com.starception.submission.family

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Public deep links never forward extras or auto-answer; the server authenticates the call. */
@AndroidEntryPoint
class FamilyWebEntryActivity : ComponentActivity() {
    @Inject lateinit var client: FamilyClient
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } } }
        val id = if (intent.action == Intent.ACTION_VIEW) FamilyCallLink().callId(intent.dataString.orEmpty()) else null
        lifecycleScope.launch {
            if (id != null && client.resumeForPush(id)) {
                startActivity(Intent(this@FamilyWebEntryActivity, FamilyIncomingActivity::class.java).putExtra("callId", id))
            } else {
                Toast.makeText(this@FamilyWebEntryActivity, "Open Family to sign in or call back. This call may have ended.", Toast.LENGTH_LONG).show()
                startActivity(Intent(this@FamilyWebEntryActivity, com.starception.submission.MainActivity::class.java))
            }
            finish()
        }
    }
}
