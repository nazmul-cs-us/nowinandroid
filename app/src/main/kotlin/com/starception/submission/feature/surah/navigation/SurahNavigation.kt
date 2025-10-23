package com.starception.submission.feature.surah.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.starception.submission.feature.surah.SurahDetailScreen
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
        SurahDetailScreen(
            surahNumber = surahRoute.surahNumber,
            onBackClick = onBackClick
        )
    }
}

