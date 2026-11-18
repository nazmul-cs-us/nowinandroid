package com.starception.submission.feature.surah

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.starception.submission.feature.quran.*
import com.starception.submission.media.GlobalMediaViewModel
import com.starception.submission.media.MediaSource
import com.starception.submission.services.ChapterRecitationState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class TafsirMediaBannerTest {
    @Test fun serviceUpdatesKeepTafsirBannerVisibleWhenPausedAndClearItWhenStopped() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val model = GlobalMediaViewModel(context, backgroundScope)
        val owner = SurahTafsirPlayback.owner
        val engine = object : TafsirSpeechPlayer {
            override val state = MutableStateFlow(TafsirSpeechState())
            override fun play(text: String, language: String) { state.value = TafsirSpeechState(TafsirSpeechStatus.PLAYING) }
            override fun pause() { state.value = TafsirSpeechState(TafsirSpeechStatus.PAUSED) }
            override fun resume() { state.value = TafsirSpeechState(TafsirSpeechStatus.PLAYING) }
            override fun stop() { state.value = TafsirSpeechState() }
            override fun release() = stop()
        }
        val entry = owner.attach(1, "ibn_kathir_bn", "Al-Fatihah", { engine }, { "Ayah $it" })
        try {
            entry.player.start(); runCurrent(); owner.detach(entry)
            ChapterRecitationState.publish(true, entry.title, owner.state.value!!.subtitle)
            assertEquals(MediaSource.Tafsir(1), model.controllerState.value.playback.source)
            assertTrue(model.controllerState.value.isVisible)
            assertTrue(model.controllerState.value.playback.subtitle.contains("Tafsir · Bengali"))
            // Auto-binding Quran records an earlier service intent in Robolectric.
            val app = shadowOf(context as Application)
            while (app.nextStartedService != null) { /* drain setup intents */ }
            model.pause()
            assertEquals("com.starception.submission.CHAPTER_TOGGLE", app.nextStartedService.action)
            entry.player.pause(); runCurrent()
            ChapterRecitationState.publish(false, entry.title, owner.state.value!!.subtitle)
            assertTrue(model.controllerState.value.isVisible)
            assertFalse(model.controllerState.value.playback.isPlaying)
            owner.stop(); ChapterRecitationState.markStopped()
            assertFalse(model.controllerState.value.isVisible)
        } finally {
            owner.stop(); ChapterRecitationState.markStopped(); model.cleanup()
            Dispatchers.resetMain()
        }
    }
}
