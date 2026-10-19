/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */

package com.starception.submission.ml

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HadithBookCategorizerTest {

    private val categorizer = HadithBookCategorizer(
        org.robolectric.RuntimeEnvironment.getApplication(),
    )

    private val proposed = listOf(
        "The Book of Faith",
        "The Book of Purification",
        "The Book of Prayer",
        "The Book of Fasting",
    )

    @Test
    fun cleanSampleRunsBecomeContiguousRanges() {
        val labels = listOf(
            1 to 0, 2 to 0, 3 to 0, // Faith
            10 to 1, 11 to 1, // Purification
            20 to 2, 21 to 2, 22 to 2, // Prayer
            30 to 3, // Fasting (last book reaches hadithCount)
        )
        val books = categorizer.buildContiguousBooks(proposed, labels, hadithCount = 35)

        assertEquals(
            listOf(
                Triple("The Book of Faith", 1, 9),
                Triple("The Book of Purification", 10, 19),
                Triple("The Book of Prayer", 20, 29),
                Triple("The Book of Fasting", 30, 35),
            ),
            books.map { Triple(it.nameEnglish, it.firstHadithId, it.lastHadithId) },
        )
        // Coverage must be gapless and complete.
        assertEquals(35, books.sumOf { it.hadithCount })
        books.forEachIndexed { index, book ->
            if (index > 0) assertEquals(books[index - 1].lastHadithId + 1, book.firstHadithId)
        }
    }

    @Test
    fun isolatedMisreadWindowIsAbsorbed() {
        val labels = listOf(
            1 to 0, 2 to 0, 3 to 0, // Faith
            15 to 3, // single misread window
            20 to 1, 21 to 1, 22 to 1, // Purification
        )
        val books = categorizer.buildContiguousBooks(proposed, labels, hadithCount = 25)

        // The lone mislabel must not create a third book.
        assertEquals(2, books.size)
        assertEquals("The Book of Faith", books[0].nameEnglish)
        assertEquals("The Book of Purification", books[1].nameEnglish)
        assertEquals(25, books.sumOf { it.hadithCount })
    }

    @Test
    fun repeatedBookKeepsOnlyItsStrongestRun() {
        val labels = listOf(
            1 to 0, 2 to 0, 3 to 0,
            10 to 1, 11 to 1, 12 to 1, // Purification
            20 to 0, // stray second Faith sample
        )
        val books = categorizer.buildContiguousBooks(proposed, labels, hadithCount = 22)

        // The stray Faith sample must be dropped; the earlier 3-vote run wins.
        assertEquals(
            listOf("The Book of Faith", "The Book of Purification"),
            books.map { it.nameEnglish },
        )
        assertEquals(22, books.sumOf { it.hadithCount })
    }
}
