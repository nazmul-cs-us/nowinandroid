package com.starception.submission.ui

import android.content.Context
import com.starception.submission.core.designsystem.component.DeenlySplashReminder

/** Complete source-authored ayahs from the selected local translation; no fixed reference list. */
internal fun translatedSplashReminder(context: Context, code: String, previousReference: String?): DeenlySplashReminder? {
    if (code == "en") return null
    return shortQuranReminderPool(context, code).firstOrNull { it.reference != previousReference }
}
