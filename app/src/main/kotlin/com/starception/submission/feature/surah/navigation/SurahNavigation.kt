package com.starception.submission.feature.surah.navigation

import android.os.Bundle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import io.material.catalog.musicplayer.MusicPlayerAlbumDemoFragment
import kotlinx.serialization.Serializable

@Serializable
data class SurahRoute(val surahNumber: Int)

fun NavController.navigateToSurah(surahNumber: Int, navOptions: NavOptions? = null) {
    navigate(route = SurahRoute(surahNumber), navOptions)
}

@Composable
fun MusicPlayerFragmentScreen(
    surahNumber: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val fragmentManager = (context as? FragmentActivity)?.supportFragmentManager
        ?: return

    val fragmentTag = remember(surahNumber) { "MusicPlayerFragment_$surahNumber" }
    val containerViewId = remember { android.view.View.generateViewId() }
    var containerView by remember { mutableStateOf<android.view.View?>(null) }

    AndroidView(
        factory = { ctx ->
            FragmentContainerView(ctx).apply {
                id = containerViewId
                containerView = this
                // Enable window insets propagation to the Fragment
                ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
                    // Pass insets to child views (the Fragment's root view)
                    ViewCompat.onApplyWindowInsets(view, insets)
                }
            }
        },
        modifier = modifier.fillMaxSize()
    )

    // Handle Fragment creation and cleanup
    DisposableEffect(surahNumber, containerViewId) {
        // Always remove existing Fragment first to ensure clean state
        val existingFragment = fragmentManager.findFragmentByTag(fragmentTag)
        if (existingFragment != null) {
            fragmentManager.beginTransaction()
                .remove(existingFragment)
                .commitNowAllowingStateLoss()
            // Wait for removal to complete
            fragmentManager.executePendingTransactions()
        }
        
        // Create and add new Fragment
        // Use post to ensure container view is fully initialized
        containerView?.post {
            val fragment = MusicPlayerAlbumDemoFragment.newInstance(surahNumber)
            try {
                fragmentManager.beginTransaction()
                    .replace(containerViewId, fragment, fragmentTag)
                    .setReorderingAllowed(true)
                    .commitNowAllowingStateLoss()
            } catch (e: Exception) {
                // If commitNow fails, try async commit
                fragmentManager.beginTransaction()
                    .replace(containerViewId, fragment, fragmentTag)
                    .setReorderingAllowed(true)
                    .commitAllowingStateLoss()
            }
        } ?: run {
            // If container not ready yet, use Handler
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                val fragment = MusicPlayerAlbumDemoFragment.newInstance(surahNumber)
                try {
                    fragmentManager.beginTransaction()
                        .replace(containerViewId, fragment, fragmentTag)
                        .setReorderingAllowed(true)
                        .commitNowAllowingStateLoss()
                } catch (e: Exception) {
                    fragmentManager.beginTransaction()
                        .replace(containerViewId, fragment, fragmentTag)
                        .setReorderingAllowed(true)
                        .commitAllowingStateLoss()
                }
            }, 50)
        }
        
        onDispose {
            // Clean up Fragment when composable is disposed
            val fragment = fragmentManager.findFragmentByTag(fragmentTag)
            if (fragment != null && fragment.isAdded) {
                fragmentManager.beginTransaction()
                    .remove(fragment)
                    .commitNowAllowingStateLoss()
            }
        }
    }
}

fun NavGraphBuilder.surahScreen(
    onBackClick: () -> Unit
) {
    composable<SurahRoute> { backStackEntry ->
        val surahRoute = backStackEntry.toRoute<SurahRoute>()
        MusicPlayerFragmentScreen(surahNumber = surahRoute.surahNumber)
    }
}

