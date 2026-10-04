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

package com.starception.submission.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.starception.submission.core.designsystem.icon.NiaIcons
import com.starception.submission.shared.content.SharedContentStore
import com.starception.submission.shared.content.modulesForCourse
import io.github.alexzhirkevich.cupertino.adaptive.AdaptiveButton
import io.github.alexzhirkevich.cupertino.adaptive.AdaptiveIconButton
import io.github.alexzhirkevich.cupertino.adaptive.AdaptiveSurface
import io.github.alexzhirkevich.cupertino.adaptive.AdaptiveTextButton
import io.github.alexzhirkevich.cupertino.adaptive.ExperimentalAdaptiveApi

/**
 * The course detail page — Android's CourseDetailScreen: hero with the
 * course highlight, quick stats, module-by-module lesson list with
 * completion tracking, and lesson content opening.
 */
@OptIn(ExperimentalAdaptiveApi::class)
@Composable
internal fun CourseDetailScreen(
    courseId: String,
    store: SharedContentStore,
    onBack: () -> Unit,
    onOpenSurah: (Int) -> Unit = {},
    onOpenBukhariBook: (Int) -> Unit = {},
) {
    val course = remember(courseId) {
        com.starception.submission.shared.content.SharedCourses.firstOrNull { it.id == courseId }
    }
    if (course == null) {
        SharedDetailScaffold(title = "Course", onBack = onBack) {
            Text("Course not found", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val modules = remember(courseId) { modulesForCourse(courseId) }
    var enrolled by remember { mutableStateOf(courseId in store.enrolledCourses()) }
    var completedLessons by remember(courseId) { mutableStateOf(store.courseCompletedLessons(courseId)) }

    val totalLessons = modules.sumOf { it.lessons.size }
    val completedCount = completedLessons.size
    val progressPercent = if (totalLessons > 0) completedCount * 100 / totalLessons else 0

    // Hero highlight — Android's CourseHeroSection dynamic content
    val (highlightText, highlightSubtext) = remember(courseId) {
        when (courseId) {
            "memorize_3_ayahs" -> Pair("114", "Surahs to Master")
            "daily_bukhari" -> Pair("365", "Days Journey")
            "juz_amma" -> Pair("37", "Surahs • Juz 30")
            "quran_reading" -> Pair("604", "Pages of Quran")
            else -> Pair("${course.totalLessons}", "Lessons")
        }
    }
    val courseTagline = remember(courseId) {
        when (courseId) {
            "memorize_3_ayahs" -> "Start with the opening verses"
            "daily_bukhari" -> "One hadith, one day at a time"
            "juz_amma" -> "The most recited Juz"
            "quran_reading" -> "Complete the Holy Quran"
            else -> course.subtitle
        }
    }
    val accentColor = when (course.category) {
        "Quran" -> Color(0xFF4F779D)
        "Hadith" -> Color(0xFF99593C)
        else -> MaterialTheme.colorScheme.primary
    }

    AdaptiveSurface(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp),
        ) {
            // Toolbar
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AdaptiveIconButton(onClick = onBack) {
                        Icon(NiaIcons.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        course.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Hero
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        highlightText,
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                    )
                    Text(
                        highlightSubtext,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        courseTagline,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }

            // Quick stats pills
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        Triple("$completedCount", "Completed", Modifier.weight(1f)),
                        Triple("$totalLessons", "Lessons", Modifier.weight(1f)),
                        Triple("${course.estimatedDays}", "Days", Modifier.weight(1f)),
                        Triple(course.difficulty, "Level", Modifier.weight(1f)),
                    ).forEach { (value, label, modifier) ->
                        AdaptiveSurface(
                            shape = RoundedCornerShape(16.dp),
                            modifier = modifier,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    value,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor,
                                )
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Enroll / Continue
            item {
                if (!enrolled) {
                    AdaptiveButton(
                        onClick = {
                            enrolled = true
                            store.toggleEnrolledCourse(courseId)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp),
                    ) {
                        Text("Enroll in this course", fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    // Progress bar
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "$progressPercent% complete",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = accentColor,
                            )
                            Text(
                                "$completedCount of $totalLessons lessons",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { progressPercent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(999.dp)),
                            color = accentColor,
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            // Module list with lessons
            modules.forEach { module ->
                item(key = module.id) {
                    // Module header
                    Text(
                        module.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp),
                    )
                    val moduleCompleted = module.lessons.count { it.id in completedLessons }
                    val modulePercent = if (module.lessons.isNotEmpty()) {
                        moduleCompleted * 100 / module.lessons.size
                    } else {
                        0
                    }
                    Text(
                        "$moduleCompleted of ${module.lessons.size} · $modulePercent%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 22.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                }

                items(module.lessons, key = { it.id }) { lesson ->
                    LessonRow(
                        lesson = lesson,
                        isCompleted = lesson.id in completedLessons,
                        isLocked = !enrolled,
                        accentColor = accentColor,
                        onClick = {
                            if (enrolled) {
                                when {
                                    lesson.surahNumber > 0 -> onOpenSurah(lesson.surahNumber)
                                    lesson.hadithNumber > 0 -> onOpenBukhariBook(1)
                                }
                            }
                        },
                        onToggleComplete = {
                            if (enrolled) {
                                completedLessons = store.toggleCourseLesson(courseId, lesson.id)
                            }
                        },
                    )
                }

                item(key = "${module.id}_spacer") {
                    Spacer(Modifier.height(10.dp))
                }
            }

            // Unenroll
            if (enrolled) {
                item {
                    AdaptiveTextButton(
                        onClick = {
                            enrolled = false
                            store.toggleEnrolledCourse(courseId)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp),
                    ) {
                        Text("Unenroll", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

/** One lesson row — completion checkbox, title, subtitle, duration, open. */
@Composable
private fun LessonRow(
    lesson: com.starception.submission.shared.content.SharedLesson,
    isCompleted: Boolean,
    isLocked: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    onToggleComplete: () -> Unit,
) {
    AdaptiveCard(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 3.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Completion checkbox
            AdaptiveIconButton(
                onClick = onToggleComplete,
                enabled = !isLocked,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    if (isCompleted) Icons.Filled.CheckCircle else Icons.Filled.Circle,
                    contentDescription = if (isCompleted) "Completed" else "Mark complete",
                    tint = if (isCompleted) accentColor else MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    lesson.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    lesson.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                lesson.duration,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
