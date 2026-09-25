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

package com.starception.submission.core.duadatabase

import org.junit.Assert.assertEquals
import org.junit.Test

class DuaEntityTest {

    @Test
    fun invocationMappingPreservesRecordedAudioUrl() {
        val audioUrl = "https://example.test/fortress/dua.mp3"
        val entity = DuaInvocationEntity(
            id = 1,
            chapterId = 16,
            position = 1,
            arabic = "دعاء",
            transliteration = "Dua",
            translation = "Supplication",
            context = null,
            instruction = null,
            note = null,
            postContext = null,
            description = null,
            sourceIds = null,
            audioUrl = audioUrl,
        )

        assertEquals(audioUrl, entity.toDua("Opening prayer").audioUrl)
    }
}
