package com.starception.submission.shared.assets

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class ContentDownloadBusTest {
    @Test fun reciterProgressIsVisibleBeforeFirstByteAndClearsIndependently() = runBlocking<Unit> {
        try {
            ContentDownloadBus.publish(null)
            ContentDownloadBus.publishReciter("test-a", ContentDownloadStatus("Reciter A", 0f))
            val starting = withTimeout(5000) { ContentDownloadBus.state.first { it?.label == "Reciter A" } }
            assertEquals(0.001f, starting?.progress)
            ContentDownloadBus.publish(ContentDownloadStatus("Content", 0.5f))
            withTimeout(5000) { ContentDownloadBus.state.first { it?.label == "Content" } }
            ContentDownloadBus.publishReciter("test-a", null)
            assertEquals("Content", ContentDownloadBus.state.value?.label)
            ContentDownloadBus.publish(null)
            withTimeout(5000) { ContentDownloadBus.state.first { it == null } }
        } finally {
            ContentDownloadBus.publish(null)
            ContentDownloadBus.publishReciter("test-a", null)
        }
    }

    @Test fun contentCompletionRestoresOngoingReciterProgress() = runBlocking<Unit> {
        try {
            ContentDownloadBus.publish(ContentDownloadStatus("Content", 0.5f))
            ContentDownloadBus.publishReciter("test-b", ContentDownloadStatus("Reciter B", 0.3f))
            withTimeout(5000) { ContentDownloadBus.state.first { it?.label == "Content" } }
            ContentDownloadBus.publish(null)
            val restored = withTimeout(5000) { ContentDownloadBus.state.first { it?.label == "Reciter B" } }
            assertEquals(0.3f, restored?.progress)
        } finally {
            ContentDownloadBus.publish(null)
            ContentDownloadBus.publishReciter("test-b", null)
        }
    }
}
