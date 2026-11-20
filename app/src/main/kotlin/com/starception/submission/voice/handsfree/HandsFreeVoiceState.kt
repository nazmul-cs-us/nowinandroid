package com.starception.submission.voice.handsfree

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class HandsFreePhase { OFF, PREPARING, WAKE_WORD, COMMAND, PROCESSING, PAUSED, ERROR }
internal data class HandsFreeStatus(val phase: HandsFreePhase = HandsFreePhase.OFF, val message: String = "Off") {
    val enabled get() = phase != HandsFreePhase.OFF && phase != HandsFreePhase.ERROR
}

/** Deliberately in-memory: reboot, process death, or force-stop never re-enables a microphone. */
internal object HandsFreeVoiceState {
    private val mutable = MutableStateFlow(HandsFreeStatus())
    val status = mutable.asStateFlow()
    @Volatile var requested = false
        private set
    @Volatile var sessionId = 0L
        private set

    fun isCurrent(id: Long): Boolean = requested && sessionId == id

    fun hasPermissions(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
            (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun enable(context: Context) {
        if (requested) return
        if (!hasPermissions(context)) { end("Microphone and notification permissions are required."); return }
        requested = true
        sessionId++
        update(HandsFreePhase.PREPARING, "Preparing offline voice models…")
        try {
            ContextCompat.startForegroundService(context, Intent(context, HandsFreeVoiceService::class.java).setAction(HandsFreeVoiceService.START))
        } catch (_: RuntimeException) {
            end("Open Deenly and enable hands-free mode while the app is visible.")
        }
    }

    fun disable(context: Context) {
        end()
        context.stopService(Intent(context, HandsFreeVoiceService::class.java))
    }

    fun update(phase: HandsFreePhase, message: String, expectedSession: Long = sessionId) {
        if (isCurrent(expectedSession)) mutable.value = HandsFreeStatus(phase, message)
    }

    fun end(error: String? = null, expectedSession: Long? = null) {
        if (expectedSession != null && expectedSession != sessionId) return
        requested = false
        sessionId++
        mutable.value = if (error == null) HandsFreeStatus() else HandsFreeStatus(HandsFreePhase.ERROR, error)
    }
}
