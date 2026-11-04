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

package com.starception.submission.shared.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedTopicCatalogTest {
    @Test
    fun catalogMatchesAndroidTopicIds() {
        assertEquals(
            // Include Shama'il (9) and the additional downloadable Hadith collections (38–45).
            setOf(7, 8, 9, 11) + (21..45),
            SharedTopics.mapTo(mutableSetOf()) { it.id },
        )
    }

    @Test
    fun everyFortressTopicHasChapterNews() {
        assertEquals((21..37).toSet(), fortressChaptersByTopic.keys)
        assertTrue(fortressChaptersByTopic.values.all { it.isNotEmpty() })
    }
}
