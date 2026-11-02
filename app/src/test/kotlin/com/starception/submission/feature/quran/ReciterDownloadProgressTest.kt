package com.starception.submission.feature.quran

import org.junit.Test
import kotlin.test.assertEquals

class ReciterDownloadProgressTest {
    @Test fun progressIncludesTheFileCurrentlyDownloading() {
        val status = ReciterDownloadManager.DownloadStatus(
            downloadedSurahs = 2, totalSurahs = 10, downloading = true, currentFileProgress = 0.5f,
        )
        assertEquals(0.25f, status.progress)
        assertEquals(0.3f, status.copy(downloadedSurahs = 3, currentFileProgress = 0f).progress)
    }

    @Test fun anEmptyOrCompletedLibraryHasValidProgress() {
        assertEquals(0f, ReciterDownloadManager.DownloadStatus().progress)
        assertEquals(1f, ReciterDownloadManager.DownloadStatus(downloadedSurahs = 114, totalSurahs = 114).progress)
    }
}
