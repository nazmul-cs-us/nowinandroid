/*
 * Copyright 2021 The Android Open Source Project
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

package com.starception.submission.shared.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.starception.submission.prayer.model.PrayerSettings
import com.starception.submission.settings.components.PrayerTimesSection

/**
 * Presents the app's prayer settings on iOS.
 *
 * Deliberately only a container. The controls inside are [PrayerTimesSection],
 * the same composable the Android settings screen uses, moved to :core:components
 * so there is one implementation rather than a second that drifts. An earlier
 * version of this file was a hand-written sheet with its own method list; it was
 * deleted, because a settings screen that merely resembles the real one is worse
 * than none — it looks authoritative while disagreeing about what the app does.
 *
 * A sheet rather than a screen only because there is no navigation on iOS yet.
 * When there is, this becomes a destination and the section moves with it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerSettingsSheet(
    settings: PrayerSettings,
    countryName: String?,
    onSettingsChange: (PrayerSettings) -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
        ) {
            Text(
                text = "Prayer times",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            PrayerTimesSection(
                prayerSettings = settings,
                // Offered whenever a country is known, so a user who has changed
                // something can always get back to their local defaults.
                showRestoreOption = countryName != null,
                autoDetectedCountryName = countryName,
                onSettingsChange = onSettingsChange,
                onRestoreClick = onRestore,
            )
        }
    }
}
