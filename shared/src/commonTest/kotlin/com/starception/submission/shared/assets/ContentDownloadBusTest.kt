package com.starception.submission.shared.assets

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class ContentDownloadBusTest {
    @Test fun finishingOneReciterDoesNotHideAnotherTransfer() = runBlocking<Unit> {
        try {
            ContentDownloadBus.publish(null)
            ContentDownloadBus.publishReciter("first", ContentDownloadStatus("First", 0.2f))
            ContentDownloadBus.publishReciter("second", ContentDownloadStatus("Second", 0.6f))
            withTimeout(5000) { ContentDownloadBus.state.first { it?.label == "Downloading recitations (2)" } }
            ContentDownloadBus.publishReciter("first", null)
            val remaining = withTimeout(5000) { ContentDownloadBus.state.first { it?.label == "Second" } }
            assertEquals(0.6f, remaining?.progress)
            ContentDownloadBus.publishReciter("second", null)
            withTimeout(5000) { ContentDownloadBus.state.first { it == null } }
        } finally {
            ContentDownloadBus.publishReciter("first", null)
            ContentDownloadBus.publishReciter("second", null)
        }
    }

    @Test fun reciterProgressIsVisibleBeforeFirstByteAndClearsIndependently() = runBlocking<Unit> {
        try {
            ContentDownloadBus.publish(null)
            ContentDownloadBus.publishReciter("test-a", ContentDownloadStatus("Reciter A", 0f))
            val starting = withTimeout(5000) { ContentDownloadBus.state.first { it?.label == "Reciter A" } }
            assertEquals(0.001f, starting?.progress)
            ContentDownloadBus.publish(ContentDownloadStatus("Content", 0.5f))
            withTimeout(5000) { ContentDownloadBus.state.first { it?.label?.contains("other downloads active") == true } }
            ContentDownloadBus.publishReciter("test-a", null)
            val remaining = withTimeout(5000) { ContentDownloadBus.state.first { it?.label == "Content" } }
            assertEquals("Content", remaining?.label)
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
            withTimeout(5000) { ContentDownloadBus.state.first { it?.label?.contains("other downloads active") == true } }
            ContentDownloadBus.publish(null)
            val restored = withTimeout(5000) { ContentDownloadBus.state.first { it?.label == "Reciter B" } }
            assertEquals(0.3f, restored?.progress)
        } finally {
            ContentDownloadBus.publish(null)
            ContentDownloadBus.publishReciter("test-b", null)
        }
    }
}
