package com.starception.submission.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingNavigationScrollTest {
    @Test fun fifthDestinationScrollsIntoFourItemViewport() {
        assertEquals(70, floatingNavigationScrollTarget(
            selectedIndex = 4,
            itemWidthPx = 70,
            viewportWidthPx = 280,
            currentScrollPx = 0,
        ))
        assertEquals(70, floatingNavigationScrollTarget(
            selectedIndex = 3,
            itemWidthPx = 70,
            viewportWidthPx = 280,
            currentScrollPx = 70,
        ))
        assertEquals(0, floatingNavigationScrollTarget(
            selectedIndex = 0,
            itemWidthPx = 70,
            viewportWidthPx = 280,
            currentScrollPx = 70,
        ))
    }
}
