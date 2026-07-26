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
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import com.starception.submission.voice.SherpaOnnxTtsService
import com.starception.submission.core.designsystem.component.NiaOutlinedButton
import com.starception.submission.core.hadithdatabase.BukhariLocalTranslationRepository
import com.starception.submission.core.hadithdatabase.HadithDatabase
import com.starception.submission.core.hadithdatabase.HadithRepository
import com.starception.submission.download.MissingContentCard
import com.starception.submission.settings.components.TtsVoice
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
import dagger.hilt.android.EntryPointAccessors
import com.starception.submission.core.designsystem.component.NiaTopicTag
import com.starception.submission.core.ui.FlaticonIcon
import com.starception.submission.core.ui.FlaticonIcons
import com.starception.submission.voice.SherpaOnnxTtsEntryPoint

/**
 * Course data model
 */
data class Course(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val iconGlyph: String,
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
    val entryPoint = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            SherpaOnnxTtsEntryPoint::class.java
        )
    }
    val downloadManager = remember { entryPoint.assetDownloadManager() }

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
    var pendingBukhariEnrollment by rememberSaveable { mutableStateOf(false) }

    // Get progress for each enrolled course (reactive to changes)
    val courseProgress = remember(enrolledCourseIds, progressUpdateTrigger) {
        myCourses.associate { course ->
            course.id to prefs.getInt("progress_${course.id}", 0)
        }
    }

    fun enrollCourse(courseId: String) {
        val newSet = enrolledCourseIds.toMutableSet()
        newSet.add(courseId)
        enrolledCourseIds = newSet
        prefs.edit().putStringSet("enrolled_courses", newSet).apply()
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

    // Pre-generate TTS for next 3 hadiths when Daily Bukhari is enrolled
    // This eliminates delay when user clicks "Continue Learning"
    LaunchedEffect(enrolledCourseIds, courseProgress) {
        if ("daily_bukhari" in enrolledCourseIds) {
            val currentProgress = prefs.getInt("progress_daily_bukhari", 0)
            preGenerateHadithTts(context, currentProgress + 1)
        }
    }

    // Show lesson completion as a bottom sheet.
    // Capture values to avoid smart cast issues with delegated properties
    val currentCourseId = pendingCourseId
    val currentLessonId = pendingLessonId
    val currentLessonTitle = pendingLessonTitle

    if (dialogShown && currentCourseId != null && currentLessonId != null && currentLessonTitle != null) {
        LessonCompletionBottomSheet(
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
            CourseHeader(
                enrolledCount = myCourses.size,
                completedLessons = courseProgress.values.sum(),
                totalLessons = myCourses.sumOf { it.totalLessons },
            )
        }

        // Swipeable Big Tile for enrolled courses (only show if enrolled)
        if (myCourses.isNotEmpty()) {
            item {
                SectionTitle(
                    title = "Continue learning",
                    subtitle = "${myCourses.size} active",
                )
            }

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
        if (exploreCourses.isNotEmpty()) {
            item {
                SectionTitle(
                    title = if (myCourses.isEmpty()) "Start your journey" else "Explore courses",
                    subtitle = "${exploreCourses.size} available",
                )
            }
        }

        items(exploreCourses) { course ->
            Column {
                CourseCard(
                    course = course,
                    onClick = {
                        // Preview course before enrolling
                        onCourseClick(course.id)
                    },
                    onEnroll = {
                        if (course.id == "daily_bukhari" && !HadithDatabase.isDatabaseAvailable(context, "sahih_bukhari.db")) {
                            pendingBukhariEnrollment = true
                        } else {
                            enrollCourse(course.id)
                        }
                    },
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                )

                if (course.id == "daily_bukhari" && pendingBukhariEnrollment) {
                    MissingContentCard(
                        resourceName = "Sahih Bukhari",
                        category = "hadith_sahih_bukhari",
                        description = "Download Sahih Bukhari to enable Daily Hadith enrollment and play the hadith automatically after travel dua when you start the audio chain.",
                        downloadManager = downloadManager,
                        onDownloadComplete = {
                            HadithDatabase.clearInstance(context, "sahih_bukhari.db")
                            pendingBukhariEnrollment = false
                            enrollCourse(course.id)
                        },
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
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

        // Compact analytics card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
            ),
            tonalElevation = 1.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp, start = 18.dp, end = 18.dp, bottom = 10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Box(
                                modifier = Modifier.size(44.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                FlaticonIcon(
                                    glyph = FlaticonIcons.TRENDING_UP,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    fontSize = 21.sp,
                                )
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Learning progress",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "$completedLessons of $totalLessons lessons complete",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = "$overallProgress%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                LinearProgressIndicator(
                    progress = { overallProgress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(999.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    strokeCap = StrokeCap.Round,
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Pager with different graph views
                HorizontalPager(
                    state = progressPagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                ) { page ->
                    when (page) {
                        0 -> {
                            // Page 1: Interactive 7-Day Multi-Course Progress Chart
                            Column(
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                // Scrollable legend keeps course names readable as the catalog grows.
                                LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    items(courseProgressHistory) { data ->
                                        val index = courseProgressHistory.indexOf(data)
                                        val isSelected = selectedLegendIndex == index
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isSelected) data.color.copy(alpha = 0.25f) else data.color.copy(alpha = 0.12f),
                                            modifier = Modifier
                                                .widthIn(max = 154.dp)
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
                                                    text = data.course.title,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = 10.sp,
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
                                            FlaticonIcon(
                                                glyph = if (isComplete) FlaticonIcons.CHECK else course.iconGlyph,
                                                contentDescription = null,
                                                tint = when (course.category) {
                                                    CourseCategory.MEMORIZATION -> MaterialTheme.colorScheme.onPrimaryContainer
                                                    CourseCategory.HADITH -> MaterialTheme.colorScheme.onSecondaryContainer
                                                    CourseCategory.QURAN -> MaterialTheme.colorScheme.onTertiaryContainer
                                                },
                                                fontSize = 22.sp,
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
                                            FlaticonIcon(
                                                glyph = FlaticonIcons.TROPHY,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                fontSize = 20.sp,
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
                iconGlyph = FlaticonIcons.SCHOOL,
                value = "$completedCourses/$totalCourses",
                label = "Courses",
                modifier = Modifier.weight(1f),
            )

            // Lessons stat
            StatCard(
                iconGlyph = FlaticonIcons.BOOK,
                value = "$completedLessons/$totalLessons",
                label = "Lessons",
                modifier = Modifier.weight(1f),
            )

            // Progress stat
            StatCard(
                iconGlyph = FlaticonIcons.TRENDING_UP,
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
            iconGlyph = FlaticonIcons.REFRESH,
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
            iconGlyph = FlaticonIcons.NOTIFICATIONS,
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
    iconGlyph: String,
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
                FlaticonIcon(
                    glyph = iconGlyph,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    fontSize = 20.sp,
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
    iconGlyph: String,
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
                    FlaticonIcon(
                        glyph = iconGlyph,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        fontSize = 24.sp,
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
                        FlaticonIcon(
                            glyph = FlaticonIcons.ANGLE_RIGHT,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            fontSize = 20.sp,
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
                        FlaticonIcon(
                            glyph = FlaticonIcons.CHECK,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 18.sp,
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
                        FlaticonIcon(
                            glyph = FlaticonIcons.COMPLETED,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp,
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

            FlaticonIcon(
                glyph = FlaticonIcons.ANGLE_RIGHT,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 22.sp,
            )
        }
    }
}

@Composable
private fun CourseHeader(
    enrolledCount: Int,
    completedLessons: Int,
    totalLessons: Int,
) {
    val hubAccent = MaterialTheme.colorScheme.primary
    val hubShape = RoundedCornerShape(30.dp)
    val overallProgress = if (totalLessons > 0) {
        (completedLessons.toFloat() / totalLessons).coerceIn(0f, 1f)
    } else {
        0f
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 14.dp)
            .clip(hubShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                shape = hubShape,
            ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 38.dp, y = (-42).dp)
                .size(150.dp)
                .border(28.dp, hubAccent.copy(alpha = 0.045f), CircleShape),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 24.dp, y = 42.dp)
                .size(94.dp)
                .background(Color(0xFFE6B95C).copy(alpha = 0.10f), CircleShape),
        )

        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                    contentColor = hubAccent,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        FlaticonIcon(
                            glyph = FlaticonIcons.SCHOOL,
                            contentDescription = null,
                            tint = hubAccent,
                            fontSize = 15.sp,
                        )
                        Text(
                            text = "LEARNING HUB",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        )
                    }
                }

                Surface(
                    color = Color(0xFFFFE7AD).copy(alpha = 0.65f),
                    contentColor = Color(0xFF8A6200),
                    shape = CircleShape,
                ) {
                    Box(
                        modifier = Modifier.size(38.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        FlaticonIcon(
                            glyph = FlaticonIcons.TROPHY,
                            contentDescription = null,
                            tint = Color(0xFF8A6200),
                            fontSize = 19.sp,
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = if (enrolledCount > 0) "Keep your momentum" else "Begin with one small step",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (enrolledCount > 0) {
                        "$completedLessons lessons completed across $enrolledCount ${if (enrolledCount == 1) "course" else "courses"}"
                    } else {
                        "Guided Quran and Hadith lessons, designed for a steady daily rhythm."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (enrolledCount > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Overall progress",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "${(overallProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = hubAccent,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { overallProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(999.dp)),
                        color = hubAccent,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        strokeCap = StrokeCap.Round,
                    )
                }
            }
        }
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
            .padding(horizontal = 22.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
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
                        FlaticonIcon(
                            glyph = course.iconGlyph,
                            contentDescription = null,
                            tint = Color.White,
                            fontSize = 28.sp,
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
    val (containerColor, accentColor) = when (course.category) {
        CourseCategory.MEMORIZATION -> Pair(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.primary,
        )
        CourseCategory.HADITH -> Pair(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.secondary,
        )
        CourseCategory.QURAN -> Pair(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.tertiary,
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        ),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                containerColor.copy(alpha = 0.95f),
                                containerColor.copy(alpha = 0.75f),
                            ),
                        ),
                    )
                    .padding(18.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = accentColor.copy(alpha = 0.14f),
                            ) {
                                Box(
                                    modifier = Modifier.size(52.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    FlaticonIcon(
                                        glyph = course.iconGlyph,
                                        contentDescription = null,
                                        tint = accentColor,
                                        fontSize = 28.sp,
                                    )
                                }
                            }

                            Column {
                                Text(
                                    text = course.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 28.sp,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = course.subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = Color(0xFF2E7D32),
                            ) {
                                Text(
                                    text = "FREE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    letterSpacing = 0.4.sp,
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = "${course.totalLessons}",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = accentColor,
                                    )
                                    Text(
                                        text = if (course.totalLessons == 1) "lesson" else "lessons",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = course.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.84f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CourseMetaChip(
                        iconGlyph = FlaticonIcons.BOOKMARK,
                        text = course.category.label,
                    )
                    CourseMetaChip(
                        iconGlyph = FlaticonIcons.DIFFICULTY,
                        text = course.difficulty.label,
                    )
                    CourseMetaChip(
                        iconGlyph = FlaticonIcons.SCHEDULE,
                        text = "Self-paced",
                    )
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = containerColor.copy(alpha = 0.42f),
                    border = BorderStroke(
                        width = 1.dp,
                        color = accentColor.copy(alpha = 0.12f),
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CourseStatItem(
                                iconGlyph = FlaticonIcons.PLAY,
                                value = course.totalLessons.toString(),
                                label = if (course.totalLessons == 1) "lesson" else "lessons",
                                modifier = Modifier.weight(1f),
                                accentColor = accentColor,
                            )
                            CourseStatItem(
                                iconGlyph = FlaticonIcons.SCHEDULE,
                                value = course.estimatedDays.toString(),
                                label = if (course.estimatedDays == 1) "day" else "days",
                                modifier = Modifier.weight(1f),
                                accentColor = accentColor,
                            )
                        }

                        CoursePrimaryActionButton(
                            text = "Enroll Now",
                            iconGlyph = FlaticonIcons.PLAY,
                            onClick = onEnroll,
                        )
                    }
                }
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
private fun CourseMetaChip(
    iconGlyph: String,
    text: String,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FlaticonIcon(
                glyph = iconGlyph,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun CoursePrimaryActionButton(
    text: String,
    iconGlyph: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    NiaOutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    ) {
        FlaticonIcon(
            glyph = iconGlyph,
            contentDescription = null,
            fontSize = 22.sp,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
private fun CourseStatItem(
    iconGlyph: String,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = accentColor.copy(alpha = 0.12f),
        ) {
            Box(
                modifier = Modifier.size(34.dp),
                contentAlignment = Alignment.Center,
            ) {
                FlaticonIcon(
                    glyph = iconGlyph,
                    contentDescription = null,
                    tint = accentColor,
                    fontSize = 18.sp,
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
            iconGlyph = FlaticonIcons.QURAN,
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
            description = "Read one authentic hadith from Sahih Al-Bukhari every day. When enabled, it can also play automatically after travel dua during the driving audio chain.",
            iconGlyph = FlaticonIcons.BOOK,
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
            iconGlyph = FlaticonIcons.SCHOOL,
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
            iconGlyph = FlaticonIcons.OPEN_BOOK,
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
            iconGlyph = FlaticonIcons.VOLUME,
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
@OptIn(ExperimentalMaterial3Api::class)
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
    var showUnenrollConfirmation by remember { mutableStateOf(false) }

    val actionColor = when (course.category) {
        CourseCategory.MEMORIZATION -> Color(0xFF4F377D)
        CourseCategory.HADITH -> Color(0xFF07594F)
        CourseCategory.QURAN -> Color(0xFF083E55)
    }

    Card(
        modifier = Modifier
            .fillMaxSize()
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(26.dp),
                spotColor = actionColor.copy(alpha = 0.16f),
            ),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable(onClick = onClick)
                .padding(18.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 50.dp, y = (-62).dp)
                    .size(170.dp)
                    .border(30.dp, actionColor.copy(alpha = 0.045f), CircleShape),
            )

            Column(
                modifier = Modifier
                    .fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = actionColor.copy(alpha = 0.12f),
                        ) {
                            Box(
                                modifier = Modifier.size(44.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                FlaticonIcon(
                                    glyph = course.iconGlyph,
                                    contentDescription = null,
                                    tint = actionColor,
                                    fontSize = 24.sp,
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = actionColor.copy(alpha = 0.10f),
                        ) {
                            Text(
                                text = course.category.label.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = actionColor,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                letterSpacing = 0.8.sp,
                            )
                        }
                    }

                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        ) {
                            FlaticonIcon(
                                glyph = FlaticonIcons.MORE,
                                contentDescription = "More options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 18.sp,
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = course.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = course.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (progress == 0) "READY TO START" else "${progressPercent.toInt()}% COMPLETE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = actionColor,
                            letterSpacing = 0.6.sp,
                        )
                        Text(
                            text = "$progress/${course.totalLessons} lessons",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    LinearProgressIndicator(
                        progress = { progressPercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp)),
                        color = actionColor,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        strokeCap = StrokeCap.Round,
                    )

                    Button(
                        onClick = onContinue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        enabled = !isCompleted,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = actionColor,
                            contentColor = Color.White,
                            disabledContainerColor = actionColor.copy(alpha = 0.35f),
                            disabledContentColor = Color.White.copy(alpha = 0.75f),
                        ),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                    ) {
                        FlaticonIcon(
                            glyph = if (isCompleted) FlaticonIcons.COMPLETED else FlaticonIcons.PLAY,
                            contentDescription = null,
                            tint = LocalContentColor.current,
                            fontSize = 20.sp,
                        )
                        Spacer(modifier = Modifier.width(9.dp))
                        Text(
                            text = if (isCompleted) "Completed" else "Continue learning",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }

    if (showMenu) {
        ModalBottomSheet(
            onDismissRequest = { showMenu = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            dragHandle = {
                BottomSheetDefaults.DragHandle(
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 30.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(actionColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        FlaticonIcon(
                            glyph = course.iconGlyph,
                            contentDescription = null,
                            tint = Color.White,
                            fontSize = 29.sp,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Manage course",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = course.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            text = "${progressPercent.toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))

                CourseOptionRow(
                    iconGlyph = FlaticonIcons.INFO,
                    title = "View course details",
                    description = "Open the overview, outcomes, and lesson plan",
                    iconColor = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    onClick = {
                        showMenu = false
                        onClick()
                    },
                )

                CourseOptionRow(
                    iconGlyph = FlaticonIcons.REMOVE,
                    title = "Unenroll from course",
                    description = "Remove it from My Learning; your progress stays saved",
                    iconColor = MaterialTheme.colorScheme.error,
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                    onClick = {
                        showMenu = false
                        showUnenrollConfirmation = true
                    },
                )
            }
        }
    }

    if (showUnenrollConfirmation) {
        AlertDialog(
            onDismissRequest = { showUnenrollConfirmation = false },
            icon = {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Box(
                        modifier = Modifier.size(52.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        FlaticonIcon(
                            glyph = FlaticonIcons.WARNING,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            fontSize = 25.sp,
                        )
                    }
                }
            },
            title = {
                Text(
                    text = "Unenroll from this course?",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            },
            text = {
                Text(
                    text = "${course.title} will be removed from My Learning. Your $progress completed ${if (progress == 1) "lesson" else "lessons"} will remain saved if you enroll again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUnenrollConfirmation = false
                        onUnenroll()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text("Unenroll")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnenrollConfirmation = false }) {
                    Text("Keep course")
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
}

@Composable
private fun CourseOptionRow(
    iconGlyph: String,
    title: String,
    description: String,
    iconColor: Color,
    containerColor: Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = containerColor,
            ) {
                Box(
                    modifier = Modifier.size(46.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    FlaticonIcon(
                        glyph = iconGlyph,
                        contentDescription = null,
                        tint = iconColor,
                        fontSize = 22.sp,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (iconGlyph == FlaticonIcons.REMOVE) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlaticonIcon(
                glyph = FlaticonIcons.ANGLE_RIGHT,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 18.sp,
            )
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

/**
 * Pre-generate TTS for the next 3 hadiths starting from the given hadith number.
 * This runs in background so the audio is ready when user clicks "Continue Learning".
 *
 * Uses the same text format as DrivingAudioService for cache compatibility.
 */
private suspend fun preGenerateHadithTts(context: Context, startHadithNumber: Int) {
    try {
        android.util.Log.i("CourseScreen", "🔄 Pre-generating TTS for hadith #$startHadithNumber and next 2...")

        // Check TTS settings
        val ttsPrefs = context.getSharedPreferences("tts_settings", Context.MODE_PRIVATE)
        val selectedVoiceName = ttsPrefs.getString("selected_voice", null)
        val selectedSpeakerId = ttsPrefs.getInt("selected_speaker_id", 0)

        if (selectedVoiceName == null) {
            android.util.Log.d("CourseScreen", "🔄 Using Android TTS, no pre-generation needed")
            return
        }

        // Get SherpaOnnxTtsService via EntryPoint
        val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
            context.applicationContext,
            HadithTtsEntryPoint::class.java
        )
        val sherpaOnnxTts = entryPoint.sherpaOnnxTtsService()

        // Set voice from settings
        val voice = try {
            TtsVoice.valueOf(selectedVoiceName)
        } catch (e: Exception) {
            TtsVoice.KOKORO_EN
        }
        sherpaOnnxTts.setVoice(voice)

        // Use BukhariLocalTranslationRepository (SAME source as DrivingAudioService and HadithDetailScreen)
        // This ensures cache hash matches across all entry points
        val bukhariTranslationRepo = BukhariLocalTranslationRepository.getInstance(context)
        bukhariTranslationRepo.loadTranslations()

        // Fallback to database if needed
        val hadithRepository = HadithRepository.getInstance(context)

        // Pre-generate next 3 hadiths
        for (offset in 0 until 3) {
            val hadithNumber = startHadithNumber + offset
            if (hadithNumber > 7563) break // Max Bukhari hadiths

            // Use BukhariLocalTranslationRepository for text (same as HadithDetailScreen)
            val hadithText = bukhariTranslationRepo.getEnglishText(hadithNumber)
                ?: run {
                    // Fallback to database if not in JSON
                    val hadith = hadithRepository.getHadith("sahih_bukhari.db", hadithNumber)
                    hadith?.textPlain ?: hadith?.elaboration
                }
                ?: continue

            // Use same format as DrivingAudioService for cache compatibility
            val introText = "Hadith number $hadithNumber from Sahih Al-Bukhari."
            val fullText = "$introText $hadithText"

            // Check if already cached
            if (!sherpaOnnxTts.isCached(fullText)) {
                android.util.Log.i("CourseScreen", "🔄 Pre-generating hadith #$hadithNumber (${fullText.length} chars)")
                sherpaOnnxTts.preGenerateAsync(
                    text = fullText,
                    speakerId = selectedSpeakerId
                )
                // Small delay between generations
                kotlinx.coroutines.delay(500)
            } else {
                android.util.Log.d("CourseScreen", "📦 Hadith #$hadithNumber already cached")
            }
        }

        android.util.Log.i("CourseScreen", "✅ Pre-generation complete for hadiths #$startHadithNumber to #${startHadithNumber + 2}")
    } catch (e: Exception) {
        android.util.Log.e("CourseScreen", "Error pre-generating hadith TTS", e)
    }
}

/**
 * Hilt EntryPoint to get SherpaOnnxTtsService in non-Hilt components
 */
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface HadithTtsEntryPoint {
    fun sherpaOnnxTtsService(): SherpaOnnxTtsService
}
