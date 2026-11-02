package com.starception.submission.settings.components

import android.app.Application
import android.content.Context
import android.media.MediaPlayer
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ReciterPreviewPlayerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var preview: ReciterPreviewPlayer
    private val players = mutableListOf<MediaPlayer>()
    private val url = "https://example.org/001.mp3"

    @Before fun setUp() {
        ShadowMediaPlayer.setCreateListener { player, _ -> players += player }
        ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(url), ShadowMediaPlayer.MediaInfo(60_000, 100))
        preview = ReciterPreviewPlayer(context)
    }

    @After fun tearDown() {
        preview.stop()
    }

    @Test fun previewStopsAfterTwentySecondsWithoutSelectingTheVoice() {
        val preferences = context.getSharedPreferences("quran_reciter", Context.MODE_PRIVATE)
        preferences.edit().putString("selected_id", "original").commit()
        preview.toggle("candidate", url) {}
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        assertFalse(preview.loading)
        assertTrue(players.single().isPlaying)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20))
        assertNull(preview.activeId)
        assertEquals(ShadowMediaPlayer.State.END, shadowOf(players.single()).state)
        assertEquals("original", preferences.getString("selected_id", null))
    }

    @Test fun switchingVoicesReleasesThePreviousPreview() {
        preview.toggle("first", url) {}
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        preview.toggle("second", url) {}
        assertEquals(ShadowMediaPlayer.State.END, shadowOf(players.first()).state)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        assertEquals("second", preview.activeId)
        assertTrue(players.last().isPlaying)
    }

    @Test fun stoppingWhilePreparingPreventsPlaybackStartingLater() {
        preview.toggle("candidate", url) {}
        preview.stop()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30))
        assertNull(preview.activeId)
        assertEquals(ShadowMediaPlayer.State.END, shadowOf(players.single()).state)
    }

    @Test fun stalledPreparationTimesOutAndCanBeRetried() {
        ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(url), ShadowMediaPlayer.MediaInfo(60_000, -1))
        preview.toggle("candidate", url) {}
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(15))
        assertNull(preview.activeId)
        assertNotNull(preview.error)
        ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(url), ShadowMediaPlayer.MediaInfo(60_000, 100))
        preview.toggle("candidate", url) {}
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        assertNull(preview.error)
        assertTrue(players.last().isPlaying)
    }
}
