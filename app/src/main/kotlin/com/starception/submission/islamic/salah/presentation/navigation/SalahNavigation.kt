package com.starception.submission.islamic.salah.presentation.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.starception.submission.islamic.salah.presentation.screen.SalahDashboard
import com.starception.submission.prayer.service.EnhancedLocationService

/**
 * Islamic Salah Navigation Routes
 */
const val SALAH_ROUTE = "salah"

/**
 * Navigate to Salah (Prayer Times) screen
 */
fun NavController.navigateToSalah(navOptions: NavOptions? = null) {
    this.navigate(SALAH_ROUTE, navOptions)
}

/**
 * Add Salah navigation to NavGraphBuilder
 */
fun NavGraphBuilder.salahScreen(
    onSettingsClick: () -> Unit,
    locationService: EnhancedLocationService? = null
) {
    composable(route = SALAH_ROUTE) {
        SalahDashboard(
            onSettingsClick = onSettingsClick,
            locationService = locationService
        )
    }
}