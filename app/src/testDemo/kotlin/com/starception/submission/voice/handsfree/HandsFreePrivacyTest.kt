package com.starception.submission.voice.handsfree

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.starception.submission.feature.quran.QuranPlaybackService
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class HandsFreePrivacyTest {
    @After fun turnOff() { HandsFreeVoiceState.disable(ApplicationProvider.getApplicationContext()) }

    @Test fun modeIsOffByDefaultAndCannotStartWithoutMicrophonePermission() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        HandsFreeVoiceState.end()
        assertFalse(HandsFreeVoiceState.status.value.enabled)
        shadowOf(context).denyPermissions(Manifest.permission.RECORD_AUDIO)
        HandsFreeVoiceState.enable(context)
        assertFalse(HandsFreeVoiceState.requested)
        assertFalse(HandsFreeVoiceState.status.value.enabled)
        assertEquals(HandsFreePhase.ERROR, HandsFreeVoiceState.status.value.phase)
        assertNull(shadowOf(context).nextStartedService)
    }

    @Test fun aSystemRestartOrUnconsentedStartNeverBeginsListening() {
        HandsFreeVoiceState.end()
        val service = Robolectric.buildService(HandsFreeVoiceService::class.java).get()
        assertEquals(android.app.Service.START_NOT_STICKY, service.onStartCommand(null, 0, 1))
        assertEquals(android.app.Service.START_NOT_STICKY,
            service.onStartCommand(Intent().setAction(HandsFreeVoiceService.START), 0, 2))
        assertFalse(HandsFreeVoiceState.requested)
        assertFalse(HandsFreeVoiceState.status.value.enabled)
        service.onDestroy()
    }

    @Test fun notificationAccessIsRequiredEvenWhenMicrophoneAccessWasGranted() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        HandsFreeVoiceState.end()
        shadowOf(context).grantPermissions(Manifest.permission.RECORD_AUDIO)
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        HandsFreeVoiceState.enable(context)
        assertFalse(HandsFreeVoiceState.requested)
        assertNull(shadowOf(context).nextStartedService)
    }

    @Test fun mediaControlsDoNotCreateAPlayerWhenNothingIsPlaying() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        listOf(QuranPlaybackService.ACTION_PAUSE, QuranPlaybackService.ACTION_RESUME,
            QuranPlaybackService.ACTION_STOP, QuranPlaybackService.ACTION_NEXT, QuranPlaybackService.ACTION_PREVIOUS)
            .forEach { assertFalse(QuranPlaybackService.handleHandsFreeControl(it)) }
        assertNull(shadowOf(context).nextStartedService)
    }

    @Test fun lateCallbacksCannotRestoreListeningAfterTheUserTurnsItOff() {
        HandsFreeVoiceState.end()
        HandsFreeVoiceState.update(HandsFreePhase.COMMAND, "Late callback")
        assertEquals(HandsFreePhase.OFF, HandsFreeVoiceState.status.value.phase)
        HandsFreeVoiceState.end("Permission revoked")
        HandsFreeVoiceState.update(HandsFreePhase.WAKE_WORD, "Late ready callback")
        assertEquals(HandsFreePhase.ERROR, HandsFreeVoiceState.status.value.phase)
        assertFalse(HandsFreeVoiceState.status.value.enabled)
    }

    @Test fun resultsFromAnOlderSessionCannotAffectANewUserEnabledSession() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(context).grantPermissions(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(context.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(true)
        HandsFreeVoiceState.end()
        HandsFreeVoiceState.enable(context)
        val old = HandsFreeVoiceState.sessionId
        assertTrue(HandsFreeVoiceState.isCurrent(old))
        HandsFreeVoiceState.disable(context)
        HandsFreeVoiceState.enable(context)
        val current = HandsFreeVoiceState.sessionId
        assertTrue(HandsFreeVoiceState.isCurrent(current))
        assertFalse(HandsFreeVoiceState.isCurrent(old))
        HandsFreeVoiceState.update(HandsFreePhase.COMMAND, "Old transcript", old)
        HandsFreeVoiceState.end("Old callback", old)
        assertEquals(HandsFreePhase.PREPARING, HandsFreeVoiceState.status.value.phase)
        assertTrue(HandsFreeVoiceState.isCurrent(current))
    }
}
