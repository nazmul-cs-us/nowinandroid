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

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.starception.submission.shared.qibla.HeadingProvider
import com.starception.submission.shared.qibla.HeadingReading
import com.starception.submission.shared.qibla.cardinalDirection
import com.starception.submission.shared.qibla.qiblaBearing
import com.starception.submission.shared.qibla.qiblaDistanceKm
import kotlin.math.roundToInt

/** Android's separate large globe presentation, with native sheet dismissal. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QiblaGlobeSheet(latitude: Double, longitude: Double, onDismiss: () -> Unit) {
    val provider = remember { HeadingProvider() }
    var reading by remember { mutableStateOf(HeadingReading()) }
    DisposableEffect(provider) {
        provider.start { reading = it }
        onDispose { provider.stop() }
    }
    val bearing = remember(latitude, longitude) { qiblaBearing(latitude, longitude) }
    val distance = remember(latitude, longitude) { qiblaDistanceKm(latitude, longitude).roundToInt() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val globeHeight = ((maxWidth - 32.dp) * 1.10f).coerceIn(300.dp, 450.dp)
            Column(
                Modifier.fillMaxWidth().heightIn(max = maxHeight)
                    .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            ) {
                Row(Modifier.fillMaxWidth()) {
                    Text("Qibla direction", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { scope.launch { sheetState.hide(); onDismiss() } }) { Text("Done") }
                }
                Text(
                    "${bearing.roundToInt()}° ${cardinalDirection(bearing)} · $distance km to Makkah",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                PlatformWorldWindGlobe(
                    latitude = latitude,
                    longitude = longitude,
                    headingDegrees = reading.headingDegrees,
                    headingAccuracyDegrees = reading.accuracyDegrees,
                    qiblaBearing = bearing,
                    expanded = true,
                    modifier = Modifier.fillMaxWidth().height(globeHeight).clip(RoundedCornerShape(24.dp)),
                )
            }
        }
    }
}
