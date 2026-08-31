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
import android.content.Intent
import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.starception.submission.core.designsystem.component.NiaTopicTag
import com.starception.submission.core.designsystem.component.NiaOutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starception.submission.core.ui.FlaticonIcon
import com.starception.submission.core.ui.FlaticonIcons
import androidx.compose.ui.zIndex
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

/**
 * Lesson data model with memorization and time tracking
 */
data class Lesson(
    val id: String,
    val title: String,
    val subtitle: String,
    val duration: String,
    val type: LessonType,
    val isCompleted: Boolean = false,
    val isLocked: Boolean = false,
    val totalAyahs: Int = 3, // For memorization courses
    val surahNumber: Int = 0, // Surah number for Quran courses
)

enum class LessonType {
    VIDEO,
    READING,
    QUIZ,
    PRACTICE,
    MEMORIZATION,
    LISTENING,
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
 * Memorization progress for a specific lesson/surah
 */
data class MemorizationProgress(
    val lessonId: String,
    val memorizedAyahs: Set<Int> = emptySet(), // Set of ayah numbers that are memorized
    val totalAyahs: Int = 3,
    val timeSpentMinutes: Int = 0, // Total time spent on this lesson
    val lastPracticed: Long = 0L, // Timestamp of last practice
    val practiceCount: Int = 0, // Number of times practiced
)

/**
 * Learning session for time tracking
 */
data class LearningSession(
    val lessonId: String,
    val startTime: Long,
    val endTime: Long,
    val durationMinutes: Int,
)

/**
 * Instructor data model
 */
data class Instructor(
    val name: String,
    val title: String,
    val bio: String,
    val coursesCount: Int,
    val studentsCount: String,
    val rating: Float,
)

/**
 * Review data model
 */
data class Review(
    val id: String,
    val userName: String,
    val rating: Float,
    val comment: String,
    val date: String,
    val helpful: Int,
)

/**
 * Course Detail Screen - Professional Coursera/Udemy style with complete overhaul
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
    val listState = rememberLazyListState()
    var playDailyHadithAfterTravelDua by rememberSaveable(course.id) {
        mutableStateOf(prefs.getBoolean("play_daily_bukhari_after_travel_dua", true))
    }
    var playQuranListeningInDrivingChain by rememberSaveable(course.id) {
        mutableStateOf(prefs.getBoolean("play_quran_listening_in_driving_chain", true))
    }
    var completionConfirmationMandatory by rememberSaveable(course.id) {
        mutableStateOf(CourseProgressTracker.isCompletionConfirmationMandatory(context, course.id))
    }

    // Check if enrolled
    var isEnrolled by remember {
        mutableStateOf(
            (prefs.getStringSet("enrolled_courses", emptySet()) ?: emptySet()).contains(course.id)
        )
    }

    // Track completed lessons - use same key as CourseProgressTracker for consistency
    var completedLessons by remember {
        mutableStateOf(
            prefs.getStringSet("completed_lessons_${course.id}", emptySet()) ?: emptySet()
        )
    }

    // Track memorization progress per lesson (which ayahs are memorized)
    var memorizationProgress: Map<String, MemorizationProgress> by remember {
        mutableStateOf(loadMemorizationProgress(prefs, course.id))
    }

    // Track time spent per lesson (in minutes)
    var timeSpentPerLesson: Map<String, Int> by remember {
        mutableStateOf(loadTimeSpent(prefs, course.id))
    }

    // State for showing completion bottom sheet - use rememberSaveable to survive config changes
    var showCompletionSheet by remember { mutableStateOf<Lesson?>(null) }

    // Store pending lesson info that survives recomposition during predictive back
    var pendingLessonForSheet by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingLessonTitle by rememberSaveable { mutableStateOf<String?>(null) }

    // Track when sheet was shown to distinguish user dismiss from system dismiss
    var sheetShownTimestamp by remember { mutableStateOf(0L) }

    // Check for pending completion on initial composition
    LaunchedEffect(Unit) {
        val pending = CourseProgressTracker.getPendingCompletion(context, course.id)
        Log.d("CourseDetail_TRACE", "🔍 LaunchedEffect - Pending: ${pending?.lessonId ?: "null"}")
        if (pending != null && pendingLessonForSheet == null) {
            pendingLessonForSheet = pending.lessonId
            pendingLessonTitle = pending.lessonTitle
            // Clear from prefs immediately
            CourseProgressTracker.clearPendingCompletion(context, course.id)
            Log.d("CourseDetail_TRACE", "📋 Stored pending: ${pending.lessonId}, cleared from prefs")
        }
    }

    // Retry counter to handle predictive back dismissals
    var showAttempt by rememberSaveable { mutableIntStateOf(0) }

    // Show bottom sheet when we have a pending lesson (with delay for navigation to settle)
    // Re-trigger when showAttempt changes (for retries)
    LaunchedEffect(pendingLessonForSheet, showAttempt) {
        val lessonId = pendingLessonForSheet
        if (lessonId != null && showCompletionSheet == null) {
            Log.d("CourseDetail_TRACE", "⏰ Attempt $showAttempt: Waiting 1.5s for navigation to settle...")
            kotlinx.coroutines.delay(1500) // 1.5 second delay for predictive back
            // Double-check we still need to show (might have been dismissed by user)
            if (pendingLessonForSheet != null && showCompletionSheet == null) {
                Log.d("CourseDetail_TRACE", "🔍 Looking for lesson: $lessonId")
                val modules = generateModulesForCourse(course)
                for (module in modules) {
                    val lesson = module.lessons.find { it.id == lessonId }
                    if (lesson != null) {
                        Log.d("CourseDetail_TRACE", "✅ Found lesson, showing bottom sheet: ${lesson.id}")
                        sheetShownTimestamp = System.currentTimeMillis()
                        showCompletionSheet = lesson
                        break
                    }
                }
            }
            // Note: Don't clear pendingLessonForSheet here - only clear when user confirms/dismisses
        }
    }

    // Total time spent on course
    val totalTimeSpentMinutes: Int = remember(timeSpentPerLesson) {
        timeSpentPerLesson.values.sum()
    }

    // Last accessed timestamp
    val lastAccessed = remember {
        prefs.getLong("last_accessed_${course.id}", 0L)
    }

    // Update last accessed on screen open
    remember {
        prefs.edit().putLong("last_accessed_${course.id}", System.currentTimeMillis()).apply()
        true
    }

    // Generate modules based on course type
    val modules = remember(course) { generateModulesForCourse(course) }

    // Calculate overall progress
    val totalLessons = modules.sumOf { it.lessons.size }
    val completedCount = completedLessons.size
    val progressPercent = if (totalLessons > 0) (completedCount * 100f / totalLessons) else 0f

    // Calculate estimated time remaining
    val estimatedMinutesRemaining = remember(completedCount, totalLessons) {
        val remainingLessons = totalLessons - completedCount
        remainingLessons * 5 // Average 5 minutes per lesson
    }

    // Instructor and reviews data
    val instructor = remember { getInstructorForCourse(course) }
    val reviews = remember { getReviewsForCourse(course) }
    val learningOutcomes = remember { getLearningOutcomes(course) }

    // Collapsing toolbar state
    val density = LocalDensity.current
    val headerHeight = 280.dp
    val headerHeightPx = with(density) { headerHeight.toPx() }
    val toolbarHeight = 64.dp
    val toolbarHeightPx = with(density) { toolbarHeight.toPx() }

    val scrollOffset by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex * 1000 + listState.firstVisibleItemScrollOffset
        }
    }

    val collapseProgress by remember {
        derivedStateOf {
            (scrollOffset / (headerHeightPx - toolbarHeightPx)).coerceIn(0f, 1f)
        }
    }

    val toolbarAlpha by animateFloatAsState(
        targetValue = collapseProgress,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "toolbarAlpha"
    )

    // Stable item positions power the in-page navigator. These are derived from
    // the same conditions used by the LazyColumn, so navigation stays correct for
    // every course type and enrollment state.
    var nextSectionIndex = 3 // hero, stats, section navigator
    if (isEnrolled) nextSectionIndex++
    if (isEnrolled && progressPercent > 0) nextSectionIndex++
    if (isEnrolled && (course.id == "memorize_3_ayahs" || course.id == "juz_amma")) {
        nextSectionIndex++
    }
    if (isEnrolled && totalTimeSpentMinutes > 0) nextSectionIndex++
    val outcomesSectionIndex = nextSectionIndex++
    if (course.id == "daily_bukhari") nextSectionIndex++
    if (course.id == "complete_quran_listening") nextSectionIndex++
    if (course.id == "daily_bukhari" || course.id == "complete_quran_listening") {
        nextSectionIndex++
    }
    val lessonsSectionIndex = nextSectionIndex
    val selectedCourseSection by remember(outcomesSectionIndex, lessonsSectionIndex) {
        derivedStateOf {
            when {
                listState.firstVisibleItemIndex >= lessonsSectionIndex -> 2
                listState.firstVisibleItemIndex >= outcomesSectionIndex -> 1
                else -> 0
            }
        }
    }
    val courseNavigationScope = rememberCoroutineScope()
    val courseNavigationHaptics = LocalHapticFeedback.current

    Box(modifier = modifier.fillMaxSize()) {
        // Main Content
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = if (isEnrolled) 100.dp else 120.dp),
        ) {
            // Hero Section with course title and info
            item {
                CourseHeroSection(
                    course = course,
                    progressPercent = progressPercent,
                    completedCount = completedCount,
                    totalLessons = totalLessons,
                    isEnrolled = isEnrolled,
                    collapseProgress = collapseProgress,
                    estimatedMinutesRemaining = estimatedMinutesRemaining,
                    lastAccessed = lastAccessed,
                )
            }

            // Quick Stats Pills
            item {
                QuickStatsPills(
                    course = course,
                    completedCount = completedCount,
                    totalLessons = totalLessons,
                )
            }

            item(key = "course_section_navigator") {
                CourseSectionNavigator(
                    selectedIndex = selectedCourseSection,
                    onSectionSelected = { sectionIndex ->
                        courseNavigationHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val targetIndex = when (sectionIndex) {
                            1 -> outcomesSectionIndex
                            2 -> lessonsSectionIndex
                            else -> 0
                        }
                        courseNavigationScope.launch {
                            listState.animateScrollToItem(targetIndex)
                        }
                    },
                )
            }

            // Put the next lesson directly after the overview so returning learners
            // can resume without scrolling through analytics first.
            if (isEnrolled) {
                item {
                    ContinueLearningCard(
                        modules = modules,
                        completedLessons = completedLessons,
                        onLessonClick = onLessonClick,
                        course = course,
                    )
                }
            }

            // Certificate Progress (if enrolled and has progress)
            if (isEnrolled && progressPercent > 0) {
                item {
                    CertificateProgressCard(
                        progressPercent = progressPercent,
                        course = course,
                    )
                }
            }

            // Memorization & Time Progress Section (for memorization courses)
            if (isEnrolled && (course.id == "memorize_3_ayahs" || course.id == "juz_amma")) {
                item {
                    MemorizationProgressSection(
                        modules = modules,
                        memorizationProgress = memorizationProgress,
                        timeSpentPerLesson = timeSpentPerLesson,
                        totalTimeSpentMinutes = totalTimeSpentMinutes,
                        accentColor = MaterialTheme.colorScheme.primary,
                        onAyahToggle = { lessonId, ayahNumber ->
                            val currentProgress = memorizationProgress[lessonId] ?: MemorizationProgress(
                                lessonId = lessonId,
                                totalAyahs = 3
                            )
                            val newAyahs = currentProgress.memorizedAyahs.toMutableSet()
                            if (ayahNumber in newAyahs) {
                                newAyahs.remove(ayahNumber)
                            } else {
                                newAyahs.add(ayahNumber)
                            }
                            val updatedProgress = currentProgress.copy(
                                memorizedAyahs = newAyahs,
                                lastPracticed = System.currentTimeMillis()
                            )
                            memorizationProgress = memorizationProgress.toMutableMap().apply {
                                put(lessonId, updatedProgress)
                            }
                            saveMemorizationProgress(prefs, course.id, memorizationProgress)

                            // Auto-mark lesson complete if all ayahs memorized
                            if (newAyahs.size >= currentProgress.totalAyahs) {
                                if (lessonId !in completedLessons) {
                                    val newSet = completedLessons.toMutableSet()
                                    newSet.add(lessonId)
                                    completedLessons = newSet
                                    prefs.edit().putStringSet("completed_lessons_${course.id}", newSet).apply()
                                    prefs.edit().putInt("progress_${course.id}", newSet.size).apply()
                                }
                            }
                        },
                    )
                }
            }

            // Time Spent Card (for all courses when enrolled)
            if (isEnrolled && totalTimeSpentMinutes > 0) {
                item {
                    TimeSpentCard(
                        totalMinutes = totalTimeSpentMinutes,
                        lessonsWithTime = timeSpentPerLesson.count { it.value > 0 },
                        totalLessons = totalLessons,
                        accentColor = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // What You'll Learn Section
            item {
                WhatYouWillLearnSection(
                    outcomes = learningOutcomes,
                    accentColor = MaterialTheme.colorScheme.primary,
                )
            }

            if (course.id == "daily_bukhari") {
                item {
                    DrivingChainPlaybackCard(
                        title = "Driving audio playback",
                        description = "When Daily Hadith is enrolled, the next Sahih Bukhari hadith can play automatically after travel dua in the driving audio chain.",
                        enabled = playDailyHadithAfterTravelDua,
                        onEnabledChange = { enabled ->
                            playDailyHadithAfterTravelDua = enabled
                            prefs.edit().putBoolean("play_daily_bukhari_after_travel_dua", enabled).apply()
                        },
                        enabledSummary = "On: travel dua → Daily Hadith → Quran (if enrolled).",
                        disabledSummary = "Off: travel dua skips Daily Hadith during driving. You can still open the course and read hadith manually.",
                    )
                }
            }

            if (course.id == "complete_quran_listening") {
                item {
                    DrivingChainPlaybackCard(
                        title = "Driving audio playback",
                        description = "When Complete Quran Listening is enrolled, Quran playback can continue in the driving audio chain after travel dua and Daily Hadith when those are enabled.",
                        enabled = playQuranListeningInDrivingChain,
                        onEnabledChange = { enabled ->
                            playQuranListeningInDrivingChain = enabled
                            prefs.edit().putBoolean("play_quran_listening_in_driving_chain", enabled).apply()
                        },
                        enabledSummary = "On: travel dua → Daily Hadith (if enabled) → Quran Listening.",
                        disabledSummary = "Off: the driving audio chain ends after travel dua or Daily Hadith. You can still listen from the course manually.",
                    )
                }
            }

            if (course.id == "daily_bukhari" || course.id == "complete_quran_listening") {
                item {
                    CompletionConfirmationCard(
                        mandatory = completionConfirmationMandatory,
                        onMandatoryChange = { mandatory ->
                            completionConfirmationMandatory = mandatory
                            CourseProgressTracker.setCompletionConfirmationMandatory(context, course.id, mandatory)
                        },
                        retryAttempts = CourseProgressTracker.getCompletionConfirmationRetryAttempts(),
                        totalAttempts = CourseProgressTracker.getCompletionConfirmationTotalAttempts(),
                    )
                }
            }

            // Syllabus Header
            item {
                SyllabusHeader(
                    totalModules = modules.size,
                    totalLessons = totalLessons,
                    estimatedHours = (totalLessons * 5) / 60,
                )
            }

            // Module List
            modules.forEachIndexed { moduleIndex, module ->
                item(key = module.id) {
                    EnhancedModuleCard(
                        module = module,
                        moduleIndex = moduleIndex,
                        completedLessons = completedLessons,
                        memorizationProgress = memorizationProgress,
                        timeSpentPerLesson = timeSpentPerLesson,
                        accentColor = MaterialTheme.colorScheme.primary,
                        isEnrolled = isEnrolled,
                        isMemorizationCourse = course.id == "memorize_3_ayahs" || course.id == "juz_amma",
                        onLessonClick = { lesson ->
                            val lessonIndex = module.lessons.indexOf(lesson)
                            onLessonClick(lesson, lessonIndex)
                        },
                        onMarkComplete = { lesson ->
                            if (lesson.id in completedLessons) {
                                // Allow unchecking without confirmation
                                val newSet = completedLessons.toMutableSet()
                                newSet.remove(lesson.id)
                                completedLessons = newSet
                                prefs.edit().putStringSet("completed_lessons_${course.id}", newSet).apply()
                                prefs.edit().putInt("progress_${course.id}", newSet.size).apply()
                            } else {
                                // Show completion confirmation bottom sheet
                                showCompletionSheet = lesson
                            }
                            onMarkComplete(lesson)
                        },
                        onAyahToggle = { lessonId, ayahNumber ->
                            val lesson = module.lessons.find { it.id == lessonId }
                            val totalAyahs = lesson?.totalAyahs ?: 3
                            val currentProgress = memorizationProgress[lessonId] ?: MemorizationProgress(
                                lessonId = lessonId,
                                totalAyahs = totalAyahs
                            )
                            val newAyahs = currentProgress.memorizedAyahs.toMutableSet()
                            if (ayahNumber in newAyahs) {
                                newAyahs.remove(ayahNumber)
                            } else {
                                newAyahs.add(ayahNumber)
                            }
                            val updatedProgress = currentProgress.copy(
                                memorizedAyahs = newAyahs,
                                lastPracticed = System.currentTimeMillis()
                            )
                            memorizationProgress = memorizationProgress.toMutableMap().apply {
                                put(lessonId, updatedProgress)
                            }
                            saveMemorizationProgress(prefs, course.id, memorizationProgress)

                            // Auto-mark lesson complete if all ayahs memorized
                            if (newAyahs.size >= totalAyahs) {
                                if (lessonId !in completedLessons) {
                                    val newSet = completedLessons.toMutableSet()
                                    newSet.add(lessonId)
                                    completedLessons = newSet
                                    prefs.edit().putStringSet("completed_lessons_${course.id}", newSet).apply()
                                    prefs.edit().putInt("progress_${course.id}", newSet.size).apply()
                                }
                            }
                        },
                        onTimeSpent = { lessonId, minutes ->
                            val current = timeSpentPerLesson[lessonId] ?: 0
                            timeSpentPerLesson = timeSpentPerLesson.toMutableMap().apply {
                                put(lessonId, current + minutes)
                            }
                            saveTimeSpent(prefs, course.id, timeSpentPerLesson)
                        },
                    )
                }
            }

            // Reviews Section
            item {
                ReviewsSection(
                    reviews = reviews,
                    averageRating = 4.8f,
                    totalReviews = reviews.size,
                    accentColor = MaterialTheme.colorScheme.primary,
                )
            }

            // Instructor Section (at the end)
            item {
                InstructorSection(
                    instructor = instructor,
                    accentColor = MaterialTheme.colorScheme.primary,
                )
            }

            // Bottom spacing
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

            // Bottom Action Bar
            EnhancedBottomBar(
                course = course,
                isEnrolled = isEnrolled,
                progressPercent = progressPercent,
                onEnroll = {
                    val enrolledSet = (prefs.getStringSet("enrolled_courses", emptySet()) ?: emptySet()).toMutableSet()
                    enrolledSet.add(course.id)
                    prefs.edit().putStringSet("enrolled_courses", enrolledSet).apply()
                    isEnrolled = true
                    onEnroll()
                },
                onContinue = {
                    // Find next incomplete lesson and navigate
                    for (module in modules) {
                        for ((index, lesson) in module.lessons.withIndex()) {
                            if (lesson.id !in completedLessons) {
                                onLessonClick(lesson, index)
                                return@EnhancedBottomBar
                            }
                        }
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )

        // Transparent collapsing toolbar - overlays content like SurahDetailScreen
        CourseDetailTopBar(
            collapseProgress = collapseProgress,
            courseTitle = course.title,
            courseSubtitle = course.subtitle,
            onBackClick = onBackClick,
            onShareClick = {
                val courseLink = "starception://course/${course.id}"
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, course.title)
                    putExtra(Intent.EXTRA_TEXT, "Check out this course: ${course.title}\n\n${course.description}\n\nOpen in app: $courseLink")
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Course"))
            },
            onBookmarkClick = { /* Bookmark */ },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        run {
            val statusBarInsets = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val statusBarPadding = if (statusBarInsets > 0.dp) statusBarInsets else 8.dp
            val headerYPx = with(density) { (statusBarPadding + 64.dp).toPx() }
            val toolbarYPx = with(density) { 24.dp.toPx() }
            val startXPx = with(density) { 20.dp.toPx() }
            val endXPx = with(density) { 56.dp.toPx() }

            val floatingState by remember {
                derivedStateOf {
                    val scrollOff = if (listState.firstVisibleItemIndex == 0) {
                        listState.firstVisibleItemScrollOffset.toFloat()
                    } else {
                        headerYPx
                    }
                    val titleY = (headerYPx - scrollOff).coerceAtLeast(toolbarYPx)
                    val progress = ((headerYPx - titleY) / (headerYPx - toolbarYPx)).coerceIn(0f, 1f)
                    Triple(titleY, progress, startXPx + (progress * (endXPx - startXPx)))
                }
            }

            val (titleYPx, progress, xOffsetPx) = floatingState
            val scale = 1f - (progress * 0.4f)
            val subtitleAlpha = (1f - (progress / 0.7f)).coerceIn(0f, 1f)
            val compactWidth = 264.dp - (56.dp * progress)
            val titleSpacing = 8.dp * (1f - progress)
            val contentColor = MaterialTheme.colorScheme.onSurface

            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = xOffsetPx
                        translationY = titleYPx
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                    }
                    .width(compactWidth)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(titleSpacing),
                ) {
                    Text(
                        text = course.title,
                        style = MaterialTheme.typography.headlineMedium.copy(lineHeight = 34.sp),
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        maxLines = if (progress > 0.5f) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (course.subtitle.isNotBlank() && subtitleAlpha > 0f) {
                        Text(
                            text = course.subtitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = contentColor.copy(alpha = 0.72f * subtitleAlpha),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }

    // Lesson completion bottom sheet
    showCompletionSheet?.let { lesson ->
        Log.d("CourseDetail_TRACE", "🎯 Rendering LessonCompletionBottomSheet for: ${lesson.id} - ${lesson.title}")
        LessonCompletionBottomSheet(
            lessonTitle = lesson.title,
            courseId = course.id,
            lessonId = lesson.id,
            onComplete = { hasRecording ->
                Log.d("CourseDetail_TRACE", "✅ onComplete called for: ${lesson.id}, hasRecording: $hasRecording")
                // Mark lesson as completed
                CourseProgressTracker.markLessonCompleted(context, course.id, lesson.id)
                CourseProgressTracker.clearPendingCompletion(context, course.id)

                // Update local state
                val newSet = completedLessons.toMutableSet()
                newSet.add(lesson.id)
                completedLessons = newSet
                prefs.edit().putStringSet("completed_lessons_${course.id}", newSet).apply()
                prefs.edit().putInt("progress_${course.id}", newSet.size).apply()

                // Clear all pending state
                pendingLessonForSheet = null
                pendingLessonTitle = null
                showCompletionSheet = null
            },
            onDismiss = {
                Log.d("CourseDetail_TRACE", "❌ onDismiss called for: ${lesson.id}")
                // Clear all pending state when user dismisses
                CourseProgressTracker.clearPendingCompletion(context, course.id)
                pendingLessonForSheet = null
                pendingLessonTitle = null
                showCompletionSheet = null
            },
        )
    }
}

/**
 * Transparent collapsing toolbar for course detail screen.
 * Similar to AlbumPlayerTopBar in SurahDetailScreen.
 */
@Composable
private fun CourseDetailTopBar(
    collapseProgress: Float,
    courseTitle: String,
    courseSubtitle: String,
    onBackClick: () -> Unit,
    onShareClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = collapseProgress)
    val contentColor = MaterialTheme.colorScheme.onSurface
    val actionContainerColor = MaterialTheme.colorScheme.surface.copy(
        alpha = 0.76f + (collapseProgress * 0.24f),
    )

    Surface(
        color = backgroundColor,
        tonalElevation = (4 * collapseProgress).dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = actionContainerColor,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                ),
            ) {
                IconButton(onClick = onBackClick) {
                    FlaticonIcon(
                        glyph = FlaticonIcons.ARROW_BACK,
                        contentDescription = "Back",
                        tint = contentColor,
                        fontSize = 20.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = actionContainerColor,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                ),
            ) {
                IconButton(onClick = onShareClick) {
                    FlaticonIcon(
                        glyph = FlaticonIcons.SHARE,
                        contentDescription = "Share",
                        tint = contentColor,
                        fontSize = 19.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = actionContainerColor,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                ),
            ) {
                IconButton(onClick = onBookmarkClick) {
                    FlaticonIcon(
                        glyph = FlaticonIcons.BOOKMARK,
                        contentDescription = "Bookmark",
                        tint = contentColor,
                        fontSize = 19.sp,
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
    collapseProgress: Float,
    estimatedMinutesRemaining: Int,
    lastAccessed: Long,
) {
    val parallaxOffset = collapseProgress * 100
    val statusBarInsets = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // Use minimum padding when status bar is hidden to clear toolbar (8dp toolbar top + 56dp toolbar height)
    val statusBarHeight = if (statusBarInsets > 0.dp) statusBarInsets else 8.dp

    // Dynamic content based on course
    val (highlightText, highlightSubtext) = remember(course.id) {
        when (course.id) {
            "memorize_3_ayahs" -> Pair("114", "Surahs to Master")
            "daily_bukhari" -> Pair("365", "Days Journey")
            "juz_amma" -> Pair("37", "Surahs • Juz 30")
            "quran_reading" -> Pair("604", "Pages of Quran")
            else -> Pair("${course.totalLessons}", "Lessons")
        }
    }

    val courseTagline = remember(course.id) {
        when (course.id) {
            "memorize_3_ayahs" -> "Start with the opening verses"
            "daily_bukhari" -> "One hadith, one day at a time"
            "juz_amma" -> "The most recited Juz"
            "quran_reading" -> "Complete the Holy Quran"
            "complete_quran_listening" -> "Listen during your commute"
            else -> course.subtitle
        }
    }

    // Use category-based colors like in course cards
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = parallaxOffset
                alpha = 1f - (collapseProgress * 0.3f)
            }
            .background(containerColor),
    ) {
        // Decorative elements
        Box(
            modifier = Modifier
                .size(180.dp)
                .offset(x = 260.dp, y = (-30).dp)
                .background(
                    color = accentColor.copy(alpha = 0.1f),
                    shape = CircleShape,
                )
        )

        // Content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = statusBarHeight + 56.dp,
                    bottom = 24.dp
                ),
        ) {
            // Main content row: Info + Dynamic Highlight
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    val isTitleLong = course.title.length > 18
                    val hasSubtitle = course.subtitle.isNotBlank()
                    val titleSpacerHeight = when {
                        isTitleLong && hasSubtitle -> 164.dp
                        isTitleLong && !hasSubtitle -> 136.dp
                        !isTitleLong && hasSubtitle -> 120.dp
                        else -> 92.dp
                    }
                    Spacer(modifier = Modifier.height(titleSpacerHeight))

                    Text(
                        text = courseTagline,
                        style = MaterialTheme.typography.bodyLarge,
                        color = accentColor,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = course.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp,
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Compact course identity panel: a non-figurative Flaticon glyph
                // plus the most useful course-sized metric.
                Surface(
                    modifier = Modifier
                        .width(104.dp)
                        .height(124.dp),
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.94f),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Surface(
                            modifier = Modifier.size(32.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = accentColor.copy(alpha = 0.12f),
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                FlaticonIcon(
                                    glyph = course.iconGlyph,
                                    contentDescription = null,
                                    tint = accentColor,
                                    fontSize = 17.sp,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = highlightText,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                        )
                        Text(
                            text = highlightSubtext,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            lineHeight = 14.sp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Category badges row - NiaTopicTag style
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Category tag
                NiaTopicTag(
                    followed = true,
                    onClick = { },
                    enabled = false,
                ) {
                    Text(course.category.label)
                }

                // Difficulty tag
                NiaTopicTag(
                    followed = false,
                    onClick = { },
                    enabled = false,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        FlaticonIcon(
                            glyph = FlaticonIcons.DIFFICULTY,
                            contentDescription = null,
                            fontSize = 14.sp,
                        )
                        Text(course.difficulty.label)
                    }
                }

                // Rating tag
                NiaTopicTag(
                    followed = false,
                    onClick = { },
                    enabled = false,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        FlaticonIcon(
                            glyph = FlaticonIcons.STAR,
                            contentDescription = null,
                            tint = Color(0xFFFFB800),
                            fontSize = 14.sp,
                        )
                        Text("4.8")
                    }
                }
            }

            // Progress and last activity - unified card
            if (isEnrolled) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = accentColor.copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Progress section
                        FlaticonIcon(
                            glyph = FlaticonIcons.COMPLETED,
                            contentDescription = null,
                            tint = accentColor,
                            fontSize = 18.sp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "$completedCount of $totalLessons",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "lessons completed",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Divider
                        if (lastAccessed > 0) {
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(32.dp)
                                    .background(accentColor.copy(alpha = 0.3f))
                            )
                            Spacer(modifier = Modifier.width(16.dp))

                            // Last activity section
                            FlaticonIcon(
                                glyph = FlaticonIcons.SCHEDULE,
                                contentDescription = null,
                                tint = accentColor,
                                fontSize = 18.sp,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = formatLastAccessed(lastAccessed),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "last activity",
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
}

@Composable
private fun CompletionConfirmationCard(
    mandatory: Boolean,
    onMandatoryChange: (Boolean) -> Unit,
    retryAttempts: Int,
    totalAttempts: Int,
) {
    DrivingChainPlaybackCard(
        title = "Completion confirmation",
        description = "Choose whether voice confirmation is required before the course marks a lesson complete after playback.",
        enabled = mandatory,
        onEnabledChange = onMandatoryChange,
        enabledSummary = "Mandatory: asks for YES/NO and retries up to $retryAttempts times ($totalAttempts attempts total) before leaving completion pending.",
        disabledSummary = "Optional: if playback finishes, the lesson is marked complete automatically without waiting for voice confirmation.",
    )
}

@Composable
private fun DrivingChainPlaybackCard(
    title: String,
    description: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    enabledSummary: String,
    disabledSummary: String,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            Text(
                text = if (enabled) enabledSummary else disabledSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CourseSectionNavigator(
    selectedIndex: Int,
    onSectionSelected: (Int) -> Unit,
) {
    val sections = listOf(
        FlaticonIcons.INFO to "Overview",
        FlaticonIcons.CHECK to "Outcomes",
        FlaticonIcons.BOOK to "Lessons",
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            sections.forEachIndexed { index, (glyph, label) ->
                val selected = selectedIndex == index
                val containerColor by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Transparent
                    },
                    animationSpec = tween(durationMillis = 180),
                    label = "courseSectionContainer",
                )
                val contentColor by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = tween(durationMillis = 180),
                    label = "courseSectionContent",
                )

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .semantics {
                            contentDescription = "$label section"
                            this.selected = selected
                        }
                        .clickable { onSectionSelected(index) },
                    shape = RoundedCornerShape(14.dp),
                    color = containerColor,
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FlaticonIcon(
                            glyph = glyph,
                            contentDescription = null,
                            tint = contentColor,
                            fontSize = 16.sp,
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = contentColor,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickStatsPills(
    course: Course,
    completedCount: Int,
    totalLessons: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatPill(
            iconGlyph = FlaticonIcons.BOOK,
            value = "$totalLessons",
            label = "Lessons",
            accentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        StatPill(
            iconGlyph = FlaticonIcons.SCHEDULE,
            value = "${course.estimatedDays}",
            label = "Days",
            accentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        StatPill(
            iconGlyph = FlaticonIcons.COMPLETED,
            value = "$completedCount",
            label = "Done",
            accentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatPill(
    iconGlyph: String,
    value: String,
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    // Material 3 Expressive StatPill with larger corners and layered styling
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = accentColor.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            accentColor.copy(alpha = 0.15f),
        ),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Icon with expressive container
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(12.dp),
                color = accentColor.copy(alpha = 0.15f),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    FlaticonIcon(
                        glyph = iconGlyph,
                        contentDescription = null,
                        tint = accentColor,
                        fontSize = 20.sp,
                    )
                }
            }
            Column {
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
}

@Composable
private fun CertificateProgressCard(
    progressPercent: Float,
    course: Course,
) {
    // Material 3 Expressive Card with BorderStroke
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
        ),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Certificate icon with progress ring
            val progressPrimaryColor = MaterialTheme.colorScheme.primary
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(56.dp),
            ) {
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Background circle
                    drawArc(
                        color = progressPrimaryColor.copy(alpha = 0.2f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 6.dp.toPx(),
                            cap = StrokeCap.Round,
                        ),
                    )
                    // Progress arc
                    drawArc(
                        color = progressPrimaryColor,
                        startAngle = -90f,
                        sweepAngle = 360f * (progressPercent / 100f),
                        useCenter = false,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 6.dp.toPx(),
                            cap = StrokeCap.Round,
                        ),
                    )
                }
                FlaticonIcon(
                    glyph = FlaticonIcons.MEDAL,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                    fontSize = 24.sp,
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Certificate Progress",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${progressPercent.toInt()}% complete • ${(100 - progressPercent).toInt()}% to go",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (progressPercent >= 100) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            FlaticonIcon(
                                glyph = FlaticonIcons.TROPHY,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(14.dp),
                                fontSize = 14.sp,
                            )
                            Text(
                                text = "Certificate Earned!",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                }
            }

            if (progressPercent >= 100) {
                NiaOutlinedButton(
                    onClick = { /* Download certificate */ },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "View",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ContinueLearningCard(
    modules: List<CourseModule>,
    completedLessons: Set<String>,
    onLessonClick: (Lesson, Int) -> Unit,
    course: Course,
) {
    // Find next incomplete lesson
    var nextLesson: Lesson? = null
    var nextLessonIndex = 0
    var moduleName = ""
    outer@ for (module in modules) {
        for ((index, lesson) in module.lessons.withIndex()) {
            if (lesson.id !in completedLessons) {
                nextLesson = lesson
                nextLessonIndex = index
                moduleName = module.title
                break@outer
            }
        }
    }

    if (nextLesson != null) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clickable { onLessonClick(nextLesson, nextLessonIndex) },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            ),
            tonalElevation = 1.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        FlaticonIcon(
                            glyph = course.iconGlyph,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(26.dp),
                            fontSize = 24.sp,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Continue Learning",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = nextLesson.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = moduleName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        Text(
                            text = nextLesson.duration,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        FlaticonIcon(
                            glyph = FlaticonIcons.ANGLE_RIGHT,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp),
                            fontSize = 16.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WhatYouWillLearnSection(
    outcomes: List<String>,
    accentColor: Color,
) {
    // Material 3 Expressive Section Card
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
        ),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Icon with expressive container
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = accentColor.copy(alpha = 0.15f),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        FlaticonIcon(
                            glyph = FlaticonIcons.BOOK,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp),
                            fontSize = 20.sp,
                        )
                    }
                }
                Text(
                    text = "What You'll Learn",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            outcomes.forEach { outcome ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FlaticonIcon(
                        glyph = FlaticonIcons.COMPLETED,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp),
                        fontSize = 20.sp,
                    )
                    Text(
                        text = outcome,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun InstructorSection(
    instructor: Instructor,
    accentColor: Color,
) {
    // Material 3 Expressive Instructor Section
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
        ),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Icon with expressive container
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = accentColor.copy(alpha = 0.15f),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        FlaticonIcon(
                            glyph = FlaticonIcons.BOOK,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp),
                            fontSize = 20.sp,
                        )
                    }
                }
                Text(
                    text = "Instructor",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.Top,
            ) {
                // Instructor avatar with expressive styling
                Surface(
                    modifier = Modifier.size(68.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Transparent,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(accentColor, accentColor.copy(alpha = 0.7f))
                                ),
                                shape = RoundedCornerShape(20.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = instructor.name.split(" ").map { it.first() }.take(2).joinToString(""),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = instructor.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        FlaticonIcon(
                            glyph = FlaticonIcons.VERIFIED,
                            contentDescription = "Verified",
                            tint = accentColor,
                            modifier = Modifier.size(16.dp),
                            fontSize = 16.sp,
                        )
                    }

                    Text(
                        text = instructor.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = instructor.bio,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Stats row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Column {
                            Text(
                                text = "${instructor.coursesCount}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Courses",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column {
                            Text(
                                text = instructor.studentsCount,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Students",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                FlaticonIcon(
                                    glyph = FlaticonIcons.STAR,
                                    contentDescription = null,
                                    tint = Color(0xFFFFB800),
                                    modifier = Modifier.size(14.dp),
                                    fontSize = 14.sp,
                                )
                                Text(
                                    text = "${instructor.rating}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Text(
                                text = "Rating",
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
private fun SyllabusHeader(
    totalModules: Int,
    totalLessons: Int,
    estimatedHours: Int,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FlaticonIcon(
                    glyph = FlaticonIcons.BOOK,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(16.dp),
                    fontSize = 16.sp,
                )
                Text(
                    text = "Learning path",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Course Syllabus",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "$totalModules modules • $totalLessons lessons • ${estimatedHours}h estimated",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EnhancedModuleCard(
    module: CourseModule,
    moduleIndex: Int,
    completedLessons: Set<String>,
    memorizationProgress: Map<String, MemorizationProgress>,
    timeSpentPerLesson: Map<String, Int>,
    accentColor: Color,
    isEnrolled: Boolean,
    isMemorizationCourse: Boolean = false,
    onLessonClick: (Lesson) -> Unit,
    onMarkComplete: (Lesson) -> Unit,
    onAyahToggle: (String, Int) -> Unit = { _, _ -> },
    onTimeSpent: (String, Int) -> Unit = { _, _ -> },
) {
    var isExpanded by remember { mutableStateOf(moduleIndex == 0) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "rotation"
    )

    val completedInModule = module.lessons.count { it.id in completedLessons }
    val totalInModule = module.lessons.size
    val moduleProgress = if (totalInModule > 0) completedInModule.toFloat() / totalInModule else 0f
    val isModuleComplete = completedInModule == totalInModule

    val borderColor by animateColorAsState(
        targetValue = when {
            isModuleComplete -> accentColor.copy(alpha = 0.42f)
            isExpanded -> accentColor.copy(alpha = 0.22f)
            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        },
        label = "borderColor"
    )
    val cardColor by animateColorAsState(
        targetValue = when {
            isModuleComplete -> accentColor.copy(alpha = 0.08f)
            isExpanded -> MaterialTheme.colorScheme.surfaceContainerLow
            else -> MaterialTheme.colorScheme.surfaceContainerLowest
        },
        label = "cardColor"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .animateContentSize(
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            ),
        shape = RoundedCornerShape(28.dp),
        color = cardColor,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = borderColor,
        ),
        tonalElevation = if (isExpanded) 3.dp else 1.dp,
        shadowElevation = 0.dp,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Module Number/Check with progress ring
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(44.dp),
                ) {
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        drawArc(
                            color = accentColor.copy(alpha = 0.18f),
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 2.5.dp.toPx(),
                                cap = StrokeCap.Round,
                            ),
                        )
                        drawArc(
                            color = accentColor,
                            startAngle = -90f,
                            sweepAngle = 360f * moduleProgress,
                            useCenter = false,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 2.5.dp.toPx(),
                                cap = StrokeCap.Round,
                            ),
                        )
                    }

                    Surface(
                        modifier = Modifier.size(30.dp),
                        shape = CircleShape,
                        color = if (isModuleComplete) accentColor else MaterialTheme.colorScheme.surface,
                        border = if (isModuleComplete) null else androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        ),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isModuleComplete) {
                                FlaticonIcon(
                                    glyph = FlaticonIcons.COMPLETED,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                    fontSize = 18.sp,
                                )
                            } else {
                                Text(
                                    text = "${moduleIndex + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = module.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$completedInModule/$totalInModule lessons completed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = if (isExpanded) 2.dp else 0.dp,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        FlaticonIcon(
                            glyph = FlaticonIcons.ANGLE_DOWN,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            modifier = Modifier
                                .rotate(rotationAngle)
                                .size(22.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(animationSpec = tween(300)) + fadeIn(),
                exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(),
            ) {
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    shadowElevation = 0.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp),
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        module.lessons.forEachIndexed { index, lesson ->
                            val isCompleted = lesson.id in completedLessons
                            val lessonMemProgress = memorizationProgress[lesson.id]
                            val lessonTimeSpent = timeSpentPerLesson[lesson.id] ?: 0
                            EnhancedLessonItem(
                                lesson = lesson.copy(isCompleted = isCompleted),
                                lessonIndex = index,
                                accentColor = accentColor,
                                isEnrolled = isEnrolled,
                                isMemorizationCourse = isMemorizationCourse,
                                memorizedAyahs = lessonMemProgress?.memorizedAyahs ?: emptySet(),
                                timeSpentMinutes = lessonTimeSpent,
                                onClick = { onLessonClick(lesson) },
                                onMarkComplete = { onMarkComplete(lesson) },
                                onAyahToggle = { ayahNumber -> onAyahToggle(lesson.id, ayahNumber) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EnhancedLessonItem(
    lesson: Lesson,
    lessonIndex: Int,
    accentColor: Color,
    isEnrolled: Boolean,
    isMemorizationCourse: Boolean = false,
    memorizedAyahs: Set<Int> = emptySet(),
    timeSpentMinutes: Int = 0,
    onClick: () -> Unit,
    onMarkComplete: () -> Unit,
    onAyahToggle: (Int) -> Unit = {},
) {
    var showAyahSelector by remember { mutableStateOf(false) }

    val backgroundColor by animateColorAsState(
        targetValue = if (lesson.isCompleted) accentColor.copy(alpha = 0.05f)
        else Color.Transparent,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "bgColor"
    )

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .clickable(enabled = !lesson.isLocked && isEnrolled, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Completion Checkbox
            if (isEnrolled) {
                IconButton(
                    onClick = onMarkComplete,
                    modifier = Modifier.size(36.dp),
                    enabled = !lesson.isLocked,
                ) {
                    FlaticonIcon(
                        glyph = if (lesson.isCompleted) {
                            FlaticonIcons.COMPLETED
                        } else {
                            FlaticonIcons.INCOMPLETE
                        },
                        contentDescription = if (lesson.isCompleted) "Completed" else "Mark complete",
                        tint = if (lesson.isCompleted) {
                            accentColor
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        },
                        fontSize = 24.sp,
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(8.dp))
            }

            Spacer(modifier = Modifier.width(4.dp))

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

                // Show subtitle and duration for all lessons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = lesson.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (lesson.isLocked) 0.4f else 0.8f
                        ),
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                    Text(
                        text = lesson.duration,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (lesson.isLocked) 0.4f else 0.8f
                        ),
                    )
                }

                // Show time spent if any
                if (timeSpentMinutes > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        FlaticonIcon(
                            glyph = FlaticonIcons.SCHEDULE,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(12.dp),
                            fontSize = 12.sp,
                        )
                        Text(
                            text = formatTimeSpent(timeSpentMinutes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                }
            }

            // Lock or Play Icon
            if (lesson.isLocked) {
                FlaticonIcon(
                    glyph = FlaticonIcons.LOCK,
                    contentDescription = "Locked",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    modifier = Modifier.size(18.dp),
                    fontSize = 18.sp,
                )
            } else if (isEnrolled && !lesson.isCompleted) {
                FlaticonIcon(
                    glyph = FlaticonIcons.ANGLE_RIGHT,
                    contentDescription = "Play",
                    tint = accentColor,
                    modifier = Modifier.size(20.dp),
                    fontSize = 20.sp,
                )
            }
        }
    }
}

@Composable
private fun ReviewsSection(
    reviews: List<Review>,
    averageRating: Float,
    totalReviews: Int,
    accentColor: Color,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Learner Reviews",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            TextButton(onClick = { /* View all */ }) {
                Text(
                    text = "See All",
                    color = accentColor,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Rating summary card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Large rating number
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "$averageRating",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row {
                        repeat(5) { index ->
                            FlaticonIcon(
                                glyph = FlaticonIcons.STAR,
                                contentDescription = null,
                                tint = if (index < averageRating.toInt()) Color(0xFFFFB800)
                                else Color(0xFFFFB800).copy(alpha = 0.3f),
                                modifier = Modifier.size(16.dp),
                                fontSize = 16.sp,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$totalReviews reviews",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.width(24.dp))

                // Rating bars
                Column(modifier = Modifier.weight(1f)) {
                    listOf(5 to 0.85f, 4 to 0.10f, 3 to 0.03f, 2 to 0.01f, 1 to 0.01f).forEach { (stars, progress) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp),
                        ) {
                            Text(
                                text = "$stars",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(12.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = Color(0xFFFFB800),
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Individual reviews
        reviews.take(3).forEach { review ->
            ReviewCard(review = review)
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ReviewCard(review: Review) {
    // Material 3 Expressive Review Card
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        ),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // User avatar with expressive styling
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = review.userName.first().toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = review.userName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        repeat(5) { index ->
                            FlaticonIcon(
                                glyph = FlaticonIcons.STAR,
                                contentDescription = null,
                                tint = if (index < review.rating.toInt()) Color(0xFFFFB800)
                                else Color(0xFFFFB800).copy(alpha = 0.3f),
                                modifier = Modifier.size(12.dp),
                                fontSize = 12.sp,
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = review.date,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = review.comment,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${review.helpful} found this helpful",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EnhancedBottomBar(
    course: Course,
    isEnrolled: Boolean,
    progressPercent: Float,
    onEnroll: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shadowElevation = 16.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        if (isEnrolled) {
            // Compact enrolled state: progress remains visible without repeating
            // the full-width progress block already shown in the page content.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        Text(
                            text = "${progressPercent.toInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = "Progress",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                        )
                    }
                }

                NiaOutlinedButton(
                    onClick = onContinue,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    enabled = progressPercent < 100f,
                ) {
                    Text(
                        text = if (progressPercent >= 100f) "Course Completed" else "Continue Learning",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        } else {
            // Not enrolled - show price and enroll button
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
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "${course.totalLessons} lessons • ${course.estimatedDays} days",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                NiaOutlinedButton(
                    onClick = onEnroll,
                    modifier = Modifier.height(52.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = "Enroll Now",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                    )
                }
            }
        }
    }
}

// Helper functions

private fun formatLastAccessed(timestamp: Long): String {
    if (timestamp == 0L) return "Never"

    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> "just now"
        diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m ago"
        diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)}h ago"
        diff < TimeUnit.DAYS.toMillis(7) -> "${TimeUnit.MILLISECONDS.toDays(diff)}d ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}

private fun getInstructorForCourse(course: Course): Instructor {
    return when (course.id) {
        "memorize_3_ayahs" -> Instructor(
            name = "Sheikh Ahmad",
            title = "Quran Memorization Specialist",
            bio = "15+ years experience teaching Quran memorization with proven techniques for students of all ages.",
            coursesCount = 8,
            studentsCount = "12.5K",
            rating = 4.9f,
        )
        "daily_bukhari" -> Instructor(
            name = "Dr. Muhammad Ali",
            title = "Hadith Scholar",
            bio = "PhD in Hadith Sciences, specializing in Sahih Al-Bukhari with focus on practical application.",
            coursesCount = 5,
            studentsCount = "8.2K",
            rating = 4.8f,
        )
        "juz_amma" -> Instructor(
            name = "Ustadh Ibrahim",
            title = "Tajweed & Memorization Expert",
            bio = "Ijazah holder in Hafs, teaching Juz Amma memorization with proper tajweed for over 10 years.",
            coursesCount = 12,
            studentsCount = "25K",
            rating = 4.9f,
        )
        else -> Instructor(
            name = "Islamic Academy",
            title = "Expert Team",
            bio = "A team of qualified scholars dedicated to teaching authentic Islamic knowledge.",
            coursesCount = 20,
            studentsCount = "50K",
            rating = 4.8f,
        )
    }
}

private fun getReviewsForCourse(course: Course): List<Review> {
    return listOf(
        Review(
            id = "1",
            userName = "Abdullah M.",
            rating = 5f,
            comment = "Excellent course structure! The bite-sized lessons make it easy to stay consistent with my learning.",
            date = "2 weeks ago",
            helpful = 24,
        ),
        Review(
            id = "2",
            userName = "Fatima K.",
            rating = 5f,
            comment = "Finally a course that fits my busy schedule. Highly recommend for anyone wanting to strengthen their Islamic knowledge.",
            date = "1 month ago",
            helpful = 18,
        ),
        Review(
            id = "3",
            userName = "Omar S.",
            rating = 4f,
            comment = "Great content and well-organized. Would love to see more advanced levels in the future.",
            date = "1 month ago",
            helpful = 12,
        ),
    )
}

private fun getLearningOutcomes(course: Course): List<String> {
    return when (course.id) {
        "memorize_3_ayahs" -> listOf(
            "Memorize the opening verses of all 114 Surahs",
            "Understand the themes and context of each Surah",
            "Build a strong foundation for complete Quran memorization",
            "Develop consistent memorization habits",
            "Learn effective revision techniques",
        )
        "daily_bukhari" -> listOf(
            "Read and understand authentic Prophetic narrations",
            "Learn practical lessons from Sahih Al-Bukhari",
            "Build daily habit of hadith study",
            "Understand the context and application of each hadith",
            "Strengthen your connection with the Sunnah",
        )
        "juz_amma" -> listOf(
            "Complete memorization of the 30th Juz",
            "Perfect tajweed and pronunciation",
            "Understand the meanings of each Surah",
            "Build confidence in leading prayers",
            "Master commonly recited Surahs",
        )
        "quran_reading" -> listOf(
            "Complete reading of the entire Quran",
            "Improve reading fluency and speed",
            "Develop daily Quran reading habit",
            "Track your progress through all 30 Juz",
            "Build a deeper connection with the Quran",
        )
        else -> listOf(
            "Master the course content thoroughly",
            "Apply knowledge in daily life",
            "Track progress and stay motivated",
        )
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
        "complete_quran_listening" -> generateQuranListeningModules()
        else -> emptyList()
    }
}

private val courseSurahNames = listOf(
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
    "Al-Masad", "Al-Ikhlas", "Al-Falaq", "An-Nas",
)

private fun generateMemorize3AyahsModules(): List<CourseModule> {
    return courseSurahNames.chunked(10).mapIndexed { moduleIndex, surahs ->
        CourseModule(
            id = "module_$moduleIndex",
            title = "Surahs ${moduleIndex * 10 + 1} - ${moduleIndex * 10 + surahs.size}",
            lessons = surahs.mapIndexed { index, surahName ->
                val surahNumber = moduleIndex * 10 + index + 1
                Lesson(
                    id = "surah_$surahNumber",
                    title = "Surah $surahName",
                    subtitle = "3 ayahs",
                    duration = "5 min",
                    type = LessonType.MEMORIZATION,
                    totalAyahs = 3,
                    surahNumber = surahNumber,
                )
            }
        )
    }
}

private fun generateDailyBukhariModules(): List<CourseModule> {
    val totalHadiths = 365
    val hadithsPerSession = 30
    val totalSessions = (totalHadiths + hadithsPerSession - 1) / hadithsPerSession // Ceiling division

    return (0 until totalSessions).map { sessionIndex ->
        val startHadith = sessionIndex * hadithsPerSession + 1
        val endHadith = minOf((sessionIndex + 1) * hadithsPerSession, totalHadiths)
        val hadithsInSession = endHadith - startHadith + 1

        CourseModule(
            id = "session_$sessionIndex",
            title = "Session ${sessionIndex + 1}",
            lessons = (0 until hadithsInSession).map { lessonIndex ->
                val hadithNumber = startHadith + lessonIndex
                Lesson(
                    id = "hadith_$hadithNumber",
                    title = "Hadith #$hadithNumber",
                    subtitle = "Sahih Al-Bukhari",
                    duration = "3 min",
                    type = LessonType.READING,
                )
            }
        )
    }
}

private fun generateJuzAmmaModules(): List<CourseModule> {
    // Juz Amma surahs with their actual ayah counts
    val juzAmmaSurahsWithAyahs = listOf(
        "An-Naba" to 40, "An-Nazi'at" to 46, "Abasa" to 42, "At-Takwir" to 29, "Al-Infitar" to 19,
        "Al-Mutaffifin" to 36, "Al-Inshiqaq" to 25, "Al-Buruj" to 22, "At-Tariq" to 17, "Al-A'la" to 19,
        "Al-Ghashiyah" to 26, "Al-Fajr" to 30, "Al-Balad" to 20, "Ash-Shams" to 15, "Al-Layl" to 21,
        "Ad-Duha" to 11, "Ash-Sharh" to 8, "At-Tin" to 8, "Al-Alaq" to 19, "Al-Qadr" to 5,
        "Al-Bayyinah" to 8, "Az-Zalzalah" to 8, "Al-Adiyat" to 11, "Al-Qari'ah" to 11, "At-Takathur" to 8,
        "Al-Asr" to 3, "Al-Humazah" to 9, "Al-Fil" to 5, "Quraysh" to 4, "Al-Ma'un" to 7,
        "Al-Kawthar" to 3, "Al-Kafirun" to 6, "An-Nasr" to 3, "Al-Masad" to 5, "Al-Ikhlas" to 4,
        "Al-Falaq" to 5, "An-Nas" to 6
    )

    return listOf(
        CourseModule(
            id = "module_long",
            title = "Longer Surahs (78-88)",
            lessons = juzAmmaSurahsWithAyahs.take(11).mapIndexed { index, (name, ayahCount) ->
                val surahNum = 78 + index
                Lesson(
                    id = "juz_$surahNum",
                    title = "Surah $name",
                    subtitle = "$ayahCount ayahs",
                    duration = "${ayahCount / 4 + 5} min",
                    type = LessonType.MEMORIZATION,
                    totalAyahs = ayahCount,
                    surahNumber = surahNum,
                )
            }
        ),
        CourseModule(
            id = "module_medium",
            title = "Medium Surahs (89-100)",
            lessons = juzAmmaSurahsWithAyahs.slice(11..22).mapIndexed { index, (name, ayahCount) ->
                val surahNum = 89 + index
                Lesson(
                    id = "juz_$surahNum",
                    title = "Surah $name",
                    subtitle = "$ayahCount ayahs",
                    duration = "${ayahCount / 4 + 3} min",
                    type = LessonType.MEMORIZATION,
                    totalAyahs = ayahCount,
                    surahNumber = surahNum,
                )
            }
        ),
        CourseModule(
            id = "module_short",
            title = "Short Surahs (101-114)",
            lessons = juzAmmaSurahsWithAyahs.drop(23).mapIndexed { index, (name, ayahCount) ->
                val surahNum = 101 + index
                Lesson(
                    id = "juz_$surahNum",
                    title = "Surah $name",
                    subtitle = "$ayahCount ayahs",
                    duration = "${ayahCount / 2 + 2} min",
                    type = LessonType.MEMORIZATION,
                    totalAyahs = ayahCount,
                    surahNumber = surahNum,
                )
            }
        ),
    )
}

private fun generateQuranReadingModules(): List<CourseModule> {
    val juzStartPages = listOf(
        1, 22, 42, 62, 82, 102, 121, 142, 162, 182,
        201, 222, 242, 262, 282, 302, 322, 342, 362, 382,
        402, 422, 442, 462, 482, 502, 522, 542, 562, 582,
    )

    return juzStartPages.mapIndexed { juzIndex, startPage ->
        val endPage = juzStartPages.getOrNull(juzIndex + 1)?.minus(1) ?: 604
        CourseModule(
            id = "juz_$juzIndex",
            title = "Juz ${juzIndex + 1}",
            lessons = (startPage..endPage).map { pageNumber ->
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

private fun generateQuranListeningModules(): List<CourseModule> {
    // All 114 surahs organized by Juz for listening
    val surahNames = courseSurahNames

    // Approximate duration in minutes for each surah (based on average recitation)
    val surahDurations = listOf(
        1, 150, 100, 90, 70, 80, 90, 35, 70, 55,
        60, 55, 25, 25, 20, 65, 55, 55, 25, 60,
        50, 40, 30, 35, 30, 55, 45, 50, 35, 30,
        20, 15, 40, 30, 25, 25, 45, 25, 40, 45,
        30, 30, 30, 15, 20, 20, 25, 20, 15, 25,
        15, 15, 15, 20, 15, 25, 20, 20, 20, 15,
        10, 10, 10, 10, 10, 10, 15, 15, 15, 10,
        10, 15, 10, 15, 10, 15, 15, 15, 15, 10,
        10, 10, 15, 10, 10, 5, 10, 10, 15, 10,
        5, 10, 5, 5, 5, 5, 5, 5, 5, 5,
        5, 5, 3, 5, 5, 3, 5, 3, 5, 3,
        5, 3, 3, 3
    )

    // Group surahs into 6 modules
    return listOf(
        CourseModule(
            id = "module_1",
            title = "Surahs 1-19",
            lessons = (0 until 19).map { index ->
                val surahNum = index + 1
                Lesson(
                    id = "surah_$surahNum",
                    title = "Surah ${surahNames[index]}",
                    subtitle = "Listen & reflect",
                    duration = "${surahDurations.getOrElse(index) { 10 }} min",
                    type = LessonType.LISTENING,
                    surahNumber = surahNum,
                )
            }
        ),
        CourseModule(
            id = "module_2",
            title = "Surahs 20-38",
            lessons = (19 until 38).map { index ->
                val surahNum = index + 1
                Lesson(
                    id = "surah_$surahNum",
                    title = "Surah ${surahNames[index]}",
                    subtitle = "Listen & reflect",
                    duration = "${surahDurations.getOrElse(index) { 10 }} min",
                    type = LessonType.LISTENING,
                    surahNumber = surahNum,
                )
            }
        ),
        CourseModule(
            id = "module_3",
            title = "Surahs 39-57",
            lessons = (38 until 57).map { index ->
                val surahNum = index + 1
                Lesson(
                    id = "surah_$surahNum",
                    title = "Surah ${surahNames[index]}",
                    subtitle = "Listen & reflect",
                    duration = "${surahDurations.getOrElse(index) { 10 }} min",
                    type = LessonType.LISTENING,
                    surahNumber = surahNum,
                )
            }
        ),
        CourseModule(
            id = "module_4",
            title = "Surahs 58-76",
            lessons = (57 until 76).map { index ->
                val surahNum = index + 1
                Lesson(
                    id = "surah_$surahNum",
                    title = "Surah ${surahNames[index]}",
                    subtitle = "Listen & reflect",
                    duration = "${surahDurations.getOrElse(index) { 10 }} min",
                    type = LessonType.LISTENING,
                    surahNumber = surahNum,
                )
            }
        ),
        CourseModule(
            id = "module_5",
            title = "Surahs 77-95",
            lessons = (76 until 95).map { index ->
                val surahNum = index + 1
                Lesson(
                    id = "surah_$surahNum",
                    title = "Surah ${surahNames[index]}",
                    subtitle = "Listen & reflect",
                    duration = "${surahDurations.getOrElse(index) { 10 }} min",
                    type = LessonType.LISTENING,
                    surahNumber = surahNum,
                )
            }
        ),
        CourseModule(
            id = "module_6",
            title = "Surahs 96-114",
            lessons = (95 until 114).map { index ->
                val surahNum = index + 1
                Lesson(
                    id = "surah_$surahNum",
                    title = "Surah ${surahNames[index]}",
                    subtitle = "Listen & reflect",
                    duration = "${surahDurations.getOrElse(index) { 10 }} min",
                    type = LessonType.LISTENING,
                    surahNumber = surahNum,
                )
            }
        ),
    )
}

@Composable
private fun TextButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        content()
    }
}

/**
 * Memorization Progress Section - Shows detailed ayah-by-ayah progress
 */
@Composable
private fun MemorizationProgressSection(
    modules: List<CourseModule>,
    memorizationProgress: Map<String, MemorizationProgress>,
    timeSpentPerLesson: Map<String, Int>,
    totalTimeSpentMinutes: Int,
    accentColor: Color,
    onAyahToggle: (String, Int) -> Unit,
) {
    val totalAyahsToMemorize = modules.sumOf { module ->
        module.lessons.sumOf { it.totalAyahs }
    }
    val totalMemorizedAyahs = memorizationProgress.values.sumOf { it.memorizedAyahs.size }
    val memorizedSurahs = memorizationProgress.count { it.value.memorizedAyahs.size >= it.value.totalAyahs }
    val totalSurahs = modules.sumOf { it.lessons.size }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FlaticonIcon(
                    glyph = FlaticonIcons.BOOK,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp),
                    fontSize = 24.sp,
                )
                Text(
                    text = "Memorization Progress",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                // Ayahs Memorized
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "$totalMemorizedAyahs",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                    )
                    Text(
                        text = "of $totalAyahsToMemorize ayahs",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(40.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )

                // Surahs Complete
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "$memorizedSurahs",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                    )
                    Text(
                        text = "of $totalSurahs surahs",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(40.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )

                // Time Spent
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = formatTimeSpentShort(totalTimeSpentMinutes),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                    )
                    Text(
                        text = "time spent",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Progress Bar
            val progress = if (totalAyahsToMemorize > 0) {
                totalMemorizedAyahs.toFloat() / totalAyahsToMemorize
            } else 0f

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Overall Progress",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = accentColor,
                    trackColor = accentColor.copy(alpha = 0.2f),
                    strokeCap = StrokeCap.Round,
                )
            }

            // Tip
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = accentColor.copy(alpha = 0.1f),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "💡",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "Tap the numbered circles next to each surah to mark ayahs as memorized",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/**
 * Time Spent Card - Shows total learning time
 */
@Composable
private fun TimeSpentCard(
    totalMinutes: Int,
    lessonsWithTime: Int,
    totalLessons: Int,
    accentColor: Color,
) {
    // Material 3 Expressive Time Spent Card
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = accentColor.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            accentColor.copy(alpha = 0.15f),
        ),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Clock icon with expressive container
            Surface(
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(16.dp),
                color = accentColor.copy(alpha = 0.15f),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    FlaticonIcon(
                        glyph = FlaticonIcons.SCHEDULE,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(26.dp),
                        fontSize = 26.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Total Learning Time",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatTimeSpent(totalMinutes),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                )
                Text(
                    text = "$lessonsWithTime of $totalLessons lessons started",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// Helper functions for persistence

private fun loadMemorizationProgress(
    prefs: android.content.SharedPreferences,
    courseId: String
): Map<String, MemorizationProgress> {
    val result = mutableMapOf<String, MemorizationProgress>()
    val allKeys = prefs.all.keys.filter { it.startsWith("mem_${courseId}_") }

    for (key in allKeys) {
        val lessonId = key.removePrefix("mem_${courseId}_")
        val ayahsString = prefs.getString(key, "") ?: ""
        val ayahs = if (ayahsString.isNotEmpty()) {
            ayahsString.split(",").mapNotNull { it.toIntOrNull() }.toSet()
        } else {
            emptySet()
        }
        val totalAyahs = prefs.getInt("mem_total_${courseId}_$lessonId", 3)
        val timeSpent = prefs.getInt("time_${courseId}_$lessonId", 0)
        val lastPracticed = prefs.getLong("lastprac_${courseId}_$lessonId", 0L)

        result[lessonId] = MemorizationProgress(
            lessonId = lessonId,
            memorizedAyahs = ayahs,
            totalAyahs = totalAyahs,
            timeSpentMinutes = timeSpent,
            lastPracticed = lastPracticed,
        )
    }

    return result
}

private fun saveMemorizationProgress(
    prefs: android.content.SharedPreferences,
    courseId: String,
    progress: Map<String, MemorizationProgress>
) {
    val editor = prefs.edit()
    for ((lessonId, memProgress) in progress) {
        val ayahsString = memProgress.memorizedAyahs.joinToString(",")
        editor.putString("mem_${courseId}_$lessonId", ayahsString)
        editor.putInt("mem_total_${courseId}_$lessonId", memProgress.totalAyahs)
        editor.putLong("lastprac_${courseId}_$lessonId", memProgress.lastPracticed)
    }
    editor.apply()
}

private fun loadTimeSpent(
    prefs: android.content.SharedPreferences,
    courseId: String
): Map<String, Int> {
    val result = mutableMapOf<String, Int>()
    val allKeys = prefs.all.keys.filter { it.startsWith("time_${courseId}_") }

    for (key in allKeys) {
        val lessonId = key.removePrefix("time_${courseId}_")
        val minutes = prefs.getInt(key, 0)
        if (minutes > 0) {
            result[lessonId] = minutes
        }
    }

    return result
}

private fun saveTimeSpent(
    prefs: android.content.SharedPreferences,
    courseId: String,
    timeSpent: Map<String, Int>
) {
    val editor = prefs.edit()
    for ((lessonId, minutes) in timeSpent) {
        editor.putInt("time_${courseId}_$lessonId", minutes)
    }
    editor.apply()
}

private fun formatTimeSpent(minutes: Int): String {
    return when {
        minutes < 60 -> "${minutes}min"
        minutes < 1440 -> "${minutes / 60}h ${minutes % 60}min"
        else -> "${minutes / 1440}d ${(minutes % 1440) / 60}h"
    }
}

private fun formatTimeSpentShort(minutes: Int): String {
    return when {
        minutes < 60 -> "${minutes}m"
        minutes < 1440 -> "${minutes / 60}h"
        else -> "${minutes / 1440}d"
    }
}
