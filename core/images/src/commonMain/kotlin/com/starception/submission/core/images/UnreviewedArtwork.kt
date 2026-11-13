package com.starception.submission.core.images

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import kotlin.time.Clock
import kotlin.random.Random

/** Deliberately not a ReviewedHomeArtwork: previews cannot enter Home's approved collection. */
data class UnreviewedArtwork(val artwork: OnlineHomeArtwork, val sha256: String)

data class ArtworkReviewState(
    val candidates: List<UnreviewedArtwork> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
)

/** No disk/cache write methods: unreviewed bytes stay separate from Home's offline collection. */
fun interface ArtworkReviewPlatform {
    suspend fun download(url: String, maximumBytes: Int): ByteArray
}

internal fun artworkReviewMetadataUrl(url: String): Boolean = url == HOME_ARTWORK_FEED_URL ||
    url.startsWith("https://picsum.photos/v2/list?") ||
    url.startsWith("https://images-api.nasa.gov/search?") ||
    url.startsWith("https://openaccess-api.clevelandart.org/api/artworks/?")

internal fun allowedArtworkReviewDownload(url: String): Boolean = artworkReviewMetadataUrl(url) ||
    allowedArtworkDownload(url) || url.startsWith("https://picsum.photos/id/")

internal expect fun decodeArtworkReviewPreview(bytes: ByteArray): ImageBitmap

/** User-triggered discovery, never a background job and never automatic approval. */
class ArtworkReviewRepository(
    private val platform: ArtworkReviewPlatform,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    initialPageOffset: Int = Random.nextInt(20),
) {
    private val mutable = MutableStateFlow(ArtworkReviewState())
    val state = mutable.asStateFlow()
    private val seen = mutableSetOf<String>()
    private val seenHashes = mutableSetOf<String>()
    private var batch = initialPageOffset.coerceIn(0, 19)
    private var generation = 0
    private var request: Job? = null

    fun clear() {
        generation++
        request?.cancel()
        request = null
        mutable.value = ArtworkReviewState()
    }

    fun dismiss(id: String) {
        mutable.value = mutable.value.copy(candidates = mutable.value.candidates.filterNot { it.artwork.id == id })
    }

    suspend fun approveForDevice(candidate: UnreviewedArtwork, theme: ArtworkTheme, home: HomeArtworkRepository) {
        val bytes = withContext(dispatcher) {
            platform.download(candidate.artwork.imageUrl, 8_000_000).also {
                require(artworkSha256(it) == candidate.sha256) { "Image changed since preview; fetch and review it again" }
            }
        }
        home.approveForDevice(candidate, theme, bytes)
        dismiss(candidate.artwork.id)
    }

    fun fetch(theme: ArtworkTheme, allowUnreviewed: Boolean = false) {
        if (!allowUnreviewed) { clear(); return }
        request?.cancel()
        val version = ++generation
        val page = 1 + ((clock.now().epochSeconds / 86400 + batch++) % 20).toInt()
        val previouslySeen = seen.toSet()
        val previousHashes = seenHashes.toSet()
        mutable.value = ArtworkReviewState(loading = true)
        request = scope.launch {
            try {
                val result = withContext(dispatcher) {
                    var approvedFeedUnavailable = false
                    val approved = try {
                        parseReviewedArtworkFeed(platform.download(HOME_ARTWORK_FEED_URL, 2_000_000).decodeToString())
                            .map { it.artwork.id }.toSet()
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { approvedFeedUnavailable = true; emptySet() }
                    val discoveries = discover(theme, page).distinctBy { it.id }
                        .filter { it.id !in approved && it.id !in previouslySeen }
                    val loaded = mutableListOf<UnreviewedArtwork>()
                    var failures = 0
                    for (candidate in discoveries.take(15)) {
                        ensureActive()
                        try {
                            val bytes = platform.download(candidate.imageUrl, 8_000_000)
                            val digest = artworkSha256(bytes)
                            if (digest in previousHashes || loaded.any { it.sha256 == digest }) continue
                            val bitmap = decodeArtworkReviewPreview(bytes)
                            loaded += UnreviewedArtwork(candidate.copy(bitmap = bitmap), digest)
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { failures++ }
                        if (loaded.size == 5) break
                    }
                    ensureActive()
                    ArtworkReviewState(
                            candidates = loaded,
                            error = when {
                                discoveries.isEmpty() -> "No more candidates on this page. Tap Fetch more to search another page."
                                loaded.isEmpty() && failures == 0 -> "No new preview images on this page. Tap Fetch more to search another page."
                                loaded.isEmpty() -> "Could not download the candidate previews. Check your connection and try again."
                                else -> null
                            },
                            notice = listOfNotNull(
                                if (failures > 0) "$failures candidate preview(s) could not be loaded." else null,
                                if (approvedFeedUnavailable) "The approved-feed check was unavailable; some candidates may already have been reviewed." else null,
                             ).joinToString(" ").takeIf(String::isNotBlank),
                         )
                }
                ensureActive()
                if (version == generation) {
                    seen.addAll(result.candidates.map { it.artwork.id })
                    seenHashes.addAll(result.candidates.map { it.sha256 })
                    mutable.value = result
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (version == generation) mutable.value = ArtworkReviewState(error = "Could not fetch unreviewed images. Check your connection and try again.")
            }
        }
    }

    private suspend fun discover(theme: ArtworkTheme, page: Int): List<OnlineHomeArtwork> {
        val url = when (theme) {
            ArtworkTheme.NATURE -> "https://picsum.photos/v2/list?page=$page&limit=20"
            ArtworkTheme.SPACE -> "https://images-api.nasa.gov/search?q=nebula&media_type=image&page_size=20&page=$page"
            ArtworkTheme.ISLAMIC -> "https://openaccess-api.clevelandart.org/api/artworks/?q=tile&cc0=1&has_image=1&department=Islamic%20Art&limit=20&skip=${(page - 1) * 20}"
        }
        var text = platform.download(url, 2_000_000).decodeToString()
        if (theme == ArtworkTheme.SPACE) {
            val collection = Json.parseToJsonElement(text).jsonObject["collection"]?.jsonObject
            val total = collection?.get("metadata")?.jsonObject?.get("total_hits")?.jsonPrimitive?.intOrNull ?: 0
            if (collection?.get("items")?.jsonArray.orEmpty().isEmpty() && total > 0) {
                val lastPage = ((total + 19) / 20).coerceAtLeast(1)
                val availablePage = 1 + (page - 1) % lastPage
                text = platform.download(url.substringBeforeLast("page=") + "page=$availablePage", 2_000_000).decodeToString()
            }
        }
        if (theme == ArtworkTheme.ISLAMIC) {
            val root = Json.parseToJsonElement(text).jsonObject
            val total = root["info"]?.jsonObject?.get("total")?.jsonPrimitive?.intOrNull ?: 0
            if (root["data"]?.jsonArray.orEmpty().isEmpty() && total > 0) {
                val offset = ((page - 1) * 20) % total
                text = platform.download(url.substringBeforeLast("skip=") + "skip=$offset", 2_000_000).decodeToString()
            }
        }
        return parseArtworkReviewCandidates(theme, text)
    }
}

internal fun parseArtworkReviewCandidates(theme: ArtworkTheme, text: String): List<OnlineHomeArtwork> {
    val root = Json.parseToJsonElement(text)
    fun JsonObject.string(key: String) = get(key)?.jsonPrimitive?.contentOrNull.orEmpty()
    fun candidate(id: String, title: String, credit: String, source: String, image: String,
                  license: String, rights: String): OnlineHomeArtwork? {
        if (id.length !in 1..80 || !id.all { it.isLetterOrDigit() && it.code < 128 || it in "_-" }) return null
        if (!source.startsWith("https://") || !rights.startsWith("https://") || !allowedArtworkReviewDownload(image) || artworkReviewMetadataUrl(image)) return null
        if (listOf(title, credit, source, image, license, rights).any { it.isBlank() || it.length > 2000 }) return null
        return OnlineHomeArtwork(id, title, credit, source, image, licenseName = license, licenseUrl = rights)
    }
    return when (theme) {
        ArtworkTheme.NATURE -> root.jsonArray.mapNotNull { value ->
            val row = value.jsonObject
            val id = row.string("id")
            candidate("picsum-$id", "Nature candidate $id", row.string("author") + " · via Lorem Picsum",
                row.string("url"), "https://picsum.photos/id/$id/900/1200", "Unsplash License", "https://unsplash.com/license")
        }
        ArtworkTheme.SPACE -> root.jsonObject["collection"]?.jsonObject?.get("items")?.jsonArray.orEmpty().mapNotNull { value ->
            val row = value.jsonObject
            val data = row["data"]?.jsonArray?.firstOrNull()?.jsonObject ?: return@mapNotNull null
            if (data.string("copyright").isNotBlank()) return@mapNotNull null
            val image = row["links"]?.jsonArray.orEmpty().map { it.jsonObject }.filter {
                it.string("render") == "image" && (it["width"]?.jsonPrimitive?.intOrNull ?: 0) in 100..1800 &&
                    (it["height"]?.jsonPrimitive?.intOrNull ?: 0) in 100..1800
            }.maxByOrNull { (it["width"]?.jsonPrimitive?.intOrNull ?: 0) * (it["height"]?.jsonPrimitive?.intOrNull ?: 0) }
                ?: return@mapNotNull null
            val id = data.string("nasa_id")
            candidate("nasa-$id", data.string("title"), data.string("secondary_creator").ifBlank { "NASA · Verify source credit" },
                "https://images.nasa.gov/details/$id", image.string("href"), "NASA media usage guidelines",
                "https://www.nasa.gov/nasa-brand-center/images-and-media/")
        }
        ArtworkTheme.ISLAMIC -> root.jsonObject["data"]?.jsonArray.orEmpty().mapNotNull { value ->
            val row = value.jsonObject
            if (row.string("share_license_status") != "CC0" || row.string("department") != "Islamic Art") return@mapNotNull null
            val image = row["images"]?.takeUnless { it is JsonNull }?.jsonObject?.get("web")
                ?.takeUnless { it is JsonNull }?.jsonObject ?: return@mapNotNull null
            candidate("cma-" + row.string("accession_number").replace('.', '-'), row.string("title"),
                "The Cleveland Museum of Art · " + row.string("creditline"), row.string("url"), image.string("url"),
                "CC0", "https://creativecommons.org/publicdomain/zero/1.0/")
        }
    }
}

@Composable
expect fun rememberArtworkReviewRepository(): ArtworkReviewRepository

val LocalArtworkReviewRepository = staticCompositionLocalOf<ArtworkReviewRepository?> { null }
