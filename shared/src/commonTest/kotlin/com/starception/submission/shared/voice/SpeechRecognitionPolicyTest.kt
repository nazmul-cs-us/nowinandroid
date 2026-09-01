/*
 * Copyright 2021 The Android Open Source Project
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

package com.starception.submission.shared.voice

import com.starception.submission.shared.settings.VoiceRecognitionMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpeechRecognitionPolicyTest {
    @Test
    fun keywordModeReturnsYesOrNoBeforeFinalResult() {
        assertEquals(
            "YES",
            resolvedRecognitionText(VoiceRecognitionMode.KEYWORDS, "I said YES!", false),
        )
        assertEquals(
            "no",
            resolvedRecognitionText(VoiceRecognitionMode.KEYWORDS, "yes, actually no", false),
        )
        assertNull(resolvedRecognitionText(VoiceRecognitionMode.KEYWORDS, "maybe", true))
    }

    @Test
    fun transcriptionModeWaitsForFinalResult() {
        assertNull(
            resolvedRecognitionText(VoiceRecognitionMode.TRANSCRIPTION, "still speaking", false),
        )
        assertEquals(
            "complete response",
            resolvedRecognitionText(VoiceRecognitionMode.TRANSCRIPTION, " complete response ", true),
        )
    }
}
