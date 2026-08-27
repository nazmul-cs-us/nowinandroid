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

package com.starception.submission.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.starception.submission.core.theme.resources.Res
import com.starception.submission.core.theme.resources.ubuntu_sans_bold
import com.starception.submission.core.theme.resources.ubuntu_sans_light
import com.starception.submission.core.theme.resources.ubuntu_sans_medium
import com.starception.submission.core.theme.resources.ubuntu_sans_regular
import org.jetbrains.compose.resources.Font

/**
 * Ubuntu Sans, loaded from bundled fonts.
 *
 * This is a composable rather than a top-level `val` because Compose
 * Multiplatform's `Font()` is itself composable — it reads from the generated
 * resource table, which needs composition scope.
 *
 * Note the deliberate difference from Android's [ubuntuInspiredFontFamily] in
 * core:designsystem, which lists Google downloadable fonts *first* and falls back
 * to the bundled TTFs only while a download is in flight or unavailable. That is
 * an Android-specific size optimisation with no Compose Multiplatform equivalent,
 * so Android keeps its own definition and this one always uses bundled fonts.
 *
 * The four TTFs are consequently duplicated: `core:designsystem/res/font` for
 * Android's resource system, and this module's `composeResources/font` for the
 * multiplatform one. 912 KB, and the alternative is regressing Android.
 */
@Composable
fun ubuntuFontFamily(): FontFamily = FontFamily(
    Font(Res.font.ubuntu_sans_light, FontWeight.Light),
    Font(Res.font.ubuntu_sans_regular, FontWeight.Normal),
    Font(Res.font.ubuntu_sans_medium, FontWeight.Medium),
    Font(Res.font.ubuntu_sans_bold, FontWeight.Bold),
)

/**
 * The app's type scale for shared UI.
 *
 * Takes Material 3's default metrics and applies the app's font, matching what
 * `NiaTypography` does on Android. Deriving from the defaults rather than
 * restating every size keeps the two from drifting as Material updates its scale.
 */
@Composable
fun sharedTypography(): Typography {
    val fontFamily = ubuntuFontFamily()
    val default = MaterialTheme.typography
    return remember(fontFamily) {
        Typography(
            displayLarge = default.displayLarge.copy(fontFamily = fontFamily),
            displayMedium = default.displayMedium.copy(fontFamily = fontFamily),
            displaySmall = default.displaySmall.copy(fontFamily = fontFamily),
            headlineLarge = default.headlineLarge.copy(fontFamily = fontFamily),
            headlineMedium = default.headlineMedium.copy(fontFamily = fontFamily),
            headlineSmall = default.headlineSmall.copy(fontFamily = fontFamily),
            titleLarge = default.titleLarge.copy(fontFamily = fontFamily),
            titleMedium = default.titleMedium.copy(fontFamily = fontFamily),
            titleSmall = default.titleSmall.copy(fontFamily = fontFamily),
            bodyLarge = default.bodyLarge.copy(fontFamily = fontFamily),
            bodyMedium = default.bodyMedium.copy(fontFamily = fontFamily),
            bodySmall = default.bodySmall.copy(fontFamily = fontFamily),
            labelLarge = default.labelLarge.copy(fontFamily = fontFamily),
            labelMedium = default.labelMedium.copy(fontFamily = fontFamily),
            labelSmall = default.labelSmall.copy(fontFamily = fontFamily),
        )
    }
}
