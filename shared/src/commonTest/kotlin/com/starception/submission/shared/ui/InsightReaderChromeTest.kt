/*
 * Copyright 2026 The Android Open Source Project
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

package com.starception.submission.shared.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import com.starception.submission.core.designsystem.component.InsightReaderChromeState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InsightReaderChromeTest {
    @Test
    fun readingInEitherDirectionHidesChromeWithoutConsumingScroll() {
        for (direction in listOf(-1f, 1f)) {
            val state = InsightReaderChromeState(32f)
            assertEquals(Offset.Zero, state.connection.onPostScroll(Offset(0f, direction * 20f), Offset.Zero, NestedScrollSource.UserInput))
            assertFalse(state.hidden)
            state.connection.onPostScroll(Offset(0f, direction * 20f), Offset.Zero, NestedScrollSource.UserInput)
            assertTrue(state.hidden)
            state.showControls()
            assertFalse(state.hidden)
        }
    }

    @Test
    fun horizontalSwipesAndProgrammaticScrollDoNotHideControls() {
        val state = InsightReaderChromeState(32f)
        state.connection.onPostScroll(Offset(100f, 0f), Offset.Zero, NestedScrollSource.UserInput)
        state.connection.onPostScroll(Offset(0f, -100f), Offset.Zero, NestedScrollSource.SideEffect)
        assertFalse(state.hidden)
    }

    @Test
    fun pullingPastTheStartRestoresChrome() {
        val state = InsightReaderChromeState(32f)
        state.connection.onPostScroll(Offset(0f, -40f), Offset.Zero, NestedScrollSource.UserInput)
        assertTrue(state.hidden)
        repeat(4) { state.connection.onPostScroll(Offset.Zero, Offset(0f, 8f), NestedScrollSource.UserInput) }
        assertFalse(state.hidden)
    }
}
