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

import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Image as SkiaImage
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
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
    // Decoding + PNG re-encoding is real disk/CPU work; never do it during
    // composition on the main thread. The header stays blank for the first
    // frames and fills in as soon as the bitmap is ready.
    var imageBitmap by remember(resourceName) { mutableStateOf<ImageBitmap?>(null) }
    var decodeFinished by remember(resourceName) { mutableStateOf(false) }
    LaunchedEffect(resourceName) {
        imageBitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            bundledImageBitmap(resourceName)
        }
        decodeFinished = true
    }
    val decodedBitmap = imageBitmap
    if (decodedBitmap != null) {
        Image(
            bitmap = decodedBitmap,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else if (decodeFinished) {
        // Reader headers render as a plain Compose image (decoded once through
        // Skia), matching Android's painterResource artwork: album parallax
        // graphicsLayer transforms compose correctly, which they cannot do on
        // the UIKit interop view used for the other bundled art.
        BundledImage(
            name = resourceName,
            contentMode = UIViewContentMode.UIViewContentModeScaleAspectFill,
            modifier = modifier,
        )
    }
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
    // imageNamed is backed by the system cache and safe in composition; the
    // uncached file-read fallback loads off the main thread instead.
    var image by remember(name) { mutableStateOf(UIImage.imageNamed(name)) }
    LaunchedEffect(name) {
        if (image == null) {
            image = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                bundledImage(name)
            }
        }
    }
    UIKitView(
        factory = {
            UIImageView().apply {
                clipsToBounds = true
                this.contentMode = contentMode
            }
        },
        update = { imageView ->
            imageView.contentMode = contentMode
            imageView.image = image
        },
        modifier = modifier,
    )
}

private fun bundledImage(name: String): UIImage? =
    UIImage.imageNamed(name)
        ?: NSBundle.mainBundle.pathForResource(name, ofType = "png")
            ?.let { UIImage.imageWithContentsOfFile(it) }

/**
 * Decodes a bundled PNG into a Compose bitmap. Xcode's copypng rewrites every
 * bundled PNG into the byte-swapped CgBI format, which Skia silently decodes
 * as black; UIImage decodes it correctly, so re-encode through
 * UIImagePNGRepresentation and hand Skia a standard PNG. Skia (not UIKit)
 * owns the final bitmap so the result participates fully in Compose drawing,
 * clipping, and graphicsLayer effects.
 */
internal fun bundledImageBitmap(name: String): ImageBitmap? {
    val image = bundledImage(name) ?: return null
    val pngData = UIImagePNGRepresentation(image) ?: return null
    return nsDataImageBitmap(pngData)
}

/**
 * Decodes a PNG/JPG file from disk. Files outside the app bundle (CDN
 * downloads) are standard images Skia reads directly; the UIImage
 * re-encode path only runs if the direct decode finds nothing.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun fileImageBitmap(path: String): ImageBitmap? {
    val data = NSData.dataWithContentsOfFile(path) ?: return null
    nsDataImageBitmap(data)?.let { return it }
    val image = UIImage.imageWithContentsOfFile(path) ?: return null
    val pngData = UIImagePNGRepresentation(image) ?: return null
    return nsDataImageBitmap(pngData)
}

@OptIn(ExperimentalForeignApi::class)
internal fun nsDataImageBitmap(data: NSData): ImageBitmap? {
    val size = data.length.toInt()
    if (size <= 0) return null
    val bytes = ByteArray(size)
    bytes.usePinned { pinned ->
        memcpy(pinned.addressOf(0), data.bytes, data.length)
    }
    return runCatching { SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
}
