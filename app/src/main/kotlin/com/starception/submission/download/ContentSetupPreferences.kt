package com.starception.submission.download

import android.content.Context

/** Persists whether the one-time content setup has already been completed. */
internal class ContentSetupPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    val isComplete: Boolean
        get() = preferences.getBoolean(KEY_IS_COMPLETE, false)

    fun markComplete() {
        preferences.edit().putBoolean(KEY_IS_COMPLETE, true).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "content_setup_preferences"
        const val KEY_IS_COMPLETE = "initial_setup_complete"
    }
}

internal fun shouldShowContentSetup(
    screenState: DownloadScreenState,
    isSetupComplete: Boolean,
): Boolean = screenState is DownloadScreenState.NeedsDownload && !isSetupComplete
