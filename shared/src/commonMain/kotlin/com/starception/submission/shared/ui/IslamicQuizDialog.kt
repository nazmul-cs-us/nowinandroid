/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.starception.submission.shared.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.starception.submission.core.model.deenly.IslamicQuizBank

@Composable
internal fun IslamicQuizDialog(
    onDismiss: () -> Unit,
) {
    val questions = remember { IslamicQuizBank.questions.shuffled() }
    var questionIndex by remember { mutableIntStateOf(0) }
    var selectedOption by remember { mutableStateOf<Int?>(null) }
    var score by remember { mutableIntStateOf(0) }
    val question = questions[questionIndex]
    val answered = selectedOption != null
    val isCorrect = selectedOption == question.correctOption
    val isLastQuestion = questionIndex == questions.lastIndex

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Islamic quiz") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Question ${questionIndex + 1} of ${questions.size} · Score $score",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(question.prompt, style = MaterialTheme.typography.titleMedium)
                question.options.forEachIndexed { index, option ->
                    val selected = selectedOption == index
                    val optionColor = when {
                        !answered -> MaterialTheme.colorScheme.surfaceContainerHighest
                        index == question.correctOption -> MaterialTheme.colorScheme.primaryContainer
                        selected -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceContainerHighest
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !answered) { selectedOption = index },
                        color = optionColor,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text(
                            text = option,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        )
                    }
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
                enabled = answered,
                onClick = {
                    if (isCorrect) score++
                    if (isLastQuestion) onDismiss() else {
                        questionIndex++
                        selectedOption = null
                    }
                },
            ) { Text(if (isLastQuestion) "Finish" else "Next") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
