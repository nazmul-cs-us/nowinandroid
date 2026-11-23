package com.starception.submission.voice.handsfree

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.starception.submission.MainActivity
import com.starception.submission.R
import com.starception.submission.download.AssetRepository
import com.starception.submission.family.FamilyAudioSession
import com.starception.submission.feature.search.VoiceSearchService
import com.starception.submission.feature.search.WhisperVoiceService
import com.starception.submission.ui.search.SearchPrefillBus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.resume

/** A user-started microphone FGS, never started from boot, a receiver, or assistant binding. */
@AndroidEntryPoint
class HandsFreeVoiceService : Service() {
    @Inject lateinit var assets: AssetRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: Job? = null
    private var wake: DeenlyWakeWordDetector? = null
    private var whisper: WhisperVoiceService? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var sessionId = -1L

    companion object {
        internal const val START = "com.starception.submission.voice.HANDS_FREE_START"
        internal const val STOP = "com.starception.submission.voice.HANDS_FREE_STOP"
        internal const val CHANNEL = "hands_free_voice"
        private const val NOTIFICATION = 2037
        private const val RECORDING = "hands_free_command.wav"
        private const val MAX_SESSION_MS = 12 * 60 * 60 * 1000L
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { HandsFreeVoiceState.end(); stopSelf(); return START_NOT_STICKY }
        if (intent?.action != START || !HandsFreeVoiceState.requested || !HandsFreeVoiceState.hasPermissions(this)) {
            if (HandsFreeVoiceState.requested) HandsFreeVoiceState.end("Microphone and visible notification permissions are required.")
            stopSelf()
            return START_NOT_STICKY
        }
        if (session != null) {
            if (session?.isActive == true && HandsFreeVoiceState.isCurrent(sessionId)) return START_NOT_STICKY
            HandsFreeVoiceState.end("The previous microphone session is stopping. Please enable it again in a moment.")
            wake?.stop()
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        val token = HandsFreeVoiceState.sessionId
        sessionId = token
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Hands-free voice", NotificationManager.IMPORTANCE_LOW))
        if (manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) {
            HandsFreeVoiceState.end("Enable the Hands-free voice notification channel before listening.")
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } catch (_: RuntimeException) {
            HandsFreeVoiceState.end("Open Deenly and enable hands-free mode while the app is visible.")
            stopSelf()
            return START_NOT_STICKY
        }
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
            "Deenly:HandsFreeVoice").apply { setReferenceCounted(false); acquire(MAX_SESSION_MS) }
        session = scope.launch {
            try {
                withTimeout(MAX_SESSION_MS) { listen(token) }
                HandsFreeVoiceState.end(expectedSession = token)
            } catch (_: TimeoutCancellationException) {
                HandsFreeVoiceState.end("The 12-hour hands-free session ended. Enable it again when needed.", token)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { HandsFreeVoiceState.end(error.message ?: "Hands-free recognition stopped. Try enabling it again.", token) }
            finally { stopSelfResult(startId) }
        }
        // Revoke access promptly if the user disables microphone/notification permissions.
        scope.launch {
            while (isActive && HandsFreeVoiceState.isCurrent(token)) {
                delay(1000)
                if (!HandsFreeVoiceState.hasPermissions(this@HandsFreeVoiceService) ||
                    manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) {
                    HandsFreeVoiceState.end("Hands-free mode stopped because its microphone or notification permission changed.", token)
                    wake?.stop()
                    stopSelf()
                    break
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun busy(): Boolean = FamilyAudioSession.isActive || SearchPrefillBus.listening.value ||
        SearchPrefillBus.processing.value || getSystemService(AudioManager::class.java).mode != AudioManager.MODE_NORMAL

    private suspend fun listen(token: Long) {
        val detector = DeenlyWakeWordDetector(this, assets).also { wake = it }
        val recognizer = WhisperVoiceService(applicationContext, RECORDING, logTranscripts = false, useCacheDirectory = true).also { whisper = it }
        check(withContext(Dispatchers.IO) { recognizer.isModelAvailable() }) {
            "Download Full Transcription (Whisper) in Voice Recognition settings."
        }
        detector.prepare()
        check(suspendCancellableCoroutine { continuation ->
            recognizer.initialize { if (continuation.isActive) continuation.resume(it) }
        }) { "Could not load the offline transcription model. Check Voice Recognition settings." }
        scope.launch {
            combine(FamilyAudioSession.state, SearchPrefillBus.listening, SearchPrefillBus.processing) { call, recording, processing ->
                call || recording || processing
            }.collect { if (it) { detector.stop(); recognizer.cancel(reportEmptyResult = true) } }
        }
        scope.launch {
            recognizer.isTranscribing.collect {
                if (it && HandsFreeVoiceState.isCurrent(token) && !busy()) publish(HandsFreePhase.PROCESSING, "Recognizing the command on-device…", token)
            }
        }
        while (currentCoroutineContext().isActive && HandsFreeVoiceState.isCurrent(token)) {
            if (busy()) {
                publish(HandsFreePhase.PAUSED, "Paused while another voice feature or call uses the microphone", token)
                delay(500)
                continue
            }
            publish(HandsFreePhase.WAKE_WORD, "Say “Hey Deenly”", token)
            if (!detector.awaitWakeWord { !HandsFreeVoiceState.isCurrent(token) || busy() }) { delay(500); continue }
            if (!HandsFreeVoiceState.isCurrent(token) || busy()) continue
            publish(HandsFreePhase.COMMAND, "Listening for a Quran playback command…", token)
            val tone = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 40) }.getOrNull()
            try { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 100); delay(150) }
            finally { tone?.release() }
            try {
                val result = withTimeoutOrNull(30_000) {
                    suspendCancellableCoroutine { continuation ->
                        continuation.invokeOnCancellation { recognizer.cancel(reportEmptyResult = false) }
                        recognizer.startListening { if (continuation.isActive) continuation.resume(it) }
                    }
                }
                if (HandsFreeVoiceState.isCurrent(token) && !busy()) {
                    val message = when (result) {
                        is VoiceSearchService.VoiceSearchResult.Success -> executeVoicePlaybackCommand(this@HandsFreeVoiceService, result.text)
                            ?: "Try “play Bakarah”, “pause”, “resume”, “next surah”, or “stop”."
                        else -> "Didn’t catch that. Say “Hey Deenly” to try again."
                    }
                    publish(HandsFreePhase.WAKE_WORD, message, token)
                    delay(2000)
                }
            } finally {
                withContext(NonCancellable) { recognizer.cancelAndAwait() }
                // Only the bounded command is captured; waiting/wake audio is never written.
                File(cacheDir, RECORDING).delete()
            }
        }
    }

    private fun publish(phase: HandsFreePhase, message: String, token: Long) {
        if (!HandsFreeVoiceState.isCurrent(token)) return
        HandsFreeVoiceState.update(phase, message, token)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification())
    }

    private fun notification() = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_notification_logo)
        .setContentTitle("Deenly hands-free microphone")
        .setContentText(HandsFreeVoiceState.status.value.message)
        .setStyle(NotificationCompat.BigTextStyle().bigText(HandsFreeVoiceState.status.value.message + " · Processed on-device"))
        .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE))
        .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
        // Android 14+ can dismiss ongoing notifications; dismissal must stop this microphone.
        .setDeleteIntent(PendingIntent.getService(this, 1,
            Intent(this, HandsFreeVoiceService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE))
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .addAction(0, "Turn off microphone", PendingIntent.getService(this, 1,
            Intent(this, HandsFreeVoiceService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE))
        .build()

    override fun onDestroy() {
        if (HandsFreeVoiceState.isCurrent(sessionId)) HandsFreeVoiceState.end(expectedSession = sessionId)
        wake?.stop()
        val running = session
        scope.cancel()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            running?.join()
            withContext(Dispatchers.IO) { wake?.release() }
            whisper?.release()
            File(cacheDir, RECORDING).delete()
            cancel()
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
