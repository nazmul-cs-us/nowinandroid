package com.starception.submission.shared.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedTopicCatalogTest {
    @Test
    fun catalogMatchesAndroidTopicIds() {
        assertEquals(
            setOf(7, 8, 11) + (21..37),
            SharedTopics.mapTo(mutableSetOf()) { it.id },
        )
    }

    @Test
    fun everyFortressTopicHasChapterNews() {
        assertEquals((21..37).toSet(), fortressChaptersByTopic.keys)
        assertTrue(fortressChaptersByTopic.values.all { it.isNotEmpty() })
    }
}
