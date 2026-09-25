/*
 * Copyright 2026 The Android Open Source Project
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
    locationService: EnhancedLocationService? = null,
) {
    composable(route = SALAH_ROUTE) {
        SalahDashboard(
            onSettingsClick = onSettingsClick,
            locationService = locationService,
        )
    }
}
