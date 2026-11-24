package com.starception.submission.core.images

import kotlinx.serialization.json.*

internal fun natureCommonsSearchUrl(page: Int): String {
    val topics = listOf("forest%20mist", "Lake%20Braies", "desert%20dunes", "waterfall%20forest",
        "ocean%20cliffs", "bamboo%20forest")
    val index = (page - 1).coerceAtLeast(0)
    return "https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=search" +
        "&gsrsearch=${topics[index % topics.size]}&gsrnamespace=6&gsrlimit=20&gsroffset=${index / topics.size * 20}" +
        "&prop=imageinfo&iiprop=url%7Csize%7Cmime%7Cextmetadata&iiurlwidth=2160&iiurlheight=3840" +
        "&iiextmetadatafilter=Artist%7CLicenseShortName%7CLicenseUrl%7CRestrictions"
}

/** Discovery only. Provider metadata can reject an image, but cannot approve it for Home. */
internal fun parseCommonsNatureCandidates(root: JsonObject): List<OnlineHomeArtwork> =
    root["query"]?.jsonObject?.get("pages")?.jsonObject?.values.orEmpty().mapNotNull { element ->
        val page = element.jsonObject
        val info = page["imageinfo"]?.jsonArray?.firstOrNull()?.jsonObject ?: return@mapNotNull null
        val width = info["width"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
        val height = info["height"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
        if (!highResolutionNatureWallpaper(width, height)) return@mapNotNull null
        val downloadWidth = info["thumbwidth"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
        val downloadHeight = info["thumbheight"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
        if (!highResolutionNatureWallpaper(downloadWidth, downloadHeight)) return@mapNotNull null
        if (info["mime"]?.jsonPrimitive?.content !in setOf("image/jpeg", "image/png", "image/webp")) return@mapNotNull null
        val metadata = info["extmetadata"]?.jsonObject ?: return@mapNotNull null
        fun field(name: String): String = metadata[name]?.jsonObject?.get("value")?.jsonPrimitive?.content.orEmpty()
            .replace(Regex("<[^>]*>"), "").replace("&amp;", "&").replace("&quot;", "\"")
            .replace("&#39;", "'").replace(Regex("\\s+"), " ").trim()
        if (field("Restrictions").isNotBlank()) return@mapNotNull null
        val license = field("LicenseShortName")
        val licenseUrl = when (license) {
            "CC0" -> "https://creativecommons.org/publicdomain/zero/1.0/"
            "Public domain" -> "https://creativecommons.org/publicdomain/mark/1.0/"
            else -> field("LicenseUrl").takeIf {
                license.startsWith("CC BY") && it.matches(Regex("https://creativecommons\\.org/licenses/by(?:-sa)?/(?:2\\.0|2\\.5|3\\.0|4\\.0)/?"))
            } ?: return@mapNotNull null
        }
        val id = page["pageid"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0 } ?: return@mapNotNull null
        val image = info["thumburl"]?.jsonPrimitive?.content.orEmpty()
        val source = info["descriptionurl"]?.jsonPrimitive?.content.orEmpty()
        if (!image.startsWith("https://thumb.wikimedia.org/wikipedia/commons/") &&
            !image.startsWith("https://upload.wikimedia.org/wikipedia/commons/")) return@mapNotNull null
        if (!source.startsWith("https://commons.wikimedia.org/wiki/File:")) return@mapNotNull null
        val title = page["title"]?.jsonPrimitive?.content.orEmpty().removePrefix("File:")
        val author = field("Artist")
        if (listOf(title, author, image, source).any { it.isBlank() || it.length > 2000 }) return@mapNotNull null
        OnlineHomeArtwork("commons-$id", title, "$author · Wikimedia Commons", source, image,
            licenseName = license, licenseUrl = licenseUrl, theme = ArtworkTheme.NATURE)
    }

/** Minimum QHD portrait detail remaining after a 9:16 center crop. */
internal fun highResolutionNatureWallpaper(width: Int, height: Int): Boolean =
    height > width && minOf(width.toLong(), height.toLong() * 9 / 16) >= 1440 && height >= 2560
