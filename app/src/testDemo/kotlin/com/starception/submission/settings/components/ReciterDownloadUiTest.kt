package com.starception.submission.settings.components

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.github.takahirom.roborazzi.captureRoboImage
import com.starception.submission.download.CategoryDownloadState
import com.starception.submission.feature.quran.ReciterChoiceRow
import com.starception.submission.feature.quran.ReciterCatalog
import com.starception.submission.feature.quran.ReciterDownloadManager.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w320dp-h640dp", sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReciterDownloadUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun cachedPickerRemainsSelectableInAShortViewportWhileOffline() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val connectivity = context.getSystemService(android.net.ConnectivityManager::class.java)
        org.robolectric.Shadows.shadowOf(connectivity).setActiveNetworkInfo(null)
        context.getSharedPreferences("quran_reciter", Context.MODE_PRIVATE).edit().clear()
            .putString("catalog_json", """{"reciters":[{"id":1,"name":"Abdul Basit Abdul Samad","moshaf":[
                {"id":2,"name":"Hafs - Murattal","server":"https://example.org/audio/","surah_list":"1,2"}
            ]}]}""").commit()
        var selections = 0
        val height = mutableStateOf(480.dp)
        compose.setContent {
            MaterialTheme {
                Surface {
                    ReciterStorageSection(initiallyExpanded = true, manageDownloads = false,
                        onReciterSelected = { selections++ }, modifier = Modifier.heightIn(max = height.value))
                }
            }
        }
        compose.waitUntil(5_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Loading reciters"))
                .fetchSemanticsNodes().isEmpty()
        }
        File("build/outputs/reciter-ui").mkdirs()
        compose.onRoot().captureRoboImage("build/outputs/reciter-ui/reciter-picker.png")
        compose.runOnIdle { height.value = 220.dp }
        compose.onNodeWithTag("reciter-list").performScrollToNode(hasText("Abdul Basit Abdul Samad"))
        compose.onNodeWithText("Search reciters").assertIsDisplayed()
        compose.onNodeWithText("Abdul Basit Abdul Samad").assertIsDisplayed().performClick()
        assertEquals(1, selections)
        assertEquals("1-2", com.starception.submission.feature.quran.ReciterSelectionStore(context).current().id)
    }

    @Test fun storageOpensADedicatedSheetWithCompactIndependentDownloadControls() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val connectivity = context.getSystemService(android.net.ConnectivityManager::class.java)
        org.robolectric.Shadows.shadowOf(connectivity).setActiveNetworkInfo(null)
        context.getSharedPreferences("quran_reciter", Context.MODE_PRIVATE).edit().clear()
            .putString("catalog_json", """{"reciters":[
                {"id":54,"name":"Abdulrahman Alsudaes","moshaf":[{"id":54,"name":"Hafs - Murattal","server":"https://example.org/sudais/","surah_list":"1,2"}]},
                {"id":1,"name":"Abdul Basit Abdul Samad","moshaf":[{"id":2,"name":"Hafs - Murattal","server":"https://example.org/basit/","surah_list":"1,2"}]},
                {"id":3,"name":"Maher Al-Muaiqly","moshaf":[{"id":3,"name":"Hafs - Murattal","server":"https://example.org/maher/","surah_list":"1,2"}]}
            ]}""").commit()
        compose.setContent { MaterialTheme { Surface { ReciterStorageSection() } } }
        compose.onNodeWithTag("reciter-list").assertDoesNotExist()
        compose.onNodeWithText("Quran recitations").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Abdul Rahman Al-Sudais")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Reciters & offline audio").assertIsDisplayed()
        compose.onNodeWithContentDescription("Preview Abdul Rahman Al-Sudais")
            .assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithContentDescription("Manage downloads for Abdul Rahman Al-Sudais")
            .assertHeightIsAtLeast(48.dp).performClick()
        // Opening download details must not change the selected voice.
        assertEquals(null, com.starception.submission.feature.quran.ReciterSelectionStore(context).current().id)
        compose.onNodeWithText("0 of 2 surahs saved").assertIsDisplayed()
        compose.onNodeWithContentDescription("Hide downloads for Abdul Rahman Al-Sudais").performClick()
        compose.onNodeWithText("0 of 2 surahs saved").assertDoesNotExist()
        File("build/outputs/reciter-ui").mkdirs()
        compose.onNode(isDialog()).captureRoboImage("build/outputs/reciter-ui/reciter-library-sheet.png")
        compose.onNodeWithContentDescription("Close reciters").performClick()
        compose.onNodeWithTag("reciter-list").assertDoesNotExist()
    }

    @Test fun partialDownloadCanCancelRetryAndRemoveWithoutLosingSavedCount() {
        val status = mutableStateOf(DownloadStatus(2, 114, 2_000_000, downloading = true))
        var cancelled = 0
        var retried = 0
        var removed = 0
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1.6f)) {
                    Surface(Modifier.fillMaxSize()) {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            ReciterOfflineActions(status.value, { retried++ }, { cancelled++ }, { removed++ })
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Remove audio").assertIsNotEnabled()
        compose.onNodeWithText("Cancel download").performScrollTo().performClick()
        assertEquals(1, cancelled)
        compose.runOnIdle { status.value = status.value.copy(downloading = false, error = "Network interrupted") }
        compose.onNodeWithText("2 of 114 surahs saved").assertIsDisplayed()
        compose.onNodeWithText("Retry download").performScrollTo().performClick()
        compose.onNodeWithText("Remove audio").performScrollTo().performClick()
        assertEquals(1, retried)
        assertEquals(1, removed)
        File("build/outputs/reciter-ui").mkdirs()
        compose.onRoot().captureRoboImage("build/outputs/reciter-ui/offline-retry-large-text.png")
    }

    @Test fun storagePreviewAndDownloadControlsDoNotSelectTheReciter() {
        val playing = mutableStateOf(false)
        var selections = 0
        var downloadDetails = 0
        val reciter = ReciterCatalog.Reciter("54-54", "Abdul Rahman Al-Sudais",
            "https://example.org/", setOf(1, 2), "Hafs")
        compose.setContent {
            MaterialTheme {
                ReciterLibraryRow(reciter, DownloadStatus(totalSurahs = 2), false, false,
                    onSelect = { selections++ },
                    onToggleDownloads = { downloadDetails++ },
                    onDownload = {}, onCancel = {}, onRemove = {},
                    onPreview = { playing.value = !playing.value },
                    previewing = playing.value, previewLoading = false)
            }
        }
        compose.onNodeWithContentDescription("Preview Abdul Rahman Al-Sudais")
            .assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithContentDescription("Stop preview of Abdul Rahman Al-Sudais")
            .assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Manage downloads for Abdul Rahman Al-Sudais").performClick()
        assertEquals(0, selections)
        assertEquals(1, downloadDetails)
        compose.onNodeWithText("Abdul Rahman Al-Sudais").performClick()
        assertEquals(1, selections)
    }

    @Test fun previewDoesNotSelectAndCompletedAudioHasNoDownloadAction() {
        var selections = 0
        var previews = 0
        compose.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    Column {
                        ReciterChoiceRow("Abdul Basit Abdul Samad", "Hafs · Murattal", true,
                            onSelect = { selections++ }, onPreview = { previews++ })
                        ReciterOfflineActions(DownloadStatus(114, 114, 90_000_000), {}, {}, {})
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("Preview Abdul Basit Abdul Samad").performClick()
        assertEquals(1, previews)
        assertEquals(0, selections)
        compose.onNodeWithText("Abdul Basit Abdul Samad").assertIsSelected().performClick()
        assertEquals(1, selections)
        compose.onNodeWithText("Available offline").assertIsDisplayed()
        compose.onNodeWithText("Download all").assertDoesNotExist()
        File("build/outputs/reciter-ui").mkdirs()
        compose.onRoot().captureRoboImage("build/outputs/reciter-ui/selected-reciter-dark.png")
    }

    @Test fun activeContentTransferCannotBeDownloadedAgainAndFailureOffersRetry() {
        val state = mutableStateOf(CategoryDownloadState("quran_audio", "Quran recitation", "", 1000, 250, 114,
            required = false, isComplete = false, isDownloading = true, progress = .25f))
        var downloads = 0
        compose.setContent {
            MaterialTheme {
                Surface { ContentCategoryRow(state.value, { downloads++ }, null) }
            }
        }
        compose.onNodeWithText("Downloading · 25%").assertIsDisplayed()
        compose.onNodeWithContentDescription("Download Quran recitation").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(isDownloading = false, error = "Connection lost") }
        compose.onNodeWithText("Retry download").performClick()
        assertEquals(1, downloads)
    }
}
