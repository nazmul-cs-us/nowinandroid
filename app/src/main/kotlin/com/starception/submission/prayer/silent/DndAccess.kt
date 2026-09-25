/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.starception.submission.prayer.silent

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Open the system screen where the user grants Do-Not-Disturb ("notification policy" /
 * "Modes access") access to this app.
 *
 * The public SDK only exposes the generic access-list screen
 * (`ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS`), which drops the user into a long
 * alphabetical list of apps. AOSP/Pixel Settings, however, also handles the (hidden)
 * `NOTIFICATION_POLICY_ACCESS_DETAIL_SETTINGS` action with a `package:` data URI, which
 * lands directly on THIS app's page with its single "Allow" toggle — verified working on
 * Pixel (Android 15/16). We try the detail page first and fall back to the public list
 * on OEM builds that don't ship that activity.
 *
 * Either way a trip to Settings is unavoidable: DND access is a "special access"
 * permission with no in-app runtime dialog by OS design. The app must also declare
 * `ACCESS_NOTIFICATION_POLICY` in the manifest to appear in these screens (it does).
 */
fun openDndAccessSettings(context: Context) {
    val detail = Intent("android.settings.NOTIFICATION_POLICY_ACCESS_DETAIL_SETTINGS")
        .setData(Uri.parse("package:${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(detail) }.isSuccess) return

    // Fallback: the public generic list screen. EXTRA_APP_PACKAGE is a best-effort
    // hint some OEM builds honor to highlight this app; others ignore it.
    val list = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(list) }.onFailure {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
