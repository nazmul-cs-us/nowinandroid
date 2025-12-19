package com.starception.submission.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.starception.submission.settings.UnifiedSettingsScreen
import kotlinx.serialization.Serializable

@Serializable
object SettingsRoute

fun NavController.navigateToSettings() = navigate(SettingsRoute)

fun NavGraphBuilder.settingsScreen(
    onBackClick: () -> Unit
) {
    composable<SettingsRoute> {
        UnifiedSettingsScreen(
            onBackClick = onBackClick
        )
    }
}
