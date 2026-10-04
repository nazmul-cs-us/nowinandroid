/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.starception.submission.feature.prayertimes.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starception.submission.core.designsystem.component.NiaButton
import com.starception.submission.core.model.deenly.IslamicQuizBank
import com.starception.submission.feature.prayertimes.quiz.IslamicQuizRepository

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
) {
    val initialQuestions = remember { IslamicQuizBank.questions.shuffled() }
    val questions by produceState(initialValue = initialQuestions, key1 = Unit) {
        value = IslamicQuizRepository().loadQuestions()
    }
    var questionIndex by remember { mutableIntStateOf(0) }
    var selectedOption by remember { mutableStateOf<Int?>(null) }
    var score by remember { mutableIntStateOf(0) }
    val question = questions[questionIndex]
    val answered = selectedOption != null
    val isCorrect = selectedOption == question.correctOption
    val isLastQuestion = questionIndex == questions.lastIndex
    val displayedScore = score + if (answered && isCorrect) 1 else 0

    Column(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 10.dp),
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
                    text = "$displayedScore pts",
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
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = question.prompt,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            question.options.forEachIndexed { index, option ->
                QuizOption(
                    index = index,
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
            enabled = answered,
            onClick = {
                if (isCorrect) score++
                if (isLastQuestion) onDismiss() else {
                    questionIndex++
                    selectedOption = null
                }
            },
        ) {
            Text(
                text = when {
                    !answered -> "Choose an answer"
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
    index: Int,
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
            else -> MaterialTheme.colorScheme.surface
        },
        label = "quizOptionColor",
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            answered && correct -> MaterialTheme.colorScheme.onPrimaryContainer
            answered && selected -> MaterialTheme.colorScheme.onErrorContainer
            answered -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.onSurface
        },
        label = "quizOptionContentColor",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            answered && correct -> MaterialTheme.colorScheme.primary
            answered && selected -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.outlineVariant
        },
        label = "quizOptionBorderColor",
    )
    val badgeColor by animateColorAsState(
        targetValue = when {
            answered && correct -> MaterialTheme.colorScheme.primary
            answered && selected -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.secondaryContainer
        },
        label = "quizOptionBadgeColor",
    )
    val badgeContentColor = when {
        answered && correct -> MaterialTheme.colorScheme.onPrimary
        answered && selected -> MaterialTheme.colorScheme.onError
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = !answered,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        colors = CardDefaults.outlinedCardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = if (answered && (correct || selected)) 2.dp else 1.dp,
            color = borderColor,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Surface(
                modifier = Modifier.size(24.dp),
                shape = CircleShape,
                color = badgeColor,
                contentColor = badgeContentColor,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = ('A'.code + index).toChar().toString(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected || (answered && correct)) {
                    FontWeight.SemiBold
                } else {
                    FontWeight.Normal
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (answered && (correct || selected)) {
                Icon(
                    imageVector = if (correct) {
                        Icons.Rounded.CheckCircle
                    } else {
                        Icons.Rounded.Cancel
                    },
                    contentDescription = if (correct) "Correct answer" else "Incorrect answer",
                    tint = if (correct) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Surface(
                    modifier = Modifier.size(18.dp),
                    shape = CircleShape,
                    color = Color.Transparent,
                    border = BorderStroke(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.outline,
                    ),
                ) {}
            }
        }
    }
}
