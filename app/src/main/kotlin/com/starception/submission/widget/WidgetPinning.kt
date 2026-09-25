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

package com.starception.submission.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.starception.submission.R
import kotlinx.coroutines.delay

/**
 * Shared access to Android's launcher pinning API.
 *
 * The Settings gallery is built from the providers declared in the manifest, just like the
 * platform app-widget sample, so a newly added provider automatically appears there. The launch
 * prompt deliberately pins the balanced 4x2 Prayer Times provider as the recommended default.
 */
object WidgetPinning {
    private const val DISCOVERY_PREFERENCES = "widget_discovery_preferences"

    // v2 deliberately gives existing users one opportunity to discover the expanded widget set.
    private const val DISCOVERY_SEEN = "widget_discovery_seen_v2"
    private const val ACTION_WIDGET_PINNED =
        "com.starception.submission.action.WIDGET_PINNED"
    private const val EXTRA_WIDGET_LABEL = "widget_label"

    private val preferredProviderOrder by lazy {
        listOf(
            PrayerTimesWidgetReceiver::class.java.name,
            PrayerNextWidgetReceiver::class.java.name,
            PrayerTimelineWidgetReceiver::class.java.name,
            PrayerDevotionalWidgetReceiver::class.java.name,
            PrayerTimesTinyWidgetReceiver::class.java.name,
            PrayerTimesSmallWidgetReceiver::class.java.name,
            PrayerTimesLargeWidgetReceiver::class.java.name,
            PrayerTimesFullWidgetReceiver::class.java.name,
        )
    }

    fun installedProviders(context: Context): List<AppWidgetProviderInfo> {
        return AppWidgetManager.getInstance(context)
            .getInstalledProvidersForPackage(context.packageName, null)
            .filter { it.provider.packageName == context.packageName }
            .sortedWith(
                compareBy<AppWidgetProviderInfo> {
                    preferredProviderOrder.indexOf(it.provider.className)
                        .takeIf { index -> index >= 0 } ?: Int.MAX_VALUE
                }.thenBy {
                    it.loadLabel(context.packageManager).toString()
                },
            )
    }

    fun isPinningSupported(context: Context): Boolean {
        return AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported
    }

    fun hasPinnedWidget(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return installedProviders(context).any { provider ->
            manager.getAppWidgetIds(provider.provider).isNotEmpty()
        }
    }

    fun preferredProvider(context: Context): AppWidgetProviderInfo? {
        val providers = installedProviders(context)
        return providers.firstOrNull {
            it.provider.className == PrayerTimesWidgetReceiver::class.java.name
        } ?: providers.firstOrNull()
    }

    fun requestPin(context: Context, providerInfo: AppWidgetProviderInfo): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false

        val label = providerInfo.loadLabel(context.packageManager).toString()
        val callbackIntent = Intent(context, WidgetPinnedReceiver::class.java).apply {
            action = ACTION_WIDGET_PINNED
            putExtra(EXTRA_WIDGET_LABEL, label)
        }
        val successCallback = PendingIntent.getBroadcast(
            context,
            providerInfo.provider.flattenToString().hashCode(),
            callbackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return manager.requestPinAppWidget(providerInfo.provider, null, successCallback)
    }

    fun shouldShowDiscovery(context: Context): Boolean {
        val seen = context.getSharedPreferences(DISCOVERY_PREFERENCES, Context.MODE_PRIVATE)
            .getBoolean(DISCOVERY_SEEN, false)
        return !seen && isPinningSupported(context) && !hasPinnedWidget(context)
    }

    fun markDiscoverySeen(context: Context) {
        context.getSharedPreferences(DISCOVERY_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(DISCOVERY_SEEN, true)
            .apply()
    }

    internal fun pinnedLabel(intent: Intent): String? {
        return if (intent.action == ACTION_WIDGET_PINNED) {
            intent.getStringExtra(EXTRA_WIDGET_LABEL)
        } else {
            null
        }
    }
}

/** Receives the launcher's success callback after a requested widget is placed. */
class WidgetPinnedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WidgetPinning.markDiscoverySeen(context)
        val label = WidgetPinning.pinnedLabel(intent)
            ?: context.getString(R.string.prayer_widget_label)
        Toast.makeText(
            context,
            context.getString(R.string.widget_pinned_message, label),
            Toast.LENGTH_SHORT,
        ).show()
    }
}

/** One-time discovery prompt shown only when the launcher supports pinning and no widget exists. */
@Composable
fun WidgetDiscoveryPrompt() {
    val context = LocalContext.current
    val provider = remember(context) { WidgetPinning.preferredProvider(context) }
    var visible by rememberSaveable { mutableStateOf(false) }

    // Keep the system widget picker current even if the user never visits Settings > Widgets.
    LaunchedEffect(context) {
        WidgetPreviewRegistrar.register(context)
    }

    LaunchedEffect(provider) {
        if (provider != null && WidgetPinning.shouldShowDiscovery(context)) {
            // Let the destination settle before presenting app discovery UI.
            delay(1_200)
            visible = true
        }
    }

    if (!visible || provider == null) return

    fun dismiss() {
        WidgetPinning.markDiscoverySeen(context)
        visible = false
    }

    AlertDialog(
        onDismissRequest = ::dismiss,
        shape = RoundedCornerShape(28.dp),
        title = {
            Text(text = stringResource(R.string.widget_discovery_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(
                    painter = painterResource(R.drawable.prayer_widget_preview_medium_image),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(132.dp),
                )
                Text(
                    text = stringResource(R.string.widget_discovery_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    dismiss()
                    if (!WidgetPinning.requestPin(context, provider)) {
                        Toast.makeText(
                            context,
                            R.string.widget_pin_unavailable,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
            ) {
                Text(stringResource(R.string.widget_discovery_add))
            }
        },
        dismissButton = {
            TextButton(onClick = ::dismiss) {
                Text(stringResource(R.string.widget_discovery_not_now))
            }
        },
    )
}
