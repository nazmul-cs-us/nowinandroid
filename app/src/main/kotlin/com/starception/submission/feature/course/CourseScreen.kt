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
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.starception.submission.core.designsystem.component.NiaTopicTag

/**
 * Course data model
 */
data class Course(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val icon: ImageVector,
    val totalLessons: Int,
    val estimatedDays: Int,
    val difficulty: CourseDifficulty,
    val category: CourseCategory,
    val gradientColors: List<Color>,
)

enum class CourseDifficulty(val label: String) {
    BEGINNER("Beginner"),
    INTERMEDIATE("Intermediate"),
    ADVANCED("Advanced"),
}

enum class CourseCategory(val label: String) {
    QURAN("Quran"),
    HADITH("Hadith"),
    MEMORIZATION("Memorization"),
}

/**
 * Course Screen - Islamic Learning Platform
 */
@Composable
fun CourseScreen(
    onSurahClick: (Int) -> Unit,
    onHadithClick: (databaseFile: String, hadithNumber: Int) -> Unit = { _, _ -> },
    onCourseClick: (String) -> Unit = { },
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("course_progress", Context.MODE_PRIVATE) }

    // Enrolled courses
    var enrolledCourseIds by remember {
        mutableStateOf(prefs.getStringSet("enrolled_courses", emptySet()) ?: emptySet())
    }

    // Available courses
    val availableCourses = remember { getAvailableCourses() }

    // Separate enrolled and not enrolled
    val myCourses = availableCourses.filter { it.id in enrolledCourseIds }
    val exploreCourses = availableCourses.filter { it.id !in enrolledCourseIds }

    // Track progress updates to trigger recomposition
    var progressUpdateTrigger by remember { mutableStateOf(0) }

    // Get progress for each enrolled course (reactive to changes)
    val courseProgress = remember(enrolledCourseIds, progressUpdateTrigger) {
        myCourses.associate { course ->
            course.id to prefs.getInt("progress_${course.id}", 0)
        }
    }

    // State for showing completion dialog - use rememberSaveable to survive predictive back
    var pendingCourseId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingLessonId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingLessonTitle by rememberSaveable { mutableStateOf<String?>(null) }
    var dialogShown by rememberSaveable { mutableStateOf(false) }

    // Check for pending completions when screen is shown (with delay for navigation to settle)
    LaunchedEffect(progressUpdateTrigger) {
        // Wait for navigation to settle before checking pending
        kotlinx.coroutines.delay(500)
        // Check each enrolled course for pending completions
        for (course in myCourses) {
            val pending = CourseProgressTracker.getPendingCompletion(context, course.id)
            if (pending != null && !dialogShown) {
                pendingCourseId = course.id
                pendingLessonId = pending.lessonId
                pendingLessonTitle = pending.lessonTitle
                dialogShown = true
                break
            }
        }
    }

    // Show completion dialog (more stable than BottomSheet during predictive back)
    // Capture values to avoid smart cast issues with delegated properties
    val currentCourseId = pendingCourseId
    val currentLessonId = pendingLessonId
    val currentLessonTitle = pendingLessonTitle

    if (dialogShown && currentCourseId != null && currentLessonId != null && currentLessonTitle != null) {
        LessonCompletionDialog(
            lessonTitle = currentLessonTitle,
            courseId = currentCourseId,
            lessonId = currentLessonId,
            onComplete = { hasRecording ->
                // Mark lesson as completed
                CourseProgressTracker.confirmPendingCompletion(context, currentCourseId)
                // Update progress count
                val currentProgress = prefs.getInt("progress_$currentCourseId", 0)
                val course = availableCourses.find { it.id == currentCourseId }
                if (course != null && currentProgress < course.totalLessons) {
                    prefs.edit().putInt("progress_$currentCourseId", currentProgress + 1).apply()
                    progressUpdateTrigger++
                }
                // Clear all pending state
                CourseProgressTracker.clearPendingCompletion(context, currentCourseId)
                pendingCourseId = null
                pendingLessonId = null
                pendingLessonTitle = null
                dialogShown = false
            },
            onDismiss = {
                // User said "Not Yet" - clear pending without marking complete
                CourseProgressTracker.clearPendingCompletion(context, currentCourseId)
                pendingCourseId = null
                pendingLessonId = null
                pendingLessonTitle = null
                dialogShown = false
            },
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 100.dp),
    ) {
        // Header
        item {
            CourseHeader()
        }

        // Swipeable Big Tile for enrolled courses (only show if enrolled)
        if (myCourses.isNotEmpty()) {
            item {
                CourseSwipeableTiles(
                    enrolledCourses = myCourses,
                    courseProgress = courseProgress,
                    onCourseClick = { course ->
                        // Navigate to course detail screen
                        onCourseClick(course.id)
                    },
                    onContinueCourse = { course ->
                        val currentProgress = prefs.getInt("progress_${course.id}", 0)
                        when (course.id) {
                            "memorize_3_ayahs" -> {
                                // Set pending completion before navigating
                                val surahNumber = currentProgress + 1
                                val lessonId = "surah_$surahNumber"
                                val lessonTitle = "Surah ${getSurahName(surahNumber)}"
                                CourseProgressTracker.setPendingCompletion(context, course.id, lessonId, lessonTitle)
                                // Navigate to the surah (1-114)
                                onSurahClick(surahNumber)
                            }
                            "daily_bukhari" -> {
                                // Set pending completion before navigating
                                val hadithNumber = currentProgress + 1
                                val lessonId = "hadith_$hadithNumber"
                                val lessonTitle = "Hadith #$hadithNumber"
                                CourseProgressTracker.setPendingCompletion(context, course.id, lessonId, lessonTitle)
                                // Navigate to hadith from Bukhari
                                onHadithClick("sahih_bukhari.db", hadithNumber)
                            }
                            "juz_amma" -> {
                                // Juz Amma starts at surah 78
                                val surahNumber = 78 + currentProgress
                                if (surahNumber <= 114) {
                                    // Set pending completion before navigating
                                    val lessonId = "surah_$surahNumber"
                                    val lessonTitle = "Surah ${getSurahName(surahNumber)}"
                                    CourseProgressTracker.setPendingCompletion(context, course.id, lessonId, lessonTitle)
                                    onSurahClick(surahNumber)
                                }
                            }
                            "quran_reading" -> {
                                // Set pending completion before navigating
                                val pageNumber = currentProgress + 1
                                val lessonId = "page_$pageNumber"
                                val lessonTitle = "Page $pageNumber"
                                CourseProgressTracker.setPendingCompletion(context, course.id, lessonId, lessonTitle)
                                // Navigate to appropriate surah based on page
                                val surahNumber = ((currentProgress / 5) + 1).coerceIn(1, 114)
                                onSurahClick(surahNumber)
                            }
                            else -> {
                                // Default: show completion dialog directly
                                val lessonId = "lesson_${currentProgress + 1}"
                                val lessonTitle = "Lesson ${currentProgress + 1}"
                                pendingCourseId = course.id
                                pendingLessonId = lessonId
                                pendingLessonTitle = lessonTitle
                                dialogShown = true
                            }
                        }
                    },
                    onUnenroll = { course ->
                        val newSet = enrolledCourseIds.toMutableSet()
                        newSet.remove(course.id)
                        enrolledCourseIds = newSet
                        prefs.edit().putStringSet("enrolled_courses", newSet).apply()
                        // Optionally clear progress too
                        // prefs.edit().remove("progress_${course.id}").apply()
                    },
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        // Explore Courses Section
        item {
            SectionTitle(
                title = if (myCourses.isEmpty()) "Available Courses" else "Explore More",
                subtitle = "${exploreCourses.size} courses",
            )
        }

        items(exploreCourses) { course ->
            CourseCard(
                course = course,
                onClick = {
                    // Preview course before enrolling
                    onCourseClick(course.id)
                },
                onEnroll = {
                    val newSet = enrolledCourseIds.toMutableSet()
                    newSet.add(course.id)
                    enrolledCourseIds = newSet
                    prefs.edit().putStringSet("enrolled_courses", newSet).apply()
                },
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
            )
        }

        // All Courses enrolled message
        if (exploreCourses.isEmpty() && myCourses.isNotEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.EmojiEvents,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "You're enrolled in all courses!",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 16.dp),
    ) {
        Text(
            text = "My Learning",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Continue where you left off",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionTitle(
    title: String,
    subtitle: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EnrolledCourseCard(
    course: Course,
    progress: Int,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .width(280.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(course.gradientColors),
                    shape = RoundedCornerShape(20.dp),
                )
                .padding(20.dp),
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = course.icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.2f),
                    ) {
                        Text(
                            text = "${progress}/${course.totalLessons}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = course.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = course.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Progress bar
                LinearProgressIndicator(
                    progress = { progress.toFloat() / course.totalLessons },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f),
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "${(progress * 100 / course.totalLessons)}% complete",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
        }
    }
}

/**
 * Professional Course Card - Udemy/Coursera style
 */
@Composable
private fun CourseCard(
    course: Course,
    onClick: () -> Unit,
    onEnroll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Get colors based on category
    val (containerColor, accentColor) = when (course.category) {
        CourseCategory.MEMORIZATION -> Pair(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.primary
        )
        CourseCategory.HADITH -> Pair(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.secondary
        )
        CourseCategory.QURAN -> Pair(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.tertiary
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box {
            Column {
                // Header with gradient, title and icon
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    containerColor,
                                    containerColor.copy(alpha = 0.7f),
                                )
                            )
                        )
                        .padding(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        // Title and subtitle
                        Column(
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = course.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = accentColor,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 26.sp,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = course.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = accentColor.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Lessons count indicator
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = accentColor.copy(alpha = 0.15f),
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = "${course.totalLessons}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor,
                                )
                                Text(
                                    text = "lessons",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accentColor.copy(alpha = 0.8f),
                                )
                            }
                        }
                    }
                }

                // Bottom section with badges, stats, and button
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    // Badges and rating row - NiaTopicTag style
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Category tag - NiaTopicTag style
                        NiaTopicTag(
                            followed = true,
                            onClick = { },
                        ) {
                            Text(course.category.label)
                        }

                        // Difficulty tag with dots - NiaTopicTag style
                        NiaTopicTag(
                            followed = false,
                            onClick = { },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                val difficultyLevel = when (course.difficulty) {
                                    CourseDifficulty.BEGINNER -> 1
                                    CourseDifficulty.INTERMEDIATE -> 2
                                    CourseDifficulty.ADVANCED -> 3
                                }
                                repeat(3) { index ->
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(
                                                color = if (index < difficultyLevel) {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                                },
                                                shape = CircleShape
                                            )
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(course.difficulty.label)
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Rating stars
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFFFFB800),
                            )
                            Text(
                                text = "4.8",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Divider
                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Stats row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.PlayCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = accentColor,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${course.totalLessons} lessons",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = accentColor,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${course.estimatedDays} days",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Full-width Enroll button - prominent CTA
                    Button(
                        onClick = onEnroll,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            contentColor = Color.White,
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 4.dp,
                            pressedElevation = 2.dp,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.School,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Start Learning",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            // FREE ribbon badge - top right corner
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp),
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF4CAF50),
                shadowElevation = 2.dp,
            ) {
                Text(
                    text = "FREE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    letterSpacing = 0.5.sp,
                )
            }
        }
    }
}

@Composable
private fun CourseTag(
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = containerColor,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun CourseStatItem(
    icon: ImageVector,
    value: String,
    label: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Get available courses
 */
internal fun getAvailableCourses(): List<Course> {
    return listOf(
        Course(
            id = "memorize_3_ayahs",
            title = "Memorize First 3 Ayahs",
            subtitle = "All 114 Surahs",
            description = "Start your memorization journey by learning the opening verses of every Surah in the Quran.",
            icon = Icons.Outlined.AutoStories,
            totalLessons = 114,
            estimatedDays = 114,
            difficulty = CourseDifficulty.BEGINNER,
            category = CourseCategory.MEMORIZATION,
            gradientColors = listOf(
                Color(0xFF667eea),
                Color(0xFF764ba2),
            ),
        ),
        Course(
            id = "daily_bukhari",
            title = "Daily Hadith",
            subtitle = "Sahih Al-Bukhari",
            description = "Read one authentic hadith from Sahih Al-Bukhari every day and build a consistent learning habit.",
            icon = Icons.Outlined.MenuBook,
            totalLessons = 365,
            estimatedDays = 365,
            difficulty = CourseDifficulty.BEGINNER,
            category = CourseCategory.HADITH,
            gradientColors = listOf(
                Color(0xFF11998e),
                Color(0xFF38ef7d),
            ),
        ),
        Course(
            id = "juz_amma",
            title = "Juz Amma Memorization",
            subtitle = "Last 37 Surahs",
            description = "Complete memorization of Juz Amma (30th part) - the most commonly recited surahs in prayers.",
            icon = Icons.Outlined.School,
            totalLessons = 37,
            // ~556 total ayahs in Juz Amma, estimate 5 ayahs/day = 111 days, rounded to 120
            estimatedDays = 120,
            difficulty = CourseDifficulty.INTERMEDIATE,
            category = CourseCategory.MEMORIZATION,
            gradientColors = listOf(
                Color(0xFFf093fb),
                Color(0xFFf5576c),
            ),
        ),
        Course(
            id = "quran_reading",
            title = "Complete Quran Reading",
            subtitle = "Read the entire Quran",
            description = "A structured plan to read the complete Quran with daily reading goals and progress tracking.",
            icon = Icons.Outlined.ImportContacts,
            totalLessons = 604,
            // 604 pages at 2 pages/day = 302 days, rounded to 300
            estimatedDays = 300,
            difficulty = CourseDifficulty.INTERMEDIATE,
            category = CourseCategory.QURAN,
            gradientColors = listOf(
                Color(0xFF4facfe),
                Color(0xFF00f2fe),
            ),
        ),
    )
}

/**
 * Swipeable Big Tiles for enrolled courses
 * Shows dynamic progress and quick actions for each enrolled course
 */
@Composable
private fun CourseSwipeableTiles(
    enrolledCourses: List<Course>,
    courseProgress: Map<String, Int>,
    onCourseClick: (Course) -> Unit,
    onContinueCourse: (Course) -> Unit,
    onUnenroll: (Course) -> Unit = {},
) {
    if (enrolledCourses.isEmpty()) return

    val pagerState = rememberPagerState(
        pageCount = { enrolledCourses.size },
        initialPage = 0,
    )
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Swipeable Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
            pageSpacing = 12.dp,
            contentPadding = PaddingValues(horizontal = 22.dp),
        ) { page ->
            val course = enrolledCourses[page]
            val progress = courseProgress[course.id] ?: 0

            CourseBigTile(
                course = course,
                progress = progress,
                onClick = { onCourseClick(course) },
                onContinue = { onContinueCourse(course) },
                onUnenroll = { onUnenroll(course) },
            )
        }

        // Page Indicators
        if (enrolledCourses.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                enrolledCourses.forEachIndexed { index, _ ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (isSelected) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                }
                            )
                            .clickable {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            }
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "Swipe for more courses",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Big Tile for a single enrolled course - Professional Udemy/Coursera style
 * Shows progress, next action, and quick continue button
 */
@Composable
private fun CourseBigTile(
    course: Course,
    progress: Int,
    onClick: () -> Unit,
    onContinue: () -> Unit,
    onUnenroll: () -> Unit = {},
) {
    val progressPercent = if (course.totalLessons > 0) {
        (progress * 100f / course.totalLessons).coerceIn(0f, 100f)
    } else 0f

    val isCompleted = progress >= course.totalLessons
    var showMenu by remember { mutableStateOf(false) }

    // Use different container colors based on category (like home tiles)
    val (containerColor, accentColor) = when (course.category) {
        CourseCategory.MEMORIZATION -> Pair(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.primary
        )
        CourseCategory.HADITH -> Pair(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.secondary
        )
        CourseCategory.QURAN -> Pair(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.tertiary
        )
    }

    Card(
        modifier = Modifier
            .fillMaxSize()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = accentColor.copy(alpha = 0.2f),
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Top section with course info - Use category container color like home tiles
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(containerColor)
                    .clickable(onClick = onClick)
                    .padding(16.dp),
            ) {
                // More options menu
                Box(
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "View Details",
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            },
                            onClick = {
                                showMenu = false
                                onClick()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Unenroll",
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                showMenu = false
                                onUnenroll()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.RemoveCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Course category tag
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = accentColor.copy(alpha = 0.1f),
                    ) {
                        Text(
                            text = course.category.label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            letterSpacing = 1.sp,
                        )
                    }

                    // Course Title
                    Column {
                        Text(
                            text = course.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = course.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Bottom section with progress and action
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                // Progress bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${progressPercent.toInt()}% complete",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "$progress/${course.totalLessons} lessons",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { progressPercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = accentColor,
                    trackColor = accentColor.copy(alpha = 0.2f),
                    strokeCap = StrokeCap.Round,
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Continue button - Material 3 Expressive style with category accent color
                Button(
                    onClick = onContinue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(50), // Full pill shape
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = Color.White,
                        disabledContainerColor = accentColor.copy(alpha = 0.5f),
                        disabledContentColor = Color.White.copy(alpha = 0.7f),
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 4.dp,
                        pressedElevation = 8.dp,
                    ),
                    enabled = !isCompleted,
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    Icon(
                        imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isCompleted) "Completed" else "Continue Learning",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                    )
                }
            }
        }
    }
}

/**
 * Get Surah name by number (1-114)
 */
private fun getSurahName(surahNumber: Int): String {
    val surahNames = listOf(
        "Al-Fatihah", "Al-Baqarah", "Aal-E-Imran", "An-Nisa", "Al-Ma'idah",
        "Al-An'am", "Al-A'raf", "Al-Anfal", "At-Tawbah", "Yunus",
        "Hud", "Yusuf", "Ar-Ra'd", "Ibrahim", "Al-Hijr",
        "An-Nahl", "Al-Isra", "Al-Kahf", "Maryam", "Ta-Ha",
        "Al-Anbiya", "Al-Hajj", "Al-Mu'minun", "An-Nur", "Al-Furqan",
        "Ash-Shu'ara", "An-Naml", "Al-Qasas", "Al-Ankabut", "Ar-Rum",
        "Luqman", "As-Sajdah", "Al-Ahzab", "Saba", "Fatir",
        "Ya-Sin", "As-Saffat", "Sad", "Az-Zumar", "Ghafir",
        "Fussilat", "Ash-Shura", "Az-Zukhruf", "Ad-Dukhan", "Al-Jathiyah",
        "Al-Ahqaf", "Muhammad", "Al-Fath", "Al-Hujurat", "Qaf",
        "Adh-Dhariyat", "At-Tur", "An-Najm", "Al-Qamar", "Ar-Rahman",
        "Al-Waqi'ah", "Al-Hadid", "Al-Mujadila", "Al-Hashr", "Al-Mumtahanah",
        "As-Saff", "Al-Jumu'ah", "Al-Munafiqun", "At-Taghabun", "At-Talaq",
        "At-Tahrim", "Al-Mulk", "Al-Qalam", "Al-Haqqah", "Al-Ma'arij",
        "Nuh", "Al-Jinn", "Al-Muzzammil", "Al-Muddaththir", "Al-Qiyamah",
        "Al-Insan", "Al-Mursalat", "An-Naba", "An-Nazi'at", "Abasa",
        "At-Takwir", "Al-Infitar", "Al-Mutaffifin", "Al-Inshiqaq", "Al-Buruj",
        "At-Tariq", "Al-A'la", "Al-Ghashiyah", "Al-Fajr", "Al-Balad",
        "Ash-Shams", "Al-Layl", "Ad-Duha", "Ash-Sharh", "At-Tin",
        "Al-Alaq", "Al-Qadr", "Al-Bayyinah", "Az-Zalzalah", "Al-Adiyat",
        "Al-Qari'ah", "At-Takathur", "Al-Asr", "Al-Humazah", "Al-Fil",
        "Quraysh", "Al-Ma'un", "Al-Kawthar", "Al-Kafirun", "An-Nasr",
        "Al-Masad", "Al-Ikhlas", "Al-Falaq", "An-Nas"
    )
    return surahNames.getOrElse(surahNumber - 1) { "Surah $surahNumber" }
}

/**
 * Get Juz Amma Surah name by progress index
 * Juz Amma contains Surahs 78-114 (37 surahs)
 */
private fun getJuzAmmaSurahName(progressIndex: Int): String {
    val juzAmmaStart = 78
    val surahNumber = juzAmmaStart + progressIndex
    return if (surahNumber <= 114) {
        "Surah ${getSurahName(surahNumber)}"
    } else {
        "Completed"
    }
}
