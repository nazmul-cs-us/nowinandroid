package com.starception.submission.feature.surah

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.PreviewContextConfigurationEffect

/** Robolectric's plain Application does not run the multiplatform resource initializer. */
@OptIn(ExperimentalResourceApi::class)
internal fun ComposeContentTestRule.setTafsirContent(content: @Composable () -> Unit) {
    setContent {
        CompositionLocalProvider(LocalInspectionMode provides true) { PreviewContextConfigurationEffect() }
        content()
    }
}
