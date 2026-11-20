package com.starception.submission.settings.components

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceRecognitionDefaultTest {
    @Test fun fullTranscriptionIsTheDefaultForNewAndUnconfiguredUsers() {
        assertEquals(VoiceRecognitionEngine.WHISPER, VoiceSettingsState().selectedEngine)
        assertEquals(VoiceRecognitionEngine.WHISPER, VoiceRecognitionEngine.fromPreference(null))
        assertEquals(VoiceRecognitionEngine.WHISPER, VoiceRecognitionEngine.fromPreference("retired"))
    }

    @Test fun explicitlySelectedRecognitionModesArePreserved() {
        VoiceRecognitionEngine.entries.forEach { engine ->
            assertEquals(engine, VoiceRecognitionEngine.fromPreference(engine.name))
        }
    }
}
