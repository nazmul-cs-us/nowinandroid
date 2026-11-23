package com.starception.submission.voice.assistant

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.service.voice.VoiceInteractionService
import androidx.test.core.app.ApplicationProvider
import com.starception.submission.MainActivity
import com.starception.submission.voice.handsfree.HandsFreeVoiceService
import com.starception.submission.voice.handsfree.HandsFreeVoiceState
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class AssistantEntryTest {
    @After fun clearRequest() { AssistantEntryBus.pending.value?.let(AssistantEntryBus::consumed) }

    @Test fun assistantRequestsSurviveColdStartAndAreConsumedOnlyOnce() {
        val intent = Intent(Intent.ACTION_ASSIST)
        AssistantEntryBus.accept(intent)
        val request = requireNotNull(AssistantEntryBus.pending.value)
        assertNull(intent.action)
        assertFalse(request.routed)
        AssistantEntryBus.routed(request)
        val ready = requireNotNull(AssistantEntryBus.pending.value)
        assertTrue(ready.routed)
        assertTrue(AssistantEntryBus.consumed(ready))
        assertFalse(AssistantEntryBus.consumed(ready))
        AssistantEntryBus.accept(intent)
        assertNull(AssistantEntryBus.pending.value)
    }

    @Test fun anOldConsumerCannotDiscardANewerAssistantRequest() {
        AssistantEntryBus.accept(Intent(Intent.ACTION_ASSIST))
        val old = requireNotNull(AssistantEntryBus.pending.value)
        AssistantEntryBus.accept(Intent(Intent.ACTION_ASSIST))
        assertFalse(AssistantEntryBus.consumed(old))
        assertNotNull(AssistantEntryBus.pending.value)
    }

    @Test fun assistantMetadataIsValidAndBindingDoesNotEnableHandsFreeMode() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        HandsFreeVoiceState.end()
        val pm = context.packageManager
        val service = pm.getServiceInfo(ComponentName(context, DeenlyVoiceInteractionService::class.java), PackageManager.GET_META_DATA)
        assertEquals(Manifest.permission.BIND_VOICE_INTERACTION, service.permission)
        assertTrue(service.exported)
        assertTrue(service.metaData.getInt(VoiceInteractionService.SERVICE_META_DATA) != 0)
        // The platform parser is hidden from the SDK, but available in Robolectric's framework.
        val type = Class.forName("android.service.voice.VoiceInteractionServiceInfo")
        val info = type.getConstructor(PackageManager::class.java, ServiceInfo::class.java).newInstance(pm, service)
        assertNull(type.getMethod("getParseError").invoke(info))
        assertEquals(DeenlyRecognitionService::class.java.name, type.getMethod("getRecognitionService").invoke(info))
        val mic = pm.getServiceInfo(ComponentName(context, HandsFreeVoiceService::class.java), 0)
        assertFalse(mic.exported)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE, mic.foregroundServiceType)
        assertEquals("Closing the UI must not stop an explicitly enabled listening session", 0,
            mic.flags and ServiceInfo.FLAG_STOP_WITH_TASK)
        val recognition = pm.getServiceInfo(ComponentName(context, DeenlyRecognitionService::class.java), 0)
        assertEquals("android.permission.BIND_SPEECH_RECOGNITION_SERVICE", recognition.permission)
        assertFalse(HandsFreeVoiceState.status.value.enabled)
        assertNotNull(pm.resolveActivity(Intent(Intent.ACTION_ASSIST).setPackage(context.packageName), 0))
    }
}
