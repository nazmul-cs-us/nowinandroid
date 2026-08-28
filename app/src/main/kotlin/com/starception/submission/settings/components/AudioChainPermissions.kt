/*
 * Copyright 2022 The Android Open Source Project
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

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Android's permission gate for the travel dua audio chain.
 *
 * TravelDuaSection moved to :core:components so iOS renders the same settings.
 * The permissions could not go with it: RECORD_AUDIO and audio storage are
 * Android runtime permissions requested through an Activity result contract, and
 * the storage one even changes name at API 33. The section asks "may I play
 * this" and this answers.
 *
 * Returns a function that grants immediately when permission already exists, and
 * otherwise requests it and calls back only if the user agrees.
 */
@Composable
fun rememberAudioChainPermissionGate(): (onGranted: () -> Unit) -> Unit {
    val context = LocalContext.current
    val pending = remember { mutableStateOf<(() -> Unit)?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grantResults ->
        // Every requested permission must be granted; playing with only the
        // microphone would fail later, further from the cause.
        if (grantResults.values.all { it }) pending.value?.invoke()
        pending.value = null
    }

    return { onGranted ->
        val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val granted = listOf(Manifest.permission.RECORD_AUDIO, storagePermission).all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (granted) {
            onGranted()
        } else {
            pending.value = onGranted
            launcher.launch(arrayOf(Manifest.permission.RECORD_AUDIO, storagePermission))
        }
    }
}
