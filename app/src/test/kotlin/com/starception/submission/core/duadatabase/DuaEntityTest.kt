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
