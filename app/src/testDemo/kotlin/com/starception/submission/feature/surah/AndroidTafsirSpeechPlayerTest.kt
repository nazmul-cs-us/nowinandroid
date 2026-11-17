package com.starception.submission.feature.surah

import android.app.Application
import android.content.Context
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.test.core.app.ApplicationProvider
import com.starception.submission.feature.quran.TafsirSpeechStatus
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowTextToSpeech
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class AndroidTafsirSpeechPlayerTest {
    @Test fun closingBeforeTheEngineInitializesDoesNotStartSpeechLater() {
        val player = AndroidTafsirSpeechPlayer(ApplicationProvider.getApplicationContext<Context>())
        player.play("Commentary", "en")
        val engine = ShadowTextToSpeech.getLastTextToSpeechInstance()
        val shadow = shadowOf(engine)
        player.release()
        shadow.onInitListener.onInit(TextToSpeech.SUCCESS)
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(shadow.lastSpokenText)
        assertTrue(shadow.isShutdown)
        assertEquals(TafsirSpeechStatus.IDLE, player.state.value.status)
    }
    @Test fun unavailableLanguageReportsAnErrorWithoutReadingInAnotherVoice() {
        val player = AndroidTafsirSpeechPlayer(ApplicationProvider.getApplicationContext<Context>())
        player.play("বাংলা তাফসীর", "bn")
        val shadow = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())
        shadow.onInitListener.onInit(TextToSpeech.SUCCESS)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(TafsirSpeechStatus.ERROR, player.state.value.status)
        assertTrue(player.state.value.error.orEmpty().contains("voice"))
        assertNull(shadow.lastSpokenText)
        player.release()
    }
    @Test fun longCommentaryPlaysAllChunksAndReturnsToIdleAtCompletion() {
        ShadowTextToSpeech.addLanguageAvailability(Locale.ENGLISH)
        val text = "Long commentary paragraph. ".repeat(400)
        val player = AndroidTafsirSpeechPlayer(ApplicationProvider.getApplicationContext<Context>())
        player.play(text, "en")
        val shadow = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())
        shadow.onInitListener.onInit(TextToSpeech.SUCCESS)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(text, shadow.spokenTextList.joinToString(""))
        assertTrue(shadow.spokenTextList.all { it.length <= TextToSpeech.getMaxSpeechInputLength() })
        assertEquals("en", shadow.currentLanguage.language)
        assertEquals(TafsirSpeechStatus.IDLE, player.state.value.status)
        player.release()
    }
}
