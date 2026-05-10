package com.starception.submission.feature.surah.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.starception.submission.navigation.detailEnterTransition
import com.starception.submission.navigation.detailExitTransition
import com.starception.submission.navigation.detailPopEnterTransition
import com.starception.submission.navigation.detailPopExitTransition
import com.starception.submission.feature.surah.SurahDetailScreen
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
    onBackClick: () -> Unit,
    onTopicClick: (String) -> Unit = {},
    onNavigateToPreviousSurah: (Int) -> Unit = {},
    onNavigateToNextSurah: (Int) -> Unit = {}
) {
    composable<SurahRoute>(
        enterTransition = { detailEnterTransition() },
        exitTransition = { detailExitTransition() },
        popEnterTransition = { detailPopEnterTransition() },
        popExitTransition = { detailPopExitTransition() },
    ) { backStackEntry ->
        val surahRoute = backStackEntry.toRoute<SurahRoute>()
        // Use the Surah detail screen with MaterialTheme.colorScheme
        SurahDetailScreen(
            surahNumber = surahRoute.surahNumber,
            newsResourceId = surahRoute.newsResourceId,
            scrollToAyah = surahRoute.scrollToAyah,
            onBackClick = onBackClick,
            onTopicClick = onTopicClick,
            onNavigateToPreviousSurah = { onNavigateToPreviousSurah(surahRoute.surahNumber) },
            onNavigateToNextSurah = { onNavigateToNextSurah(surahRoute.surahNumber) }
        )
    }
}

