package com.starception.submission

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.starception.submission.ui.DeenlySparkSplash

/** Kept as the launcher alias target so existing launcher icon selections keep working. */
class VideoSplashActivity : ComponentActivity() {
    private var navigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Returning to an existing task should resume the user's screen immediately.
        if (!isTaskRoot) {
            navigateToMain()
            return
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent { DeenlySparkSplash(onFinished = ::navigateToMain) }
    }

    private fun navigateToMain() {
        if (navigated || isFinishing) return
        navigated = true
        // Preserve extras/data for launchers or shortcuts that attach a destination.
        startActivity(Intent(intent).apply {
            setClass(this@VideoSplashActivity, MainActivity::class.java)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
