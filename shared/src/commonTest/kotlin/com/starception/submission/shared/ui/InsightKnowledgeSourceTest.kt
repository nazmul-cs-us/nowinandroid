/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */

package com.starception.submission.shared.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate

class InsightKnowledgeSourceTest {
    @Test
    fun expandedKnowledgeSource_rotatesAcrossAvailableCollections() {
        val start = LocalDate(2026, 10, 12)

        assertEquals(1, expandedKnowledgeSourceIndex(start, 4))
        assertEquals(2, expandedKnowledgeSourceIndex(LocalDate(2026, 10, 13), 4))
        assertEquals(3, expandedKnowledgeSourceIndex(LocalDate(2026, 10, 14), 4))
        assertEquals(0, expandedKnowledgeSourceIndex(LocalDate(2026, 10, 15), 4))
    }
}
