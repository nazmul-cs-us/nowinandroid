package com.starception.submission.family

import androidx.compose.ui.test.SemanticsNodeInteraction
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File

/** Design previews are temporary outputs, never source-controlled screenshot baselines. */
@OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
internal fun SemanticsNodeInteraction.captureFamilyPreview(name: String) {
    val directory = File(System.getProperty("java.io.tmpdir"), "opencode/family-ui-previews").apply { mkdirs() }
    captureRoboImage(File(directory, "$name.png").absolutePath,
        roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
}
