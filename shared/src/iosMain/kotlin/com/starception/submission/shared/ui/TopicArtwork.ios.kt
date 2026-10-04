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

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.UIKitView
import platform.Foundation.NSBundle
import platform.UIKit.UIImage
import platform.UIKit.UIImageView
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
    Icon(
        imageVector = Icons.Filled.LocationOn,
        contentDescription = null,
        tint = tint,
        modifier = modifier,
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
