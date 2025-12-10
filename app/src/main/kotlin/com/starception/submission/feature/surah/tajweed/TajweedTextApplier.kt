package com.starception.submission.feature.surah.tajweed

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/**
 * Utility for applying Tajweed color annotations to text.
 * Converts plain Arabic text with Tajweed annotations to AnnotatedString.
 */
object TajweedTextApplier {

    /**
     * Apply Tajweed annotations to Arabic text.
     * Returns an AnnotatedString with colored spans for Tajweed rules.
     *
     * @param text The plain Arabic text
     * @param annotations List of Tajweed annotations with character positions
     * @param defaultStyle Optional default SpanStyle for non-annotated text
     * @return AnnotatedString with Tajweed colors applied
     */
    fun apply(
        text: String,
        annotations: List<TajweedAnnotation>,
        defaultStyle: SpanStyle? = null
    ): AnnotatedString {
        if (annotations.isEmpty()) {
            return if (defaultStyle != null) {
                buildAnnotatedString {
                    withStyle(defaultStyle) {
                        append(text)
                    }
                }
            } else {
                AnnotatedString(text)
            }
        }

        // Sort annotations by start index to process in order
        val sortedAnnotations = annotations.sortedBy { it.startIndex }

        return buildAnnotatedString {
            var currentIndex = 0

            for (annotation in sortedAnnotations) {
                // Clamp indices to valid range
                val startIndex = annotation.startIndex.coerceIn(0, text.length)
                val endIndex = annotation.endIndex.coerceIn(0, text.length)

                // Skip invalid annotations
                if (startIndex >= endIndex || startIndex >= text.length) continue

                // Append text before this annotation (if any)
                if (currentIndex < startIndex) {
                    val beforeText = text.substring(currentIndex, startIndex)
                    if (defaultStyle != null) {
                        withStyle(defaultStyle) {
                            append(beforeText)
                        }
                    } else {
                        append(beforeText)
                    }
                }

                // Append annotated text with Tajweed color
                if (startIndex < text.length) {
                    val annotatedText = text.substring(startIndex, endIndex.coerceAtMost(text.length))
                    withStyle(SpanStyle(color = annotation.rule.color)) {
                        append(annotatedText)
                    }
                }

                currentIndex = endIndex.coerceAtMost(text.length)
            }

            // Append remaining text after last annotation
            if (currentIndex < text.length) {
                val remainingText = text.substring(currentIndex)
                if (defaultStyle != null) {
                    withStyle(defaultStyle) {
                        append(remainingText)
                    }
                } else {
                    append(remainingText)
                }
            }
        }
    }

    /**
     * Apply Tajweed annotations with overlapping support.
     * When multiple annotations overlap, later ones take precedence.
     */
    fun applyWithOverlap(
        text: String,
        annotations: List<TajweedAnnotation>,
        defaultStyle: SpanStyle? = null
    ): AnnotatedString {
        if (annotations.isEmpty()) {
            return if (defaultStyle != null) {
                buildAnnotatedString {
                    withStyle(defaultStyle) {
                        append(text)
                    }
                }
            } else {
                AnnotatedString(text)
            }
        }

        // Create a color array for each character
        val charColors = arrayOfNulls<TajweedRule>(text.length)

        // Apply annotations (later ones override earlier)
        for (annotation in annotations) {
            val startIndex = annotation.startIndex.coerceIn(0, text.length)
            val endIndex = annotation.endIndex.coerceIn(0, text.length)
            for (i in startIndex until endIndex) {
                if (i < text.length) {
                    charColors[i] = annotation.rule
                }
            }
        }

        return buildAnnotatedString {
            var i = 0
            while (i < text.length) {
                val currentRule = charColors[i]

                // Find end of current span (same rule or same null)
                var spanEnd = i + 1
                while (spanEnd < text.length && charColors[spanEnd] == currentRule) {
                    spanEnd++
                }

                val spanText = text.substring(i, spanEnd)

                if (currentRule != null) {
                    withStyle(SpanStyle(color = currentRule.color)) {
                        append(spanText)
                    }
                } else if (defaultStyle != null) {
                    withStyle(defaultStyle) {
                        append(spanText)
                    }
                } else {
                    append(spanText)
                }

                i = spanEnd
            }
        }
    }
}
