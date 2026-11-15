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

package com.starception.submission.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** iOS Focus is configured by the user; there is no app-owned DND permission. */
@Composable
fun PrayerFocusGuide(onOpenAppSettings: () -> Unit) {
    var showingGuide by rememberSaveable { mutableStateOf(false) }
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Quiet prayer time", style = MaterialTheme.typography.titleSmall)
            Text("Use an iPhone Focus to silence distractions during prayer. You control when it starts and which calls or alerts can reach you.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilledTonalButton(onClick = { showingGuide = true }, modifier = Modifier.testTag("prayerFocusGuideOpen")) {
                Text("Set up prayer Focus")
            }
        }
    }
    if (showingGuide) AlertDialog(
        onDismissRequest = { showingGuide = false },
        title = { Text("Prayer Focus on iPhone") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("1. Open iPhone Settings → Focus → + → Custom. Name your Focus “Prayer”.")
                Text("2. Choose the people and apps you want to allow. Allow Deenly if you want prayer reminders and call alerts during Focus.")
                Text("3. Turn on Prayer from Control Center → Focus when you pray, or add a time schedule in Focus settings.")
                Text("4. Check your times as prayer times change. iPhone Focus schedules do not follow Deenly’s prayer calculations automatically.")
                Text("If reminders are silent, check Deenly’s notification permission and your Focus allowances.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onOpenAppSettings, modifier = Modifier.testTag("prayerFocusNotificationSettings")) {
                    Text("Open Deenly settings")
                }
            }
        },
        confirmButton = { TextButton(onClick = { showingGuide = false }) { Text("Done") } },
    )
}
