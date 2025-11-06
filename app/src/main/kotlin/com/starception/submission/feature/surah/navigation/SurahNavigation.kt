package com.starception.submission.feature.surah.navigation

import android.os.Bundle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
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
    albumId: Long = 0L,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val fragmentManager = (context as? FragmentActivity)?.supportFragmentManager
        ?: return

    val fragmentTag = remember(albumId) { "MusicPlayerFragment_$albumId" }
    val containerViewId = remember { android.view.View.generateViewId() }

    DisposableEffect(albumId) {
        val fragment = fragmentManager.findFragmentByTag(fragmentTag)
            ?: MusicPlayerAlbumDemoFragment.newInstance(albumId).also {
                fragmentManager.beginTransaction()
                    .replace(containerViewId, it, fragmentTag)
                    .commitNowAllowingStateLoss()
            }

        onDispose {
            // Fragment will be removed when the view is disposed
        }
    }

    AndroidView(
        factory = { ctx ->
            FragmentContainerView(ctx).apply {
                id = containerViewId
                // Enable window insets propagation to the Fragment
                ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
                    // Pass insets to child views (the Fragment's root view)
                    ViewCompat.onApplyWindowInsets(view, insets)
                }
            }
        },
        modifier = modifier.fillMaxSize()
    )
}

fun NavGraphBuilder.surahScreen(
    onBackClick: () -> Unit
) {
    composable<SurahRoute> { backStackEntry ->
        val surahRoute = backStackEntry.toRoute<SurahRoute>()
        // Use albumId 0 for testing (Metamorphosis album)
        MusicPlayerFragmentScreen(albumId = 0L)
    }
}

