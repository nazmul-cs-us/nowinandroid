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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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

        // All Courses enrolled - Rich achievement section
        if (exploreCourses.isEmpty() && myCourses.isNotEmpty()) {
            item {
                AllCoursesEnrolledSection(
                    myCourses = myCourses,
                    courseProgress = courseProgress,
                    prefs = prefs,
                    onCourseClick = onCourseClick,
                )
            }
        }
    }
}

/**
 * Rich "All Courses Enrolled" section with achievements, stats, and suggestions
 */
@Composable
private fun AllCoursesEnrolledSection(
    myCourses: List<Course>,
    courseProgress: Map<String, Int>,
    prefs: android.content.SharedPreferences,
    onCourseClick: (String) -> Unit,
) {
    val totalCourses = myCourses.size
    val totalLessons = myCourses.sumOf { it.totalLessons }
    val completedLessons = courseProgress.values.sum()
    val overallProgress = if (totalLessons > 0) (completedLessons.toFloat() / totalLessons * 100).toInt() else 0
    val completedCourses = myCourses.count { course ->
        val progress = courseProgress[course.id] ?: 0
        progress >= course.totalLessons
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        // Progress Overview Card with Pager for multiple graph views
        val progressPagerState = rememberPagerState(pageCount = { 4 })
        val pagerCoroutineScope = rememberCoroutineScope()

        // Generate 7-day progress data with persistence
        val today = java.time.LocalDate.now()
        val dayLabels = (6 downTo 0).map { daysAgo ->
            today.minusDays(daysAgo.toLong()).dayOfWeek.name.take(3)
        }

        // Store and retrieve per-course daily progress from SharedPreferences
        val todayKey = today.toString()

        // Save today's progress for each course
        LaunchedEffect(courseProgress) {
            myCourses.forEach { course ->
                val progress = courseProgress[course.id] ?: 0
                prefs.edit().putInt("course_${course.id}_$todayKey", progress).apply()
            }
        }

        // Per-course progress history (7 days for each course)
        data class CourseProgressData(
            val course: Course,
            val color: Color,
            val history: List<Int>
        )

        val courseColors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.error,
        )

        val courseProgressHistory = remember(courseProgress) {
            myCourses.mapIndexed { index, course ->
                val currentProgress = courseProgress[course.id] ?: 0
                val history = (6 downTo 0).map { daysAgo ->
                    val date = today.minusDays(daysAgo.toLong()).toString()
                    val saved = prefs.getInt("course_${course.id}_$date", -1)
                    if (saved >= 0) {
                        saved
                    } else {
                        // Estimate past progress
                        (currentProgress * (1 - daysAgo * 0.15f)).toInt().coerceAtLeast(0)
                    }
                }
                CourseProgressData(
                    course = course,
                    color = courseColors[index % courseColors.size],
                    history = history
                )
            }
        }

        // Selected day for interaction (-1 = none selected)
        var selectedDayIndex by remember { mutableStateOf(-1) }

        // Selected legend item for showing full name (-1 = none selected)
        var selectedLegendIndex by remember { mutableStateOf(-1) }

        // Calculate max value for chart scaling
        val chartMaxValue = courseProgressHistory
            .flatMap { it.history }
            .maxOrNull()
            ?.coerceAtLeast(5) ?: 5

        // Material 3 Expressive Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            tonalElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 12.dp),
            ) {
                // Header with icon - Material 3 Expressive style
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = "Learning Progress",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    ) {
                        Text(
                            text = if (overallProgress >= 100) "Complete" else "In Progress",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Pager with different graph views
                HorizontalPager(
                    state = progressPagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                ) { page ->
                    when (page) {
                        0 -> {
                            // Page 1: Interactive 7-Day Multi-Course Progress Chart
                            Column(
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                // Interactive Legend - expands on tap to show full name
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    courseProgressHistory.forEachIndexed { index, data ->
                                        val isSelected = selectedLegendIndex == index
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isSelected) data.color.copy(alpha = 0.25f) else data.color.copy(alpha = 0.12f),
                                            modifier = Modifier
                                                .then(
                                                    if (isSelected) Modifier.weight(2f)
                                                    else Modifier.weight(1f)
                                                )
                                                .animateContentSize(
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessLow
                                                    )
                                                )
                                                .clickable {
                                                    selectedLegendIndex = if (isSelected) -1 else index
                                                },
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center,
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .background(data.color, CircleShape)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = if (isSelected) data.course.title else data.course.title.take(6),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = if (isSelected) 10.sp else 9.sp,
                                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                                    color = data.color,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHighest

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .padding(horizontal = 8.dp)
                                        .pointerInput(Unit) {
                                            detectTapGestures { offset ->
                                                val dayWidth = size.width / 6f
                                                val tappedDay = (offset.x / dayWidth).toInt().coerceIn(0, 6)
                                                selectedDayIndex = if (selectedDayIndex == tappedDay) -1 else tappedDay
                                            }
                                        }
                                ) {
                                    Canvas(
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        val width = size.width
                                        val height = size.height - 10f
                                        val pointSpacing = width / 6f
                                        val maxVal = chartMaxValue.toFloat()

                                        // Draw subtle horizontal grid lines
                                        for (i in 0..4) {
                                            val y = height - (height * i / 4)
                                            drawLine(
                                                color = surfaceColor.copy(alpha = 0.5f),
                                                start = Offset(0f, y),
                                                end = Offset(width, y),
                                                strokeWidth = 1f,
                                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f)),
                                            )
                                        }

                                        // Draw selected day highlight with gradient
                                        if (selectedDayIndex >= 0) {
                                            val x = selectedDayIndex * pointSpacing
                                            drawRect(
                                                brush = Brush.verticalGradient(
                                                    colors = listOf(
                                                        surfaceColor.copy(alpha = 0.3f),
                                                        surfaceColor.copy(alpha = 0.1f),
                                                    ),
                                                ),
                                                topLeft = Offset(x - pointSpacing / 2, 0f),
                                                size = androidx.compose.ui.geometry.Size(pointSpacing, height),
                                            )
                                        }

                                        // Draw smooth curves with gradient fills for each course
                                        courseProgressHistory.forEachIndexed { courseIndex, data ->
                                            // Calculate points
                                            val points = data.history.mapIndexed { index, value ->
                                                Offset(
                                                    x = index * pointSpacing,
                                                    y = height - (height * value / maxVal).coerceAtMost(height)
                                                )
                                            }

                                            // Create smooth curved path using quadratic bezier
                                            val curvePath = Path().apply {
                                                if (points.isNotEmpty()) {
                                                    moveTo(points[0].x, points[0].y)
                                                    for (i in 1 until points.size) {
                                                        val prev = points[i - 1]
                                                        val curr = points[i]
                                                        val midX = (prev.x + curr.x) / 2
                                                        quadraticBezierTo(prev.x + (midX - prev.x) * 0.8f, prev.y, midX, (prev.y + curr.y) / 2)
                                                        quadraticBezierTo(curr.x - (curr.x - midX) * 0.8f, curr.y, curr.x, curr.y)
                                                    }
                                                }
                                            }

                                            // Create area path with gradient fill (only for first/main course)
                                            if (courseIndex == 0 && points.isNotEmpty()) {
                                                val areaPath = Path().apply {
                                                    moveTo(points[0].x, height)
                                                    lineTo(points[0].x, points[0].y)
                                                    for (i in 1 until points.size) {
                                                        val prev = points[i - 1]
                                                        val curr = points[i]
                                                        val midX = (prev.x + curr.x) / 2
                                                        quadraticBezierTo(prev.x + (midX - prev.x) * 0.8f, prev.y, midX, (prev.y + curr.y) / 2)
                                                        quadraticBezierTo(curr.x - (curr.x - midX) * 0.8f, curr.y, curr.x, curr.y)
                                                    }
                                                    lineTo(points.last().x, height)
                                                    close()
                                                }
                                                drawPath(
                                                    path = areaPath,
                                                    brush = Brush.verticalGradient(
                                                        colors = listOf(
                                                            data.color.copy(alpha = 0.3f),
                                                            data.color.copy(alpha = 0.05f),
                                                        ),
                                                    ),
                                                )
                                            }

                                            // Draw curved line with shadow effect
                                            drawPath(
                                                path = curvePath,
                                                color = data.color.copy(alpha = 0.3f),
                                                style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                                            )
                                            drawPath(
                                                path = curvePath,
                                                color = data.color,
                                                style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                                            )

                                            // Draw data points with glow effect
                                            points.forEachIndexed { index, point ->
                                                val isSelected = index == selectedDayIndex
                                                // Outer glow
                                                drawCircle(
                                                    color = data.color.copy(alpha = 0.3f),
                                                    radius = if (isSelected) 12f else 8f,
                                                    center = point,
                                                )
                                                // Main point
                                                drawCircle(
                                                    color = data.color,
                                                    radius = if (isSelected) 7f else 5f,
                                                    center = point,
                                                )
                                                // Inner highlight
                                                drawCircle(
                                                    color = Color.White,
                                                    radius = if (isSelected) 3.5f else 2.5f,
                                                    center = point,
                                                )
                                            }
                                        }
                                    }

                                    // Professional tooltip when day is selected
                                    if (selectedDayIndex >= 0) {
                                        Surface(
                                            modifier = Modifier
                                                .align(Alignment.TopCenter)
                                                .padding(top = 4.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.inverseSurface,
                                            shadowElevation = 8.dp,
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(12.dp),
                                                horizontalAlignment = Alignment.Start,
                                            ) {
                                                // Day header
                                                Text(
                                                    text = dayLabels[selectedDayIndex],
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.inverseOnSurface,
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                // Course progress items
                                                courseProgressHistory.forEach { data ->
                                                    Row(
                                                        modifier = Modifier.padding(vertical = 2.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        // Color indicator line
                                                        Box(
                                                            modifier = Modifier
                                                                .width(3.dp)
                                                                .height(16.dp)
                                                                .background(data.color, RoundedCornerShape(2.dp))
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        // Course name
                                                        Text(
                                                            text = data.course.title.take(10),
                                                            style = MaterialTheme.typography.labelMedium,
                                                            color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.8f),
                                                            modifier = Modifier.weight(1f),
                                                        )
                                                        Spacer(modifier = Modifier.width(12.dp))
                                                        // Lesson count
                                                        Text(
                                                            text = "${data.history[selectedDayIndex]} lessons",
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = MaterialTheme.colorScheme.inverseOnSurface,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Clickable day labels
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    dayLabels.forEachIndexed { index, day ->
                                        Surface(
                                            modifier = Modifier
                                                .clickable {
                                                    selectedDayIndex = if (selectedDayIndex == index) -1 else index
                                                },
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (index == selectedDayIndex) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                Color.Transparent
                                            },
                                        ) {
                                            Text(
                                                text = day,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (index == selectedDayIndex) {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                fontWeight = if (index == selectedDayIndex) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 9.sp,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        1 -> {
                            // Page 2: Circular Progress with course bars
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Main circular progress
                                Box(
                                    modifier = Modifier.size(130.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        progress = { 1f },
                                        modifier = Modifier.size(130.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        strokeWidth = 14.dp,
                                        strokeCap = StrokeCap.Round,
                                    )
                                    CircularProgressIndicator(
                                        progress = { overallProgress / 100f },
                                        modifier = Modifier.size(130.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 14.dp,
                                        strokeCap = StrokeCap.Round,
                                    )
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "$overallProgress%",
                                            style = MaterialTheme.typography.headlineLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            text = "Overall",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }

                                // Course mini progress
                                Column(
                                    modifier = Modifier.padding(start = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    myCourses.forEach { course ->
                                        val progress = courseProgress[course.id] ?: 0
                                        val progressPercent = if (course.totalLessons > 0) {
                                            (progress.toFloat() / course.totalLessons * 100).toInt()
                                        } else 0

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier.size(36.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                CircularProgressIndicator(
                                                    progress = { 1f },
                                                    modifier = Modifier.size(36.dp),
                                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                    strokeWidth = 4.dp,
                                                )
                                                CircularProgressIndicator(
                                                    progress = { progressPercent / 100f },
                                                    modifier = Modifier.size(36.dp),
                                                    color = when (course.category) {
                                                        CourseCategory.MEMORIZATION -> MaterialTheme.colorScheme.primary
                                                        CourseCategory.HADITH -> MaterialTheme.colorScheme.secondary
                                                        CourseCategory.QURAN -> MaterialTheme.colorScheme.tertiary
                                                    },
                                                    strokeWidth = 4.dp,
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = course.title.take(12) + if (course.title.length > 12) ".." else "",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    maxLines = 1,
                                                )
                                                Text(
                                                    text = "$progressPercent%",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        2 -> {
                            // Page 3: Bar chart style progress
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.SpaceEvenly,
                            ) {
                                myCourses.forEach { course ->
                                    val progress = courseProgress[course.id] ?: 0
                                    val progressPercent = if (course.totalLessons > 0) {
                                        (progress.toFloat() / course.totalLessons * 100).toInt()
                                    } else 0
                                    val isComplete = progressPercent >= 100

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        // Course icon
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(
                                                    color = when (course.category) {
                                                        CourseCategory.MEMORIZATION -> MaterialTheme.colorScheme.primaryContainer
                                                        CourseCategory.HADITH -> MaterialTheme.colorScheme.secondaryContainer
                                                        CourseCategory.QURAN -> MaterialTheme.colorScheme.tertiaryContainer
                                                    },
                                                    shape = RoundedCornerShape(10.dp),
                                                ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                imageVector = if (isComplete) Icons.Default.Check else course.icon,
                                                contentDescription = null,
                                                modifier = Modifier.size(22.dp),
                                                tint = when (course.category) {
                                                    CourseCategory.MEMORIZATION -> MaterialTheme.colorScheme.onPrimaryContainer
                                                    CourseCategory.HADITH -> MaterialTheme.colorScheme.onSecondaryContainer
                                                    CourseCategory.QURAN -> MaterialTheme.colorScheme.onTertiaryContainer
                                                },
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                            ) {
                                                Text(
                                                    text = course.title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f),
                                                )
                                                Text(
                                                    text = "$progress/${course.totalLessons}",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            LinearProgressIndicator(
                                                progress = { progressPercent / 100f },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(8.dp)
                                                    .clip(RoundedCornerShape(4.dp)),
                                                color = when (course.category) {
                                                    CourseCategory.MEMORIZATION -> MaterialTheme.colorScheme.primary
                                                    CourseCategory.HADITH -> MaterialTheme.colorScheme.secondary
                                                    CourseCategory.QURAN -> MaterialTheme.colorScheme.tertiary
                                                },
                                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        3 -> {
                            // Page 4: Stats summary
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                ) {
                                    // Lessons completed
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .background(
                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                    shape = CircleShape,
                                                ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = "$completedLessons",
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Lessons",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text = "Completed",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }

                                    // Courses
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .background(
                                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                                    shape = CircleShape,
                                                ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = "$completedCourses",
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Courses",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text = "Finished",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }

                                    // Remaining
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .background(
                                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                                    shape = CircleShape,
                                                ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = "${totalLessons - completedLessons}",
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Lessons",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text = "Remaining",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                // Achievement badge
                                if (overallProgress >= 50) {
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.EmojiEvents,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (overallProgress >= 100) "Learning Champion!"
                                                       else if (overallProgress >= 75) "Almost There!"
                                                       else "Great Progress!",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Page indicators (clickable with larger touch target)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    repeat(4) { index ->
                        Box(
                            modifier = Modifier
                                .clickable {
                                    pagerCoroutineScope.launch {
                                        progressPagerState.animateScrollToPage(index)
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(
                                        width = if (progressPagerState.currentPage == index) 20.dp else 8.dp,
                                        height = 8.dp
                                    )
                                    .background(
                                        color = if (progressPagerState.currentPage == index) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHighest
                                        },
                                        shape = RoundedCornerShape(4.dp),
                                    )
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Stats Dashboard
        Text(
            text = "Your Progress",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Courses stat
            StatCard(
                icon = Icons.Outlined.School,
                value = "$completedCourses/$totalCourses",
                label = "Courses",
                modifier = Modifier.weight(1f),
            )

            // Lessons stat
            StatCard(
                icon = Icons.Outlined.MenuBook,
                value = "$completedLessons/$totalLessons",
                label = "Lessons",
                modifier = Modifier.weight(1f),
            )

            // Progress stat
            StatCard(
                icon = Icons.Outlined.TrendingUp,
                value = "$overallProgress%",
                label = "Complete",
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Suggestions Section
        Text(
            text = "What's Next?",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        // Review suggestion card
        SuggestionCard(
            icon = Icons.Outlined.Refresh,
            title = "Review & Practice",
            description = "Revisit your courses to strengthen your knowledge",
            actionText = "Continue Learning",
            onClick = {
                // Navigate to first incomplete course or first course
                val incompleteCoursE = myCourses.firstOrNull { course ->
                    val progress = courseProgress[course.id] ?: 0
                    progress < course.totalLessons
                }
                onCourseClick(incompleteCoursE?.id ?: myCourses.first().id)
            },
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Check back suggestion card
        SuggestionCard(
            icon = Icons.Outlined.Notifications,
            title = "New Courses Coming",
            description = "We're working on more courses. Check back soon!",
            actionText = null,
            onClick = null,
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Course completion overview
        if (myCourses.isNotEmpty()) {
            Text(
                text = "Course Overview",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            myCourses.forEach { course ->
                val progress = courseProgress[course.id] ?: 0
                val progressPercent = if (course.totalLessons > 0) {
                    (progress.toFloat() / course.totalLessons * 100).toInt()
                } else 0
                val isComplete = progress >= course.totalLessons

                CourseProgressRow(
                    courseName = course.title,
                    progress = progressPercent,
                    isComplete = isComplete,
                    onClick = { onCourseClick(course.id) },
                )

                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    // Material 3 Expressive Card with larger corners and subtle border
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SuggestionCard(
    icon: ImageVector,
    title: String,
    description: String,
    actionText: String?,
    onClick: (() -> Unit)?,
) {
    // Material 3 Expressive Card with layered background
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Icon with expressive background
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (actionText != null && onClick != null) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                    modifier = Modifier.size(32.dp),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseProgressRow(
    courseName: String,
    progress: Int,
    isComplete: Boolean,
    onClick: () -> Unit,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest

    // Material 3 Expressive Card with circular progress indicator
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (isComplete) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            }
        ),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Circular progress indicator
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Background circle
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.size(48.dp),
                    color = trackColor,
                    strokeWidth = 4.dp,
                    strokeCap = StrokeCap.Round,
                )
                // Progress circle
                CircularProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.size(48.dp),
                    color = if (isComplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                    strokeWidth = 4.dp,
                    strokeCap = StrokeCap.Round,
                )
                // Center content
                if (isComplete) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                } else {
                    Text(
                        text = "$progress%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = courseName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (isComplete) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Course Completed",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    Text(
                        text = "${100 - progress}% remaining",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
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
        Course(
            id = "complete_quran_listening",
            title = "Complete Quran Listening",
            subtitle = "Listen to entire Quran",
            description = "Listen to all 114 surahs during your commute. Progress saves automatically and resumes where you left off. Plays after Daily Hadith when driving.",
            icon = Icons.Outlined.Headphones,
            totalLessons = 114,
            // Average surah ~5-10 min, driving ~30min/day = 2-3 surahs/day, ~40-60 days
            estimatedDays = 60,
            difficulty = CourseDifficulty.BEGINNER,
            category = CourseCategory.QURAN,
            gradientColors = listOf(
                Color(0xFF00b4db),
                Color(0xFF0083b0),
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
