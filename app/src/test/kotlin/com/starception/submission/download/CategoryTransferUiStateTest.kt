package com.starception.submission.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryTransferUiStateTest {
    private val saved = CategoryDownloadState("audio", "Audio", "", 1000, 200, 2, false, false, progress = .2f)

    @Test fun liveBytesAppearBeforeTheFileHasFinishedSaving() {
        val state = saved.withTransfer(AssetDownloadManager.DownloadState.Downloading(.65f, 650, 1000))
        assertTrue(state.isDownloading)
        assertFalse(state.isComplete)
        assertEquals(650L, state.downloadedSize)
        assertEquals(.65f, state.progress)
    }

    @Test fun failureKeepsSavedBytesAndSuccessfulRefreshClearsTheError() {
        val failed = saved.withTransfer(AssetDownloadManager.DownloadState.Failed("Offline"))
        assertFalse(failed.isDownloading)
        assertEquals(200L, failed.downloadedSize)
        assertEquals("Offline", failed.error)
        assertNull(saved.copy(isComplete = true).withTransfer(AssetDownloadManager.DownloadState.Failed("Old error")).error)
    }
}
