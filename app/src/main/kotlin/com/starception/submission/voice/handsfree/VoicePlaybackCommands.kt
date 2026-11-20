package com.starception.submission.voice.handsfree

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import androidx.annotation.MainThread
import androidx.core.content.ContextCompat
import com.starception.submission.family.FamilyAudioSession
import com.starception.submission.feature.quran.QuranData
import com.starception.submission.feature.quran.QuranPlaybackService

/** Shared by foreground voice search/default-assistant entry and the wake-word service. */
@MainThread
internal fun executeVoicePlaybackCommand(context: Context, text: String): String? {
    val command = HandsFreeCommandParser.parse(text) ?: return null // Non-commands stay ordinary search queries.
    if (FamilyAudioSession.isActive || context.getSystemService(AudioManager::class.java).mode != AudioManager.MODE_NORMAL)
        return "Finish the call or other voice session before controlling Quran playback."
    return when (command) {
        is HandsFreeCommand.PlaySurah -> {
            val name = QuranData.surahs[command.number - 1].nameEnglish
            try {
                ContextCompat.startForegroundService(context, Intent(context, QuranPlaybackService::class.java)
                    .setAction(QuranPlaybackService.ACTION_PLAY_SURAH)
                    .putExtra(QuranPlaybackService.EXTRA_SURAH_NUMBER, command.number))
                "Preparing $name playback."
            } catch (_: RuntimeException) { "Open Deenly to start playback, then try again." }
        }
        else -> {
            val action = when (command) {
                HandsFreeCommand.Pause -> QuranPlaybackService.ACTION_PAUSE
                HandsFreeCommand.Resume -> QuranPlaybackService.ACTION_RESUME
                HandsFreeCommand.Stop -> QuranPlaybackService.ACTION_STOP
                HandsFreeCommand.Next -> QuranPlaybackService.ACTION_NEXT
                HandsFreeCommand.Previous -> QuranPlaybackService.ACTION_PREVIOUS
                else -> error("Unexpected playback command")
            }
            if (QuranPlaybackService.handleHandsFreeControl(action)) "Done."
            else "No Quran playback is active. Try “play Bakarah”."
        }
    }
}
