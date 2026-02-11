/*
 * Copyright 2024 Starception
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.starception.submission

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * VIDEO SPLASH ACTIVITY
 *
 * Plays a video splash screen on FIRST APP LAUNCH ONLY.
 * Uses ExoPlayer (Media3) for smooth video playback.
 *
 * Features:
 * - Shows only on first app launch (uses SharedPreferences)
 * - Full-screen immersive video playback
 * - Automatic navigation to MainActivity when video completes
 * - Tap to skip functionality
 * - Handles lifecycle properly to release resources
 */
class VideoSplashActivity : ComponentActivity() {

    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null

    companion object {
        private const val PREFS_NAME = "splash_prefs"
        private const val KEY_SPLASH_SHOWN = "splash_shown"
    }

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        val launchTime = System.currentTimeMillis()
        android.util.Log.d("VideoSplash", "═══════════════════════════════════════════")
        android.util.Log.d("VideoSplash", "🚀 VIDEO SPLASH ACTIVITY onCreate START")
        android.util.Log.d("VideoSplash", "═══════════════════════════════════════════")

        // Check if splash was already shown BEFORE super.onCreate()
        // This allows us to set the correct theme before Android applies it
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val splashAlreadyShown = prefs.getBoolean(KEY_SPLASH_SHOWN, false)

        // Detect system theme
        val isNightMode = (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

        android.util.Log.d("VideoSplash", "📋 LAUNCH INFO:")
        android.util.Log.d("VideoSplash", "   • Splash already shown: $splashAlreadyShown")
        android.util.Log.d("VideoSplash", "   • System dark mode: $isNightMode")
        android.util.Log.d("VideoSplash", "   • savedInstanceState: ${if (savedInstanceState != null) "EXISTS" else "NULL"}")

        if (splashAlreadyShown) {
            android.util.Log.d("VideoSplash", "⏩ SCENARIO: SKIP SPLASH (subsequent launch)")
            android.util.Log.d("VideoSplash", "   • Setting theme to Theme.Nia BEFORE super.onCreate()")
            // Use the main app theme (respects dark/light mode) instead of white video splash theme
            // This prevents the flash because the theme matches MainActivity's theme
            setTheme(R.style.Theme_Nia)
        } else {
            android.util.Log.d("VideoSplash", "🎬 SCENARIO: SHOW SPLASH (first launch)")
            android.util.Log.d("VideoSplash", "   • Using default Theme.Nia.VideoSplash (white)")
        }

        android.util.Log.d("VideoSplash", "📞 Calling super.onCreate()...")
        super.onCreate(savedInstanceState)
        android.util.Log.d("VideoSplash", "✅ super.onCreate() completed")

        if (splashAlreadyShown) {
            android.util.Log.d("VideoSplash", "🔄 NAVIGATING TO MAIN ACTIVITY (no animation)")
            android.util.Log.d("VideoSplash", "   • Time in VideoSplash: ${System.currentTimeMillis() - launchTime}ms")
            // Skip splash and go directly to MainActivity without any transition
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            // No transition animation - instant switch to prevent flash
            overridePendingTransition(0, 0)
            android.util.Log.d("VideoSplash", "✅ Navigation initiated, returning from onCreate")
            return
        }

        android.util.Log.d("VideoSplash", "🎥 PREPARING VIDEO PLAYBACK...")

        // Mark splash as shown for future launches
        prefs.edit().putBoolean(KEY_SPLASH_SHOWN, true).apply()

        // Enable edge-to-edge immersive mode
        enableImmersiveMode()

        // Keep screen on during splash
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Create PlayerView programmatically with padding to shrink video
        playerView = PlayerView(this).apply {
            useController = false  // Hide playback controls
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT  // Fit video without cropping
            setBackgroundColor(android.graphics.Color.WHITE)  // White background to match video
            setOnClickListener { navigateToMain() }  // Tap to skip
        }

        // Wrap PlayerView in a FrameLayout with horizontal padding (10% on each side = 80% width)
        val container = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.WHITE)
            val horizontalPadding = (resources.displayMetrics.widthPixels * 0.10f).toInt()
            setPadding(horizontalPadding, 0, horizontalPadding, 0)
            addView(playerView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            ))
        }
        setContentView(container)

        // Set window background to white
        window.decorView.setBackgroundColor(android.graphics.Color.WHITE)

        // Initialize ExoPlayer
        initializePlayer()
    }

    @OptIn(UnstableApi::class)
    private fun initializePlayer() {
        player = ExoPlayer.Builder(this).build().also { exoPlayer ->
            playerView?.player = exoPlayer

            // Load video from raw resources
            val videoUri = "android.resource://${packageName}/${R.raw.splash_alt}"
            val mediaItem = MediaItem.fromUri(videoUri)
            exoPlayer.setMediaItem(mediaItem)

            // Navigate to MainActivity when video ends
            exoPlayer.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        navigateToMain()
                    }
                }
            })

            // Set playback speed to fit ~8 second video in 3 seconds (2.7x speed)
            exoPlayer.setPlaybackSpeed(2.7f)

            // Start playback
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }

    private fun enableImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // Make status bar and navigation bar transparent
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
    }

    private fun navigateToMain() {
        android.util.Log.d("VideoSplash", "═══════════════════════════════════════════")
        android.util.Log.d("VideoSplash", "🎬 VIDEO COMPLETED - navigateToMain()")
        android.util.Log.d("VideoSplash", "═══════════════════════════════════════════")
        android.util.Log.d("VideoSplash", "   • Using fade transition (video just finished)")

        // Prevent multiple navigations
        player?.removeListener(object : Player.Listener {})

        startActivity(Intent(this, MainActivity::class.java))
        finish()

        // Smooth fade transition
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        android.util.Log.d("VideoSplash", "✅ Navigation to MainActivity initiated with fade")
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onResume() {
        super.onResume()
        player?.play()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}
