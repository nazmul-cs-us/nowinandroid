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

package com.starception.submission.shared.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView

/**
 * Implemented by the Swift `SalahScene3DHost` so the Compose interop layer can
 * obtain the Metal-backed scene view without knowing its type.
 */
interface Salah3DSceneViewProviding {
    fun createSceneView(): platform.UIKit.UIView
}

/**
 * Embeds the Swift3D-rendered scene. The injected [Salah3DSceneService] is
 * both the data bridge (Kotlin pushes frames) and the view provider.
 */
@Composable
internal actual fun Salah3DSceneHost(service: Salah3DSceneService, modifier: Modifier) {
    val provider = service as? Salah3DSceneViewProviding
    if (provider == null) {
        Box(modifier)
        return
    }
    UIKitView(
        factory = { provider.createSceneView() },
        modifier = modifier,
        properties = UIKitInteropProperties(
            interactionMode = UIKitInteropInteractionMode.NonCooperative,
            isNativeAccessibilityEnabled = true,
        ),
    )
}
