package com.starception.submission.voice.handsfree

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.starception.submission.family.FamilyAudioSession
import com.starception.submission.feature.quran.QuranPlaybackService
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class VoicePlaybackCommandsTest {
    @After fun clearCall() { FamilyAudioSession.setActive(false) }

    @Test fun explicitVoicePlaybackUsesTheExistingQuranPlayerAndCorrectSurah() {
        FamilyAudioSession.setActive(false)
        val context = ApplicationProvider.getApplicationContext<Application>()
        assertTrue(executeVoicePlaybackCommand(context, "play Bakarah").orEmpty().contains("Al-Baqarah"))
        val intent = requireNotNull(shadowOf(context).nextStartedService)
        assertEquals(QuranPlaybackService::class.java.name, intent.component?.className)
        assertEquals(QuranPlaybackService.ACTION_PLAY_SURAH, intent.action)
        assertEquals(2, intent.getIntExtra(QuranPlaybackService.EXTRA_SURAH_NUMBER, 0))
    }

    @Test fun ordinaryQueriesRemainSearchesAndDoNotStartPlayback() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        assertNull(executeVoicePlaybackCommand(context, "find verses about mercy"))
        assertNull(shadowOf(context).nextStartedService)
    }

    @Test fun aVoiceResultCannotInterruptAnActiveFamilyCall() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        FamilyAudioSession.setActive(true)
        assertTrue(executeVoicePlaybackCommand(context, "play Bakarah").orEmpty().contains("call"))
        assertNull(shadowOf(context).nextStartedService)
    }
}
