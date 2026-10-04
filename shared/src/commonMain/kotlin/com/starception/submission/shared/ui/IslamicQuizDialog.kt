/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.starception.submission.shared.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.starception.submission.core.model.deenly.IslamicQuizBank

@Composable
internal fun IslamicQuizDialog(
    onDismiss: () -> Unit,
) {
    val questions = remember { IslamicQuizBank.questions.shuffled() }
    var questionIndex by remember { mutableIntStateOf(0) }
    var selectedOption by remember { mutableStateOf<Int?>(null) }
    var answerSubmitted by remember { mutableStateOf(false) }
    var score by remember { mutableIntStateOf(0) }
    val question = questions[questionIndex]
    val answered = answerSubmitted
    val isCorrect = selectedOption == question.correctOption
    val isLastQuestion = questionIndex == questions.lastIndex

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Islamic quiz") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Question ${questionIndex + 1} of ${questions.size} · Score $score",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(question.prompt, style = MaterialTheme.typography.titleMedium)
                question.options.forEachIndexed { index, option ->
                    SharedQuizOption(
                        text = option,
                        selected = selectedOption == index,
                        correct = index == question.correctOption,
                        answered = answered,
                        onClick = { selectedOption = index },
                    )
                }
                if (answered) {
                    Text(
                        text = if (isCorrect) "Correct" else "Not quite",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isCorrect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                    Text(question.explanation, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Source: ${question.sourceLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
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
                    when {
                        selectedOption == null -> "Choose an answer"
                        !answered -> "Check answer"
                        isLastQuestion -> "Finish"
                        else -> "Next"
                    },
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun SharedQuizOption(
    text: String,
    selected: Boolean,
    correct: Boolean,
    answered: Boolean,
    onClick: () -> Unit,
) {
    val containerColor by animateColorAsState(
        when {
            answered && correct -> MaterialTheme.colorScheme.primaryContainer
            answered && selected -> MaterialTheme.colorScheme.errorContainer
            selected -> MaterialTheme.colorScheme.secondary
                .copy(alpha = 0.16f)
                .compositeOver(MaterialTheme.colorScheme.surface)
            else -> MaterialTheme.colorScheme.surface
        },
        label = "sharedQuizOptionContainer",
    )
    val borderColor by animateColorAsState(
        when {
            answered && correct -> MaterialTheme.colorScheme.primary
            answered && selected -> MaterialTheme.colorScheme.error
            selected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outlineVariant
        },
        label = "sharedQuizOptionBorder",
    )

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = !answered,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = text,
                modifier = Modifier.padding(start = 16.dp, top = 10.dp, end = 42.dp, bottom = 10.dp),
            )
            if (answered && (correct || selected)) {
                Icon(
                    imageVector = if (correct) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
                    contentDescription = if (correct) "Correct answer" else "Incorrect answer",
                    tint = borderColor,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(20.dp),
                )
            } else if (selected) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Selected answer",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(20.dp),
                )
            }
        }
    }
}
