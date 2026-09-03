/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.starception.submission.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.UIKitView
import platform.Foundation.NSBundle
import platform.UIKit.UIImage
import platform.UIKit.UIImageView
import platform.UIKit.UIImageRenderingMode
import platform.UIKit.UIColor
import platform.UIKit.UIViewContentMode

@Composable
internal actual fun TopicArtwork(topicName: String, modifier: Modifier) {
    BundledImage(
        name = topicArtworkResourceName(topicName),
        contentMode = UIViewContentMode.UIViewContentModeScaleAspectFit,
        modifier = modifier,
    )
}

@Composable
internal actual fun NewsHeaderArtwork(resourceName: String, modifier: Modifier) {
    BundledImage(
        name = resourceName,
        contentMode = UIViewContentMode.UIViewContentModeScaleAspectFill,
        modifier = modifier,
    )
}

@Composable
internal actual fun LocationMarkerArtwork(tint: Color, modifier: Modifier) {
    UIKitView(
        factory = {
            UIImageView().apply {
                contentMode = UIViewContentMode.UIViewContentModeScaleAspectFit
                image = bundledImage("ic_flaticon_location_marker")
                    ?.imageWithRenderingMode(UIImageRenderingMode.UIImageRenderingModeAlwaysTemplate)
                tintColor = tint.toUIColor()
            }
        },
        modifier = modifier,
        update = { imageView -> imageView.tintColor = tint.toUIColor() },
    )
}

@Composable
private fun BundledImage(name: String, contentMode: UIViewContentMode, modifier: Modifier) {
    val image = remember(name) { bundledImage(name) }
    UIKitView(
        factory = {
            UIImageView().apply {
                clipsToBounds = true
                this.contentMode = contentMode
                this.image = image
            }
        },
        modifier = modifier,
        update = { imageView ->
            imageView.contentMode = contentMode
            imageView.image = image
        },
    )
}

private fun bundledImage(name: String): UIImage? =
    UIImage.imageNamed(name)
        ?: NSBundle.mainBundle.pathForResource(name, ofType = "png")
            ?.let { UIImage.imageWithContentsOfFile(it) }

private fun Color.toUIColor(): UIColor = UIColor.colorWithRed(
    red = red.toDouble(),
    green = green.toDouble(),
    blue = blue.toDouble(),
    alpha = alpha.toDouble(),
)
