package com.starception.submission.core.images

import kotlinx.serialization.json.*

// Only the feed endpoint is application configuration. Image catalogs are remote data.
const val HOME_ARTWORK_FEED_URL = "https://raw.githubusercontent.com/nazmul-cs-us/nowinandroid/main/core/images/artwork/home-artwork-feed.json"

internal fun allowedArtworkDownload(url: String): Boolean = url == HOME_ARTWORK_FEED_URL ||
    url.startsWith("https://images.metmuseum.org/CRDImages/is/web-large/") ||
    url.startsWith("https://www.clevelandart.org/_next/image?url=https%3A%2F%2Fpiction.clevelandart.org%2F") ||
    url.startsWith("https://openaccess-cdn.clevelandart.org/") ||
    url.startsWith("https://images-assets.nasa.gov/image/") ||
    url.startsWith("https://fastly.picsum.photos/id/") ||
    (url.startsWith("https://api.openverse.org/v1/images/") && url.substringBefore('?').endsWith("/thumb/")) ||
    url.startsWith("https://live.staticflickr.com/") || url.startsWith("https://upload.wikimedia.org/")

fun parseReviewedArtworkFeed(text: String, personalDeviceApproval: Boolean = false): List<ReviewedHomeArtwork> {
    val root = Json.parseToJsonElement(text).jsonObject
    require(root["schemaVersion"]?.jsonPrimitive?.intOrNull == 1)
    val rows = root.getValue("artworks").jsonArray
    require(rows.size <= 1000)
    val approved = rows.mapNotNull { element ->
        val row = element.jsonObject
        val approval = row["approved"]?.jsonPrimitive
        if (approval?.isString != false || approval.booleanOrNull != true) return@mapNotNull null
        fun field(name: String) = row.getValue(name).jsonPrimitive.content.also { require(it.isNotBlank() && it.length <= 2000) }
        val id = field("id")
        require(id.matches(Regex("[A-Za-z0-9_-]{1,80}")))
        val image = field("imageUrl")
        require(image != HOME_ARTWORK_FEED_URL && (allowedArtworkDownload(image) ||
            (personalDeviceApproval && image.startsWith("https://picsum.photos/id/"))))
        val hash = field("sha256")
        require(hash.length == 64 && hash.all { it in '0'..'9' || it in 'a'..'f' })
        val source = field("sourceUrl"); val license = field("licenseUrl")
        require(source.startsWith("https://") && license.startsWith("https://"))
        val crop = row["crop"]?.jsonArray?.let {
            require(it.size == 4)
            ArtworkCrop(it[0].jsonPrimitive.float, it[1].jsonPrimitive.float, it[2].jsonPrimitive.float, it[3].jsonPrimitive.float)
        } ?: ArtworkCrop()
        ReviewedHomeArtwork(OnlineHomeArtwork(id, field("title"), field("credit"), source, image,
            licenseName = field("licenseName"), licenseUrl = license), hash, crop, ArtworkTheme.valueOf(field("theme")))
    }
    require(approved.map { it.artwork.id }.toSet().size == approved.size)
    return approved
}

internal fun encodeReviewedArtworkFeed(entries: List<ReviewedHomeArtwork>): JsonObject = buildJsonObject {
    put("schemaVersion", 1)
    putJsonArray("artworks") {
        entries.forEach { entry -> add(buildJsonObject {
            val a = entry.artwork
            put("approved", true); put("id", a.id); put("title", a.title); put("credit", a.artist)
            put("sourceUrl", a.sourceUrl); put("imageUrl", a.imageUrl); put("sha256", entry.sha256)
            put("licenseName", a.licenseName); put("licenseUrl", a.licenseUrl); put("theme", entry.theme.name)
            putJsonArray("crop") { listOf(entry.crop.left, entry.crop.top, entry.crop.right, entry.crop.bottom).forEach { add(it) } }
        }) }
    }
}
