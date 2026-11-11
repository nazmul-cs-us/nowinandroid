package com.starception.submission.core.images

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

data class ArtworkCrop(val left: Float = 0f, val top: Float = 0f, val right: Float = 1f, val bottom: Float = 1f) {
    init { require(left >= 0 && top >= 0 && right <= 1 && bottom <= 1 && left < right && top < bottom) }
}

data class ReviewedHomeArtwork(val artwork: OnlineHomeArtwork, val sha256: String, val crop: ArtworkCrop = ArtworkCrop(),
    val theme: ArtworkTheme = ArtworkTheme.ISLAMIC)

internal expect fun artworkSha256(bytes: ByteArray): String

internal fun cropArtwork(image: ImageBitmap, crop: ArtworkCrop): ImageBitmap {
    if (crop == ArtworkCrop()) return image
    val left = (image.width * crop.left).toInt()
    val top = (image.height * crop.top).toInt()
    val size = IntSize(((crop.right - crop.left) * image.width).toInt().coerceAtLeast(1),
        ((crop.bottom - crop.top) * image.height).toInt().coerceAtLeast(1))
    return ImageBitmap(size.width, size.height).also {
        Canvas(it).drawImageRect(image, IntOffset(left, top), size, IntOffset.Zero, size, Paint())
    }
}
