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

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

internal data class RuntimePermissionGate(
    val isGranted: Boolean,
    val request: (onGranted: () -> Unit) -> Unit,
)

@Composable
internal fun rememberNotificationPermissionGate(): RuntimePermissionGate =
    rememberRuntimePermissionGate(
        permission = Manifest.permission.POST_NOTIFICATIONS,
        minimumApi = Build.VERSION_CODES.TIRAMISU,
    )

@Composable
internal fun rememberPhysicalActivityPermissionGate(): RuntimePermissionGate =
    rememberRuntimePermissionGate(
        permission = Manifest.permission.ACTIVITY_RECOGNITION,
        minimumApi = Build.VERSION_CODES.Q,
    )

@Composable
private fun rememberRuntimePermissionGate(
    permission: String,
    minimumApi: Int,
): RuntimePermissionGate {
    val context = LocalContext.current

    fun permissionIsGranted(): Boolean =
        Build.VERSION.SDK_INT < minimumApi ||
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    var isGranted by remember(permission) { mutableStateOf(permissionIsGranted()) }
    val pendingAction = remember(permission) { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        isGranted = granted
        val action = pendingAction.value
        pendingAction.value = null
        if (granted) action?.invoke()
    }

    // Refresh after returning from system settings as well as after a permission dialog.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        isGranted = permissionIsGranted()
    }

    return RuntimePermissionGate(
        isGranted = isGranted,
        request = { onGranted ->
            if (permissionIsGranted()) {
                isGranted = true
                onGranted()
            } else {
                pendingAction.value = onGranted
                launcher.launch(permission)
            }
        },
    )
}
