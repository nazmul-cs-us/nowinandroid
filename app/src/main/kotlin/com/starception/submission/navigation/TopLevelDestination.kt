/*
 * Copyright 2022 The Android Open Source Project
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

package com.starception.submission.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.starception.submission.R
import com.starception.submission.core.designsystem.icon.NiaIcons
import com.starception.submission.feature.bookmarks.navigation.BookmarksBaseRoute
import com.starception.submission.feature.bookmarks.navigation.BookmarksRoute
import com.starception.submission.feature.foryou.navigation.ForYouRoute
import com.starception.submission.feature.interests.navigation.InterestsRoute
import com.starception.submission.feature.prayertimes.navigation.PrayerTimesRoute
import com.starception.submission.feature.course.navigation.CourseRoute
import kotlin.reflect.KClass
import com.starception.submission.feature.bookmarks.R as bookmarksR
import com.starception.submission.feature.foryou.R as forYouR
import com.starception.submission.feature.search.R as searchR

/**
 * Type for the top level destinations in the application. Contains metadata about the destination
 * that is used in the top app bar and common navigation UI.
 *
 * @param selectedIcon The icon to be displayed in the navigation UI when this destination is
 * selected.
 * @param unselectedIcon The icon to be displayed in the navigation UI when this destination is
 * not selected.
 * @param iconTextId Text that to be displayed in the navigation UI.
 * @param titleTextId Text that is displayed on the top app bar.
 * @param route The route to use when navigating to this destination.
 * @param baseRoute The highest ancestor of this destination. Defaults to [route], meaning that
 * there is a single destination in that section of the app (no nested destinations).
 */
enum class TopLevelDestination(
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    @StringRes val iconTextId: Int,
    @StringRes val titleTextId: Int,
    val route: KClass<*>,
    val baseRoute: KClass<*> = route,
) {
    HOME(
        selectedIcon = NiaIcons.Home,
        unselectedIcon = NiaIcons.HomeBorder,
        iconTextId = R.string.prayer_times_title,
        titleTextId = R.string.prayer_times_title,
        route = PrayerTimesRoute::class,
    ),
    FOR_YOU(
        selectedIcon = NiaIcons.Upcoming,
        unselectedIcon = NiaIcons.UpcomingBorder,
        iconTextId = forYouR.string.feature_foryou_title,
        titleTextId = R.string.app_name,
        route = ForYouRoute::class,
    ),
    BOOKMARKS(
        selectedIcon = NiaIcons.Bookmarks,
        unselectedIcon = NiaIcons.BookmarksBorder,
        iconTextId = bookmarksR.string.feature_bookmarks_title,
        titleTextId = bookmarksR.string.feature_bookmarks_title,
        route = BookmarksRoute::class,
        baseRoute = BookmarksBaseRoute::class,
    ),
    COURSE(
        selectedIcon = NiaIcons.Course,
        unselectedIcon = NiaIcons.CourseBorder,
        iconTextId = R.string.course_title,
        titleTextId = R.string.course_title,
        route = CourseRoute::class,
    ),
    INTERESTS(
        selectedIcon = NiaIcons.Grid3x3,
        unselectedIcon = NiaIcons.Grid3x3,
        iconTextId = searchR.string.feature_search_interests,
        titleTextId = searchR.string.feature_search_interests,
        route = InterestsRoute::class,
    ),
}
