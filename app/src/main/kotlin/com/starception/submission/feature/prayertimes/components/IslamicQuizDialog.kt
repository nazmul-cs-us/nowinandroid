/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.starception.submission.feature.prayertimes.components

import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starception.submission.core.designsystem.component.NiaButton
import com.starception.submission.core.model.deenly.IslamicQuizBank
import com.starception.submission.core.model.deenly.IslamicQuizQuestion
import com.starception.submission.feature.prayertimes.quiz.IslamicQuizRepository
import com.google.android.material.card.MaterialCardView

@Composable
fun IslamicQuizDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IslamicQuizContent(onDismiss = onDismiss, modifier = modifier)
}

/** Quiz body hosted by the app-shell voice assistant's shared container. */
@Composable
fun IslamicQuizContent(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    featuredQuestion: IslamicQuizQuestion? = null,
) {
    val initialQuestions = remember(featuredQuestion?.id) {
        (listOfNotNull(featuredQuestion) + IslamicQuizBank.questions.shuffled())
            .distinctBy(IslamicQuizQuestion::id)
    }
    val questions by produceState(initialValue = initialQuestions, key1 = featuredQuestion?.id) {
        value = (listOfNotNull(featuredQuestion) + IslamicQuizRepository().loadQuestions())
            .distinctBy(IslamicQuizQuestion::id)
    }
    var questionIndex by remember { mutableIntStateOf(0) }
    var selectedOption by remember { mutableStateOf<Int?>(null) }
    var answerSubmitted by remember { mutableStateOf(false) }
    var score by remember { mutableIntStateOf(0) }
    val question = questions[questionIndex]
    val answered = answerSubmitted
    val isCorrect = selectedOption == question.correctOption
    val isLastQuestion = questionIndex == questions.lastIndex

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "KNOWLEDGE CHECK",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
                Text(
                    text = "Islamic quiz",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    text = "$score pts",
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close quiz",
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Question ${questionIndex + 1} of ${questions.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { (questionIndex + 1f) / questions.size },
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = question.prompt,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            question.options.forEachIndexed { index, option ->
                QuizOption(
                    text = option,
                    selected = selectedOption == index,
                    correct = index == question.correctOption,
                    answered = answered,
                    onClick = { selectedOption = index },
                )
            }
            if (answered) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (isCorrect) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                    contentColor = if (isCorrect) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    },
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = if (isCorrect) {
                                    Icons.Rounded.CheckCircle
                                } else {
                                    Icons.Rounded.Cancel
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = if (isCorrect) "Correct" else "Not quite",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(question.explanation, style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = "Source: ${question.sourceLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        NiaButton(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 42.dp),
            enabled = selectedOption != null,
            onClick = {
                if (!answered) {
                    answerSubmitted = true
                    if (isCorrect) score++
                } else if (isLastQuestion) {
                    onDismiss()
                } else {
                    questionIndex++
                    selectedOption = null
                    answerSubmitted = false
                }
            },
        ) {
            Text(
                text = when {
                    selectedOption == null -> "Choose an answer"
                    !answered -> "Check answer"
                    isLastQuestion -> "Finish quiz"
                    else -> "Next question"
                },
                fontWeight = FontWeight.Bold,
            )
            if (answered && !isLastQuestion) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun QuizOption(
    text: String,
    selected: Boolean,
    correct: Boolean,
    answered: Boolean,
    onClick: () -> Unit,
) {
    val containerColor by animateColorAsState(
        targetValue = when {
            answered && correct -> MaterialTheme.colorScheme.primaryContainer
            answered && selected -> MaterialTheme.colorScheme.errorContainer
            selected -> MaterialTheme.colorScheme.surface
            else -> MaterialTheme.colorScheme.surface
        },
        label = "quizOptionColor",
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            answered && correct -> MaterialTheme.colorScheme.onPrimaryContainer
            answered && selected -> MaterialTheme.colorScheme.onErrorContainer
            answered -> MaterialTheme.colorScheme.onSurfaceVariant
            selected -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurface
        },
        label = "quizOptionContentColor",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            answered && correct -> MaterialTheme.colorScheme.primary
            answered && selected -> MaterialTheme.colorScheme.error
            selected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outlineVariant
        },
        label = "quizOptionBorderColor",
    )
    val checked = selected || (answered && correct)
    AndroidView(
        modifier = Modifier
            .fillMaxWidth(),
        factory = { context ->
            val density = context.resources.displayMetrics.density
            val cardContext = ContextThemeWrapper(
                context,
                com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar,
            )
            MaterialCardView(cardContext).apply {
                isCheckable = true
                isClickable = true
                isFocusable = true
                strokeWidth = density.toInt().coerceAtLeast(1)
                checkedIconSize = (20 * density).toInt()
                checkedIconMargin = (6 * density).toInt()
                addView(
                    TextView(cardContext).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                        maxLines = 2
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        setPadding(
                            (16 * density).toInt(),
                            (10 * density).toInt(),
                            (42 * density).toInt(),
                            (10 * density).toInt(),
                        )
                    },
                )
            }
        },
        update = { card ->
            val label = card.getChildAt(0) as TextView
            label.text = text
            label.setTextColor(contentColor.toArgb())
            label.typeface = android.graphics.Typeface.create(
                label.typeface,
                if (checked) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL,
            )
            card.setCardBackgroundColor(containerColor.toArgb())
            card.strokeColor = borderColor.toArgb()
            card.setCheckedIconTint(ColorStateList.valueOf(borderColor.toArgb()))
            card.isChecked = checked
            card.isClickable = !answered
            card.setOnClickListener(if (answered) null else android.view.View.OnClickListener { onClick() })
            card.contentDescription = when {
                answered && correct -> "$text, correct answer"
                answered && selected -> "$text, incorrect answer"
                selected -> "$text, selected answer"
                else -> text
            }
        },
    )
}
