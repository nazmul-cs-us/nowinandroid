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

package com.starception.submission.feature.course

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Lesson data model
 */
data class Lesson(
    val id: String,
    val title: String,
    val subtitle: String,
    val duration: String,
    val type: LessonType,
    val isCompleted: Boolean = false,
    val isLocked: Boolean = false,
)

enum class LessonType {
    VIDEO,
    READING,
    QUIZ,
    PRACTICE,
}

/**
 * Module data model (group of lessons)
 */
data class CourseModule(
    val id: String,
    val title: String,
    val lessons: List<Lesson>,
)

/**
 * Course Detail Screen - Coursera/Udemy style
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailScreen(
    course: Course,
    onBackClick: () -> Unit,
    onLessonClick: (Lesson, Int) -> Unit,
    onMarkComplete: (Lesson) -> Unit,
    onEnroll: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("course_progress", Context.MODE_PRIVATE) }

    // Check if enrolled
    var isEnrolled by remember {
        mutableStateOf(
            (prefs.getStringSet("enrolled_courses", emptySet()) ?: emptySet()).contains(course.id)
        )
    }

    // Track completed lessons
    var completedLessons by remember {
        mutableStateOf(
            prefs.getStringSet("completed_${course.id}", emptySet()) ?: emptySet()
        )
    }

    // Generate modules based on course type
    val modules = remember(course) { generateModulesForCourse(course) }

    // Calculate overall progress
    val totalLessons = modules.sumOf { it.lessons.size }
    val completedCount = completedLessons.size
    val progressPercent = if (totalLessons > 0) (completedCount * 100f / totalLessons) else 0f

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
        bottomBar = {
            // Show enroll button if not enrolled
            if (!isEnrolled) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "Free",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "${course.totalLessons} lessons",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Button(
                            onClick = {
                                val enrolledSet = (prefs.getStringSet("enrolled_courses", emptySet()) ?: emptySet()).toMutableSet()
                                enrolledSet.add(course.id)
                                prefs.edit().putStringSet("enrolled_courses", enrolledSet).apply()
                                isEnrolled = true
                                onEnroll()
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = course.gradientColors.first(),
                            ),
                            modifier = Modifier.height(48.dp),
                        ) {
                            Text(
                                text = "Enroll Now",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                }
            }
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 100.dp),
        ) {
            // Hero Section
            item {
                CourseHeroSection(
                    course = course,
                    progressPercent = progressPercent,
                    completedCount = completedCount,
                    totalLessons = totalLessons,
                    isEnrolled = isEnrolled,
                )
            }

            // Course Stats
            item {
                CourseStatsRow(
                    course = course,
                    completedCount = completedCount,
                )
            }

            // Continue Learning Card
            item {
                ContinueLearningCard(
                    modules = modules,
                    completedLessons = completedLessons,
                    onLessonClick = onLessonClick,
                )
            }

            // Syllabus Header
            item {
                Text(
                    text = "Course Syllabus",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }

            // Module List
            modules.forEachIndexed { moduleIndex, module ->
                item {
                    ModuleCard(
                        module = module,
                        moduleIndex = moduleIndex,
                        completedLessons = completedLessons,
                        onLessonClick = { lesson ->
                            val lessonIndex = module.lessons.indexOf(lesson)
                            onLessonClick(lesson, lessonIndex)
                        },
                        onMarkComplete = { lesson ->
                            val newSet = completedLessons.toMutableSet()
                            if (lesson.id in newSet) {
                                newSet.remove(lesson.id)
                            } else {
                                newSet.add(lesson.id)
                            }
                            completedLessons = newSet
                            prefs.edit().putStringSet("completed_${course.id}", newSet).apply()
                            // Also update legacy progress count
                            prefs.edit().putInt("progress_${course.id}", newSet.size).apply()
                            onMarkComplete(lesson)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CourseHeroSection(
    course: Course,
    progressPercent: Float,
    completedCount: Int,
    totalLessons: Int,
    isEnrolled: Boolean,
) {
    Column {
        // Main Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(
                    brush = Brush.linearGradient(
                        colors = course.gradientColors,
                    )
                ),
        ) {
            // Large background icon
            Icon(
                imageVector = course.icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.15f),
                modifier = Modifier
                    .size(140.dp)
                    .align(Alignment.CenterEnd)
                    .offset(x = 20.dp),
            )

            // Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 20.dp, end = 20.dp, top = 48.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Tags row
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Black.copy(alpha = 0.3f),
                    ) {
                        Text(
                            text = course.category.label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            letterSpacing = 1.sp,
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color.White,
                    ) {
                        Text(
                            text = course.difficulty.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = course.gradientColors.first(),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }

                // Title
                Text(
                    text = course.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                )
            }
        }

        // Info Card below banner
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-16).dp)
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
            ) {
                // Description
                Text(
                    text = course.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Progress or Info
                if (isEnrolled) {
                    // Progress section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${progressPercent.toInt()}% Complete",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = course.gradientColors.first(),
                        )
                        Text(
                            text = "$completedCount of $totalLessons",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { progressPercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = course.gradientColors.first(),
                        trackColor = course.gradientColors.first().copy(alpha = 0.2f),
                        strokeCap = StrokeCap.Round,
                    )
                } else {
                    // Stats for non-enrolled
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$totalLessons",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = course.gradientColors.first(),
                            )
                            Text(
                                text = "Lessons",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${course.estimatedDays}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = course.gradientColors.first(),
                            )
                            Text(
                                text = "Days",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = Color(0xFFFFB800),
                                modifier = Modifier.size(24.dp),
                            )
                            Text(
                                text = "Popular",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseStatsRow(
    course: Course,
    completedCount: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        StatItem(
            icon = Icons.Outlined.MenuBook,
            value = "${course.totalLessons}",
            label = "Lessons",
        )
        StatItem(
            icon = Icons.Outlined.Schedule,
            value = "${course.estimatedDays}",
            label = "Days",
        )
        StatItem(
            icon = Icons.Outlined.CheckCircle,
            value = "$completedCount",
            label = "Done",
        )
        StatItem(
            icon = Icons.Outlined.TrendingUp,
            value = course.difficulty.label,
            label = "Level",
        )
    }
}

@Composable
private fun StatItem(
    icon: ImageVector,
    value: String,
    label: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ContinueLearningCard(
    modules: List<CourseModule>,
    completedLessons: Set<String>,
    onLessonClick: (Lesson, Int) -> Unit,
) {
    // Find next incomplete lesson
    var nextLesson: Lesson? = null
    var nextLessonIndex = 0
    outer@ for (module in modules) {
        for ((index, lesson) in module.lessons.withIndex()) {
            if (lesson.id !in completedLessons) {
                nextLesson = lesson
                nextLessonIndex = index
                break@outer
            }
        }
    }

    if (nextLesson != null) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clickable { onLessonClick(nextLesson, nextLessonIndex) },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp),
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Continue Learning",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                    Text(
                        text = nextLesson.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun ModuleCard(
    module: CourseModule,
    moduleIndex: Int,
    completedLessons: Set<String>,
    onLessonClick: (Lesson) -> Unit,
    onMarkComplete: (Lesson) -> Unit,
) {
    var isExpanded by remember { mutableStateOf(moduleIndex == 0) } // First module expanded by default
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "rotation"
    )

    val completedInModule = module.lessons.count { it.id in completedLessons }
    val totalInModule = module.lessons.size

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .animateContentSize(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            // Module Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Module Number
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (completedInModule == totalInModule) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (completedInModule == totalInModule) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        Text(
                            text = "${moduleIndex + 1}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = module.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "$completedInModule/$totalInModule lessons completed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.rotate(rotationAngle),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Lessons List (when expanded)
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    module.lessons.forEachIndexed { index, lesson ->
                        val isCompleted = lesson.id in completedLessons
                        LessonItem(
                            lesson = lesson.copy(isCompleted = isCompleted),
                            lessonIndex = index,
                            onClick = { onLessonClick(lesson) },
                            onMarkComplete = { onMarkComplete(lesson) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonItem(
    lesson: Lesson,
    lessonIndex: Int,
    onClick: () -> Unit,
    onMarkComplete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !lesson.isLocked, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Completion Checkbox
        IconButton(
            onClick = onMarkComplete,
            modifier = Modifier.size(32.dp),
            enabled = !lesson.isLocked,
        ) {
            Icon(
                imageVector = if (lesson.isCompleted) {
                    Icons.Filled.CheckCircle
                } else {
                    Icons.Outlined.RadioButtonUnchecked
                },
                contentDescription = if (lesson.isCompleted) "Completed" else "Mark complete",
                tint = if (lesson.isCompleted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
                modifier = Modifier.size(24.dp),
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Lesson Type Icon
        Icon(
            imageVector = when (lesson.type) {
                LessonType.VIDEO -> Icons.Outlined.PlayCircle
                LessonType.READING -> Icons.Outlined.MenuBook
                LessonType.QUIZ -> Icons.Outlined.Quiz
                LessonType.PRACTICE -> Icons.Outlined.Edit
            },
            contentDescription = null,
            tint = if (lesson.isLocked) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(20.dp),
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Lesson Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = lesson.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (lesson.isCompleted) FontWeight.Normal else FontWeight.Medium,
                color = if (lesson.isLocked) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = lesson.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (lesson.isLocked) 0.4f else 1f
                ),
            )
        }

        // Duration or Lock Icon
        if (lesson.isLocked) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Locked",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.size(18.dp),
            )
        } else {
            Text(
                text = lesson.duration,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Generate modules and lessons based on course type
 */
private fun generateModulesForCourse(course: Course): List<CourseModule> {
    return when (course.id) {
        "memorize_3_ayahs" -> generateMemorize3AyahsModules()
        "daily_bukhari" -> generateDailyBukhariModules()
        "juz_amma" -> generateJuzAmmaModules()
        "quran_reading" -> generateQuranReadingModules()
        else -> emptyList()
    }
}

private fun generateMemorize3AyahsModules(): List<CourseModule> {
    val surahNames = listOf(
        "Al-Fatihah", "Al-Baqarah", "Aal-E-Imran", "An-Nisa", "Al-Ma'idah",
        "Al-An'am", "Al-A'raf", "Al-Anfal", "At-Tawbah", "Yunus",
        "Hud", "Yusuf", "Ar-Ra'd", "Ibrahim", "Al-Hijr",
        "An-Nahl", "Al-Isra", "Al-Kahf", "Maryam", "Ta-Ha",
        "Al-Anbiya", "Al-Hajj", "Al-Mu'minun", "An-Nur", "Al-Furqan",
        "Ash-Shu'ara", "An-Naml", "Al-Qasas", "Al-Ankabut", "Ar-Rum"
    )

    // Group surahs into modules of 10
    return surahNames.chunked(10).mapIndexed { moduleIndex, surahs ->
        CourseModule(
            id = "module_$moduleIndex",
            title = "Surahs ${moduleIndex * 10 + 1} - ${moduleIndex * 10 + surahs.size}",
            lessons = surahs.mapIndexed { index, surahName ->
                val surahNumber = moduleIndex * 10 + index + 1
                Lesson(
                    id = "surah_$surahNumber",
                    title = "Surah $surahName",
                    subtitle = "Memorize first 3 ayahs",
                    duration = "5 min",
                    type = LessonType.PRACTICE,
                )
            }
        )
    }
}

private fun generateDailyBukhariModules(): List<CourseModule> {
    val monthNames = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )
    val daysInMonth = listOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

    var hadithCounter = 1
    return monthNames.mapIndexed { monthIndex, monthName ->
        val daysCount = daysInMonth[monthIndex]
        CourseModule(
            id = "month_$monthIndex",
            title = monthName,
            lessons = (1..daysCount).map { day ->
                val currentHadith = hadithCounter++
                Lesson(
                    id = "hadith_$currentHadith",
                    title = "Day $day: Hadith #$currentHadith",
                    subtitle = "Sahih Al-Bukhari",
                    duration = "3 min",
                    type = LessonType.READING,
                )
            }
        )
    }
}

private fun generateJuzAmmaModules(): List<CourseModule> {
    val juzAmmaSurahs = listOf(
        "An-Naba", "An-Nazi'at", "Abasa", "At-Takwir", "Al-Infitar",
        "Al-Mutaffifin", "Al-Inshiqaq", "Al-Buruj", "At-Tariq", "Al-A'la",
        "Al-Ghashiyah", "Al-Fajr", "Al-Balad", "Ash-Shams", "Al-Layl",
        "Ad-Duha", "Ash-Sharh", "At-Tin", "Al-Alaq", "Al-Qadr",
        "Al-Bayyinah", "Az-Zalzalah", "Al-Adiyat", "Al-Qari'ah", "At-Takathur",
        "Al-Asr", "Al-Humazah", "Al-Fil", "Quraysh", "Al-Ma'un",
        "Al-Kawthar", "Al-Kafirun", "An-Nasr", "Al-Masad", "Al-Ikhlas",
        "Al-Falaq", "An-Nas"
    )

    return listOf(
        CourseModule(
            id = "module_long",
            title = "Longer Surahs (78-88)",
            lessons = juzAmmaSurahs.take(11).mapIndexed { index, name ->
                Lesson(
                    id = "juz_${78 + index}",
                    title = "Surah $name",
                    subtitle = "Surah ${78 + index}",
                    duration = "10 min",
                    type = LessonType.PRACTICE,
                )
            }
        ),
        CourseModule(
            id = "module_medium",
            title = "Medium Surahs (89-100)",
            lessons = juzAmmaSurahs.slice(11..22).mapIndexed { index, name ->
                Lesson(
                    id = "juz_${89 + index}",
                    title = "Surah $name",
                    subtitle = "Surah ${89 + index}",
                    duration = "7 min",
                    type = LessonType.PRACTICE,
                )
            }
        ),
        CourseModule(
            id = "module_short",
            title = "Short Surahs (101-114)",
            lessons = juzAmmaSurahs.drop(23).mapIndexed { index, name ->
                Lesson(
                    id = "juz_${101 + index}",
                    title = "Surah $name",
                    subtitle = "Surah ${101 + index}",
                    duration = "3 min",
                    type = LessonType.PRACTICE,
                )
            }
        ),
    )
}

private fun generateQuranReadingModules(): List<CourseModule> {
    return (0 until 30).map { juzIndex ->
        CourseModule(
            id = "juz_$juzIndex",
            title = "Juz ${juzIndex + 1}",
            lessons = (0 until 20).map { pageIndex ->
                val pageNumber = juzIndex * 20 + pageIndex + 1
                Lesson(
                    id = "page_$pageNumber",
                    title = "Page $pageNumber",
                    subtitle = "Read and reflect",
                    duration = "5 min",
                    type = LessonType.READING,
                )
            }
        )
    }
}
