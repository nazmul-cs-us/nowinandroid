package com.starception.submission.core.qurandatabase

import org.junit.Assert.assertEquals
import org.junit.Test

class OpeningBismillahTest {
    @Test fun removesOpeningBismillahFromTodaysReadingVerseOne() {
        // Exact text stored in quran.db on the Samsung (the alif and shadda
        // forms differ from the enhanced Quran database's spelling).
        val verse = "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ عَبَسَ وَتَوَلَّىٰٓ"

        assertEquals("عَبَسَ وَتَوَلَّىٰٓ", removeBismillahIfNeeded(verse, 80, 1))
        assertEquals(verse, removeBismillahIfNeeded(verse, 1, 1))
        assertEquals(verse, removeBismillahIfNeeded(verse, 9, 1))
        assertEquals(verse, removeBismillahIfNeeded(verse, 80, 2))
    }

    @Test fun keepsBismillahMentionedInsideTheVerse() {
        val verse = "عَبَسَ وَتَوَلَّىٰ بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ"

        assertEquals(verse, removeBismillahIfNeeded(verse, 80, 1))
    }

    @Test fun removesOpeningWithQuranicMarksAndTatweelWithoutChangingVerse() {
        val verse = "بِسْمِ ٱللَّهِ ٱلرَّحْمَـٰنِ ٱلرَّحِيمِ ۞ عَبَسَ وَتَوَلَّىٰ"

        assertEquals("۞ عَبَسَ وَتَوَلَّىٰ", removeBismillahIfNeeded(verse, 80, 1))
    }

    @Test fun doesNotRemoveAnIncompleteOpening() {
        val verse = "بِسْمِ اللهِ عَبَسَ"

        assertEquals(verse, removeBismillahIfNeeded(verse, 80, 1))
    }
}
