package com.starception.submission.download

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ContentSetupPreferencesTest {

    @Test
    fun completedSetupIsAvailableToANewPreferencesInstance() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val firstInstance = ContentSetupPreferences(context)

        assertFalse(firstInstance.isComplete)
        firstInstance.markComplete()

        assertTrue(ContentSetupPreferences(context).isComplete)
    }

    @Test
    fun missingContentShowsSetupOnlyUntilSetupIsComplete() {
        val needsDownload = DownloadScreenState.NeedsDownload(
            categories = emptyList(),
            totalRequiredSize = 1L,
            totalOptionalSize = 0L,
            requiredComplete = false,
            overallProgress = 0f,
            isDownloading = false,
        )

        assertTrue(shouldShowContentSetup(needsDownload, isSetupComplete = false))
        assertFalse(shouldShowContentSetup(needsDownload, isSetupComplete = true))
        assertFalse(
            shouldShowContentSetup(
                DownloadScreenState.AllReady,
                isSetupComplete = false,
            ),
        )
    }
}
