package com.starception.submission.voice.assistant

import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import com.starception.submission.MainActivity

/** Android binds this only after the user explicitly chooses Deenly as their assistant. */
class DeenlyVoiceInteractionService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
        // Deenly searches its own content; it does not need another app's screen or screenshot.
        setDisabledShowContext(VoiceInteractionSession.SHOW_WITH_ASSIST or VoiceInteractionSession.SHOW_WITH_SCREENSHOT)
    }
}

class DeenlyVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = object : VoiceInteractionSession(this) {
        override fun onShow(args: Bundle?, showFlags: Int) {
            super.onShow(args, showFlags)
            setUiEnabled(false)
            try {
                startAssistantActivity(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_ASSIST)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            } catch (_: RuntimeException) { finish() }
        }
        override fun onTaskFinished(intent: Intent?, taskId: Int) { super.onTaskFinished(intent, taskId); finish() }
    }
}
