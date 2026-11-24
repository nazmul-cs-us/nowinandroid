package com.starception.submission.ui

import androidx.activity.compose.BackHandler
import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.starception.submission.R
import com.starception.submission.core.designsystem.component.DeenlySplashScene
import com.starception.submission.core.designsystem.component.deenlySplashReminder
import com.starception.submission.core.designsystem.component.DeenlySplashReminder
import com.starception.submission.ml.DeenlyKnowledgeModel
import com.starception.submission.ml.DeenlyKnowledgeTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun DeenlySparkSplash(
    reminderLoader: suspend (Context) -> DeenlySplashReminder = ::loadNextSplashReminder,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val reminder by produceState<DeenlySplashReminder?>(null, context) {
        value = withContext(Dispatchers.IO) {
            runCatching { reminderLoader(context) }.getOrDefault(deenlySplashReminder(0, false, false))
        }
    }
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
    BackHandler(onBack = finish)
    MaterialTheme { SparkSplashArtwork(progress = { progress.value }, onContinue = finish, reminder = reminder) }
}

@Composable
internal fun SparkSplashArtwork(progress: () -> Float, onContinue: () -> Unit,
    reminder: DeenlySplashReminder? = deenlySplashReminder(0, false, false)) {
    DeenlySplashScene(progress, onContinue, stringResource(R.string.splash_continue),
        versionLabel = "v${com.starception.submission.BuildConfig.VERSION_NAME}", reminder = reminder)
}

/** Called on IO once per splash; never load a model or generate text on the startup path. */
internal fun loadNextSplashReminder(context: Context): DeenlySplashReminder {
    val preferences = context.getSharedPreferences("splash_reminders", Context.MODE_PRIVATE)
    val model = DeenlyKnowledgeModel.isModelDownloaded(context, DeenlyKnowledgeTask.ANSWER)
    val hadith = File(context.filesDir, "cdn_assets/databases/hadith/sahih_bukhari.db")
        .let { it.isFile && it.length() > 1024 }
    val language = context.getSharedPreferences("quran_prefs", Context.MODE_PRIVATE)
        .getString("quran_translation", "ar") ?: "ar"
    var poolLanguage = language
    var pool = shortQuranReminderPool(context, language)
    if (pool.isEmpty() && language != "en") {
        poolLanguage = "en"
        pool = shortQuranReminderPool(context, "en")
    }
    if (pool.isEmpty()) {
        poolLanguage = "en-fallback"
        val first = deenlySplashReminder(0, false, false)
        pool = buildList {
            var index = 0L
            do { add(deenlySplashReminder(index++, false, false)) }
            while (deenlySplashReminder(index, false, false) != first)
        }
    }
    if (model && hadith && poolLanguage.startsWith("en")) {
        // Keep the existing verified English Hadith option; never translate it with an LLM.
        pool = pool + deenlySplashReminder(7, true, true)
    }
    return SplashReminderShuffleBag(preferences).next(poolLanguage, pool)
}
