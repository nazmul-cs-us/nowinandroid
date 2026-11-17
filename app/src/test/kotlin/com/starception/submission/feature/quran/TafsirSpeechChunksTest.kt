package com.starception.submission.feature.quran

import org.junit.Assert.*
import org.junit.Test

class TafsirSpeechChunksTest {
    @Test fun longMultilingualCommentaryIsSplitWithoutLosingTextOrBreakingUnicode() {
        val text = ("বাংলা تفسير हिन्दी 中文 🌿.\n").repeat(1200)
        val chunks = tafsirSpeechChunks(text, 127)
        assertEquals(text, chunks.joinToString(""))
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 127 })
        assertTrue(chunks.none { it.first().isLowSurrogate() || it.last().isHighSurrogate() })
    }
    @Test fun aSingleLongParagraphWithoutSpacesStillFitsTheEngineLimit() {
        val text = "🌿中文".repeat(2000)
        val chunks = tafsirSpeechChunks(text, 101)
        assertEquals(text, chunks.joinToString(""))
        assertTrue(chunks.all { it.length <= 101 })
        assertTrue(chunks.none { it.first().isLowSurrogate() || it.last().isHighSurrogate() })
    }
    @Test fun blankCommentaryDoesNotProduceUtterances() { assertTrue(tafsirSpeechChunks(" \n\t").isEmpty()) }
}
