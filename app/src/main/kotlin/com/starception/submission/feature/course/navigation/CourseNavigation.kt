/*
 * Copyright 2024 Starception
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

package com.starception.submission.feature.course.navigation

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.starception.submission.core.designsystem.animation.NiaTransitions
import com.starception.submission.core.designsystem.theme.LocalDarkTheme
import com.starception.submission.feature.course.CourseScreen
import com.starception.submission.feature.course.CourseDetailScreen
import com.starception.submission.feature.course.CourseProgressTracker
import com.starception.submission.feature.course.getAvailableCourses
import kotlinx.serialization.Serializable

@Serializable
data object CourseRoute

@Serializable
data class CourseDetailRoute(val courseId: String)

fun NavController.navigateToCourse(navOptions: NavOptions? = null) {
    navigate(CourseRoute, navOptions)
}

fun NavController.navigateToCourseDetail(courseId: String) {
    navigate(CourseDetailRoute(courseId))
}

fun NavGraphBuilder.courseScreen(
    titleRes: Int,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSurahClick: (Int) -> Unit,
    onHadithClick: (databaseFile: String, hadithNumber: Int) -> Unit = { _, _ -> },
    onCourseClick: (String) -> Unit = { },
    onBackClick: () -> Unit = { },
    onSearchSubmit: (query: String) -> Unit = {},
) {
    composable<CourseRoute> {
        val coursePageTopColor = if (LocalDarkTheme.current) {
            MaterialTheme.colorScheme.background
        } else {
            Color(0xFFF0F1F2)
        }
        com.starception.submission.ui.TopLevelTopBarScaffold(
            titleRes = titleRes,
            onSearchClick = onSearchClick,
            onSettingsClick = onSettingsClick,
            onSearchSubmit = onSearchSubmit,
            searchPillContainerColor = Color(0xFFE9E6D8),
            searchPillContentColor = Color(0xFF0A0808),
            pageContainerColor = coursePageTopColor,
        ) {
            CourseScreen(
                onSurahClick = onSurahClick,
                onHadithClick = onHadithClick,
                onCourseClick = onCourseClick,
            )
        }
    }

    composable<CourseDetailRoute>(
        enterTransition = { NiaTransitions.detailEnter() },
        exitTransition = { NiaTransitions.detailExit() },
        popEnterTransition = { NiaTransitions.detailPopEnter() },
        popExitTransition = { NiaTransitions.detailPopExit() },
    ) { backStackEntry ->
        val context = LocalContext.current
        val route = backStackEntry.arguments?.let {
            CourseDetailRoute(it.getString("courseId") ?: "")
        } ?: return@composable

        val course = getAvailableCourses().find { it.id == route.courseId }
        if (course != null) {
            CourseDetailScreen(
                course = course,
                onBackClick = onBackClick,
                onLessonClick = { lesson, index ->
                    // Set as pending completion (user has viewed but not confirmed)
                    // Completion will be confirmed via bottom sheet when returning
                    CourseProgressTracker.setPendingCompletion(
                        context,
                        course.id,
                        lesson.id,
                        lesson.title
                    )

                    // Navigate based on course type
                    when {
                        course.id == "memorize_3_ayahs" -> {
                            val surahNumber = lesson.id.removePrefix("surah_").toIntOrNull() ?: 1
                            onSurahClick(surahNumber)
                        }
                        course.id == "daily_bukhari" -> {
                            val hadithNumber = lesson.id.removePrefix("hadith_").toIntOrNull() ?: 1
                            onHadithClick("sahih_bukhari.db", hadithNumber)
                        }
                        course.id == "juz_amma" -> {
                            val surahNumber = lesson.id.removePrefix("juz_").toIntOrNull() ?: 78
                            onSurahClick(surahNumber)
                        }
                        course.id == "quran_reading" -> {
                            // Map page to surah (simplified)
                            val pageNumber = lesson.id.removePrefix("page_").toIntOrNull() ?: 1
                            val surahNumber = ((pageNumber - 1) / 5 + 1).coerceIn(1, 114)
                            onSurahClick(surahNumber)
                        }
                    }
                },
                onMarkComplete = { lesson ->
                    // No longer auto-marking here - CourseDetailScreen shows bottom sheet
                },
            )
        }
    }
}
