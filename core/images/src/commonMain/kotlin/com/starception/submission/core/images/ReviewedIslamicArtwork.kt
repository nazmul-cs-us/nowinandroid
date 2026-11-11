package com.starception.submission.core.images

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

data class ArtworkCrop(val left: Float = 0f, val top: Float = 0f, val right: Float = 1f, val bottom: Float = 1f) {
    init { require(left >= 0 && top >= 0 && right <= 1 && bottom <= 1 && left < right && top < bottom) }
}

/** Only exact, visually reviewed image bytes are eligible; museum search is not a safety filter. */
data class ReviewedHomeArtwork(val artwork: OnlineHomeArtwork, val sha256: String, val crop: ArtworkCrop = ArtworkCrop())

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

private fun met(id: Int, title: String, image: String, hash: String, crop: ArtworkCrop = ArtworkCrop(.08f, .08f, .92f, .92f)) =
    ReviewedHomeArtwork(OnlineHomeArtwork("met-$id", title, "Unknown artist · The Met",
        "https://www.metmuseum.org/art/collection/search/$id",
        "https://images.metmuseum.org/CRDImages/is/web-large/$image"), hash, crop)

/**
 * Reviewed 2026-11-10: Islamic Art department, public-domain geometric/botanical tilework;
 * no human or animal figures. Hashes pin the reviewed photographs, not merely object IDs.
 * See artwork/REVIEWED_ISLAMIC_ARTWORK.md before changing this allowlist.
 */
val reviewedIslamicArtwork: List<ReviewedHomeArtwork> = listOf(
    met(447013, "Blue floral arabesque tile panel", "DT11978.jpg",
        "a3c1e422250bd4c4ce0072d2699854ce146f189a9a9376af62f2101ebb854fc1"),
    met(447015, "Red-bordered floral tile panel", "LC-17_190_2085.jpg",
        "bc8a914f0cebfd06fcd6c106f64ef1e125b31266d858f6d294a366af847ff7f6"),
    met(445001, "Blue and green floral medallions", "07.190.20%20a.jpg",
        "aba789c646aa8c3cd79a4e0c5f3330767b0079334e6fe35fb400422cab465a27"),
    met(447604, "Blue garden tile panel", "sf22-185-11a-d.jpg",
        "c131344a99ef8bea107a44ce9ce4242f57b0108c232df77900130e1ce8a866f4"),
    met(447625, "Hexagonal floral tile panel", "LC-23_12_3.jpg",
        "8366738f3b31128d7c86e7aef82743b5cbfeeeaa52a5d8a01a320912b24d5c16", ArtworkCrop(.28f, .20f, .72f, .78f)),
    met(447014, "Blue floral lattice tile panel", "DP-30633-001.jpg",
        "83d879254fea4e27b11d6c0fdc898a9bca852cc79ba9601ac69da93864ab9b47", ArtworkCrop(.16f, .15f, .84f, .86f)),
    met(447016, "Blue and turquoise floral tile panel", "DP238754.jpg",
        "bea91d8f5e4f535d2eaa4160f8a4adc4931cf513ded854ec1fb3e7b1759d38d8", ArtworkCrop(.16f, .15f, .84f, .86f)),
    met(447017, "Warm floral arabesque tile panel", "LC-17_190_2087.jpg",
        "98fe6a194eecf5898dd93648b112b8b6eec5ced22313f022fcf0e7553142225d"),
    met(447609, "Blue and green botanical tiles", "22.185.15%20and%2007.190.15.jpg",
        "e33816785b50a01339aabda1791d8294ad390e451407b6c3e7d27ea03d40fc89"),
    met(447623, "Flowers in a vase tile panel", "sf23-12-2a-f.jpg",
        "17aafe128e92c83ad4f1fbf301d67466e177cd956fea73af140aab7340bc6c73"),
    met(449137, "Turquoise star and hexagonal tiles", "wb-37.40.26.JPG",
        "b1ad794d30568d8171cb21402ee3d052b4e00d65b05dd3b75babf5ced3d0c165"),
    met(452791, "Turquoise and cobalt star geometry", "1975.350.4.jpg",
        "89a43dea4f5caf1dbdd80d7eead21f7785abe63abfd3963095fef69976c0acca", ArtworkCrop(.25f, .22f, .75f, .78f)),
    met(444457, "Cobalt floral tile", "DP-34213-001.jpg",
        "3997b58054c00b243daf6fd04e73aa7711f899573f7d2c1959cb1183512f96d2", ArtworkCrop(.15f, .15f, .85f, .85f)),
    met(444447, "Flowers and leaves tile", "DP-34226-001.jpg",
        "610238d8c640973a5c85546ec97f83d18c8f1986eb1b410bc78af398b5bf4ba6", ArtworkCrop(.20f, .15f, .80f, .85f)),
    met(446688, "Cypress and blue flowers tile", "sf15-76-1.jpg",
        "0028c2fa7d7d602040df1a63753e25500d29dbf623b969d6457e5348e7a4a5ba"),
    met(447251, "Blue floral scrollwork tile", "sf20-120-184.jpg",
        "1df6ef5f023a7862cc5b888684e0ffb17aea4bc8aa33d9cd572651cf8e0a6d55"),
    met(452005, "Turquoise floral scrollwork tile", "sf68-215-12.jpg",
        "902f6a6f134fce2a41440103fd520dda63135a942daac91930ce9649b8446919"),
    met(452070, "Grapevine and leaves tile", "sf1970-14.jpg",
        "a9c7b2ca6d6cd29a096ebb38f025db24defa7cba6f8b7809e127e4053adb0093"),
    met(453326, "Blue botanical tile", "sf1991-224-2.jpg",
        "70c53b1a37d50c4493a3c87493dfdcd1e57b395dd920f790dae6ee0ba173d8f0", ArtworkCrop(.16f, .06f, .84f, .94f)),
    met(444704, "Turquoise ornamental tile", "DP-33639-001.jpg",
        "f707e3006a62e56d5fb38c1687a4144721a1d5d87b67a2d0c5624b3d41c21a82", ArtworkCrop(.12f, .12f, .88f, .88f)),
) + reviewedClevelandArtwork
