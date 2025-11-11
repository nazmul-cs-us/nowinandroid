package com.starception.submission.feature.surah.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.starception.submission.feature.surah.QuranAlbumPlayerScreen
import kotlinx.serialization.Serializable

@Serializable
data class SurahRoute(val surahNumber: Int)

fun NavController.navigateToSurah(surahNumber: Int, navOptions: NavOptions? = null) {
    navigate(route = SurahRoute(surahNumber), navOptions)
}

fun NavGraphBuilder.surahScreen(
    onBackClick: () -> Unit
) {
    composable<SurahRoute> { backStackEntry ->
        val surahRoute = backStackEntry.toRoute<SurahRoute>()
        // Use the new Compose album player screen with MaterialTheme.colorScheme
        QuranAlbumPlayerScreen(
            surahNumber = surahRoute.surahNumber,
            onBackClick = onBackClick
        )
    }
}

