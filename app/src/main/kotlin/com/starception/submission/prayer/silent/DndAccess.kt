package com.starception.submission.prayer.silent

import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Open the system screen where the user grants Do-Not-Disturb ("notification policy")
 * access to this app.
 *
 * Android does NOT expose a public per-app deep-link for DND access (unlike, say, app
 * notification settings) — only the generic "Do Not Disturb access" list screen exists
 * (`ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS`). We attach `EXTRA_APP_PACKAGE` as a
 * best-effort hint so OEM builds that honor it can scroll to / highlight this app; on
 * others it's ignored and the plain list opens.
 *
 * A trip to Settings is unavoidable here: DND access is a "special access" permission with
 * no in-app runtime dialog by OS design.
 */
fun openDndAccessSettings(context: Context) {
    val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }.onFailure {
        // Extremely defensive fallback: same action without the extra.
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
