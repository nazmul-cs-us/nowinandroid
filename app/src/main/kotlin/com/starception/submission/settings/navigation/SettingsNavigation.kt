package com.starception.submission.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.starception.submission.core.designsystem.animation.NiaTransitions
import com.starception.submission.settings.UnifiedSettingsScreen
import kotlinx.serialization.Serializable

@Serializable
object SettingsRoute

fun NavController.navigateToSettings() = navigate(SettingsRoute)

fun NavGraphBuilder.settingsScreen(
    onBackClick: () -> Unit,
    onNavigateToSalahDataCollection: () -> Unit = {}
) {
    composable<SettingsRoute>(
        enterTransition = { NiaTransitions.slideUpEnter() },
        exitTransition = { NiaTransitions.fadeThroughExit() },
        popEnterTransition = { NiaTransitions.fadeThroughEnter() },
        popExitTransition = { NiaTransitions.slideDownExit() },
    ) {
        UnifiedSettingsScreen(
            onBackClick = onBackClick,
            onNavigateToSalahDataCollection = onNavigateToSalahDataCollection
        )
    }
}
