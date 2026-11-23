package com.starception.submission.settings.components

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings

/** OEM pages vary by One UI version; always retain a public Settings fallback. */
internal fun samsungSideButtonSettingsIntents(): List<Intent> = listOf(
    Intent(Intent.ACTION_MAIN).setComponent(ComponentName("com.android.settings", "com.android.settings.Settings\$SideKeySettingsActivity")),
    Intent(Intent.ACTION_MAIN).setComponent(ComponentName("com.android.settings", "com.android.settings.Settings\$UsefulFeatureMainActivity")),
    Intent(Settings.ACTION_SETTINGS),
)

/** Opens the user's controls. It never writes button mappings or assistant defaults. */
internal fun openSamsungSideButtonSettings(launch: (Intent) -> Unit): Boolean {
    for (intent in samsungSideButtonSettingsIntents()) {
        try { launch(intent); return true }
        catch (_: ActivityNotFoundException) { /* Try the next supported settings page. */ }
        catch (_: SecurityException) { /* Some OEM settings activities are not exported. */ }
    }
    return false
}
