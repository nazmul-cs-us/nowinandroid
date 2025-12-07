package com.starception.submission.feature.surah.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.starception.submission.feature.surah.QuranAlbumPlayerScreen
import kotlinx.serialization.Serializable

@Serializable
data class SurahRoute(
    val surahNumber: Int,
    val newsResourceId: String? = null, // Optional: only present when opened from news feed
    val scrollToAyah: Int = 0 // Optional: scroll to specific ayah number (0 = no scroll)
)

fun NavController.navigateToSurah(
    surahNumber: Int,
    newsResourceId: String? = null,
    scrollToAyah: Int = 0,
    navOptions: NavOptions? = null
) {
    navigate(route = SurahRoute(surahNumber, newsResourceId, scrollToAyah), navOptions)
}

fun NavGraphBuilder.surahScreen(
    onBackClick: () -> Unit
) {
    composable<SurahRoute> { backStackEntry ->
        val surahRoute = backStackEntry.toRoute<SurahRoute>()
        // Use the new Compose album player screen with MaterialTheme.colorScheme
        QuranAlbumPlayerScreen(
            surahNumber = surahRoute.surahNumber,
            newsResourceId = surahRoute.newsResourceId,
            scrollToAyah = surahRoute.scrollToAyah,
            onBackClick = onBackClick
        )
    }
}

