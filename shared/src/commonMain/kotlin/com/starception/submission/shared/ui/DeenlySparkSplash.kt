package com.starception.submission.shared.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.starception.submission.core.designsystem.component.DeenlySplashScene
import com.starception.submission.core.designsystem.component.deenlySplashReminder

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun DeenlySparkSplash(onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val finished = rememberUpdatedState(onFinished)
    val completed = remember { mutableStateOf(false) }
    val finish = {
        if (!completed.value) {
            completed.value = true
            finished.value()
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        // Pause offscreen; Animatable also respects system reduced-motion settings.
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            progress.animateTo(1f, tween((3200 * (1f - progress.value)).toInt(), easing = LinearEasing))
            finish()
        }
    }
    PlatformBackHandler(onBack = finish)
    MaterialTheme { SparkSplashArtwork(progress = { progress.value }, onContinue = finish) }
}

@Composable
internal fun SparkSplashArtwork(progress: () -> Float, onContinue: () -> Unit) {
    DeenlySplashScene(progress, onContinue, "Continue", reminder = deenlySplashReminder(0, false, false))
}
