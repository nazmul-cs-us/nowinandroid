/*
 * Copyright 2021 The Android Open Source Project
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

package com.starception.submission.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable

/**
 * The shared app's destinations.
 *
 * Serializable objects rather than string routes, matching how the Android app
 * declares its own — so a screen moving between them does not have its
 * navigation rewritten on the way.
 *
 * Only what iOS can actually show today. The Android app has five top-level
 * destinations, but four of them (For You, Bookmarks, Course, Interests) are
 * feature modules that have not been ported: a tab bar with four empty tabs
 * would be worse than no tab bar.
 */
@Serializable
object PrayerTimesRoute

@Serializable
object PrayerSettingsRoute

/**
 * Hosts the shared screens.
 *
 * This exists so settings can be a destination rather than a sheet. A sheet was
 * the honest shape while there was nowhere to navigate to; now that there is,
 * settings gets a back stack, which is what makes room for the Qibla, Quran and
 * detail screens that follow.
 */
@Composable
fun SharedNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    home: @Composable (onOpenSettings: () -> Unit) -> Unit,
    settings: @Composable (onBack: () -> Unit) -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = PrayerTimesRoute,
        modifier = modifier,
    ) {
        composable<PrayerTimesRoute> {
            home { navController.navigate(PrayerSettingsRoute) }
        }
        composable<PrayerSettingsRoute> {
            // popBackStack rather than navigate(home): navigating would push a
            // second copy of the home screen and leave settings on the stack,
            // so the system back gesture would return to it.
            settings { navController.popBackStack() }
        }
    }
}
