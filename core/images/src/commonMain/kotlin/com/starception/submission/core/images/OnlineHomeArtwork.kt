package com.starception.submission.core.images

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.painterResource
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Instant

data class OnlineHomeArtwork(
    val id: String,
    val title: String,
    val artist: String,
    val sourceUrl: String,
    val imageUrl: String,
    val bitmap: ImageBitmap? = null,
    val licenseName: String = "CC0",
    val licenseUrl: String = "https://creativecommons.org/publicdomain/zero/1.0/",
)

data class HomeArtworkCollectionState(
    val artworks: List<OnlineHomeArtwork> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val updatedAt: Instant? = null,
    val notice: String? = null,
)

/** Platform IO stays separate from selection/scheduling, so failures can be tested without a network. */
interface HomeArtworkPlatform {
    suspend fun download(url: String, maximumBytes: Int): ByteArray
    fun readMetadata(): String?
    fun writeMetadata(value: String)
    fun readImage(id: String): ByteArray?
    fun writeImage(id: String, bytes: ByteArray)
    fun retainImages(ids: Set<String>)
}

private class ReviewedArtworkExhausted : Exception()

/** Owns a persistent last-good collection. Never rotates bundled resources as a pretend download. */
class HomeArtworkRepository(
    private val platform: HomeArtworkPlatform,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val clock: Clock = Clock.System,
    private val chooseOffset: (Int) -> Int = { Random.nextInt(it) },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val catalog: List<ReviewedHomeArtwork>? = null,
) {
    private val mutable = MutableStateFlow(HomeArtworkCollectionState())
    val state = mutable.asStateFlow()
    private val mutex = Mutex()
    private var activeRotation: Int? = null
    private var requestedRotation: Int? = null
    private var lastManualRevision = 0
    private var request: Job? = null
    private var manualRequest = false
    private var generation = 0
    private var lastFailure: Instant? = null
    private var activeCatalog: List<ReviewedHomeArtwork> = catalog.orEmpty()
    private var legacyMetadata: JsonObject? = null
    private val seen = homeArtworkGallery.map { it.sourceUrl.substringAfterLast('/') }.toMutableSet()
    private val restored = scope.async { restore() }

    /** Manual revisions are consumed once across Home, Settings, previews and expanded tiles. */
    fun update(rotation: Int, frozen: Boolean, manualRevision: Int = 0, theme: ArtworkTheme = ArtworkTheme.ISLAMIC,
               reuseReviewed: Boolean = false) {
        val manual = manualRevision > lastManualRevision
        if (manual) lastManualRevision = manualRevision
        if (frozen && !manual) {
            if (!manualRequest) {
                generation++; request?.cancel(); requestedRotation = null
                mutable.value = mutable.value.copy(loading = false)
            }
            return
        }
        if (!manual && (activeRotation == rotation || (request?.isActive == true && requestedRotation == rotation))) return
        if (!manual && lastFailure?.let { clock.now().epochSeconds - it.epochSeconds < 300 } == true) return
        request?.cancel()
        val version = ++generation
        requestedRotation = rotation
        manualRequest = manual
        request = scope.launch {
            mutex.withLock {
                restored.await()
                if (!manual && activeRotation == rotation) return@withLock
                var previous = mutable.value
                mutable.value = previous.copy(loading = true, error = null, notice = null)
                try {
                    val reviewed = withContext(ioDispatcher) {
                        catalog ?: parseReviewedArtworkFeed(platform.download(HOME_ARTWORK_FEED_URL, 2_000_000).decodeToString())
                    }
                    activeCatalog = reviewed
                    legacyMetadata?.let { old ->
                        withContext(ioDispatcher) { runCatching { restoreCollection(old, reviewed) } }
                        legacyMetadata = null
                        previous = mutable.value.copy(loading = false)
                    }
                    if (!manual && activeRotation == rotation) {
                        mutable.value = previous
                        return@withLock
                    }
                    val downloaded = withContext(ioDispatcher) { fetchReviewed(theme, allowPreviouslySeen = manual && reuseReviewed) }
                    ensureActive()
                    if (version != generation) return@withLock
                    val updated = clock.now()
                    val reusedImages = downloaded.count { it.id in seen }
                    val updatedSeen = (seen + downloaded.map { it.id }).toList().takeLast(2000)
                    // The small atomic metadata commit is the collection's publication point.
                    platform.writeMetadata(encodeCollection(downloaded, rotation, updated, updatedSeen, reviewed))
                    seen.addAll(downloaded.map { it.id })
                    activeRotation = rotation
                    lastFailure = null
                    mutable.value = HomeArtworkCollectionState(downloaded, updatedAt = updated,
                        notice = if (reusedImages > 0) {
                            "Selected theme collection · Includes previously viewed, reviewed artwork."
                        } else null)
                } catch (cancelled: CancellationException) {
                    if (version == generation) mutable.value = previous.copy(loading = false)
                    throw cancelled
                } catch (failure: Exception) {
                    if (version == generation) {
                        lastFailure = clock.now()
                        mutable.value = previous.copy(loading = false,
                            error = if (failure is ReviewedArtworkExhausted) {
                                "No new reviewed ${theme.label} artwork is available yet. Your current collection is unchanged. Newly discovered images must be approved before download."
                            } else "Couldn't download reviewed artwork. Your current collection is unchanged. Try Refresh now when you're online.")
                    }
                } finally {
                    withContext(NonCancellable + ioDispatcher) {
                        runCatching { platform.retainImages(mutable.value.artworks.map { it.id }.toSet()) }
                    }
                    if (version == generation) manualRequest = false
                }
            }
        }
    }

    @OptIn(ExperimentalResourceApi::class)
    private suspend fun fetchReviewed(theme: ArtworkTheme, allowPreviouslySeen: Boolean): List<OnlineHomeArtwork> {
        val chosen = linkedMapOf<String, OnlineHomeArtwork>()
        val approved = activeCatalog.distinctBy { it.artwork.id }.filter { it.theme == theme }
        val unseen = approved.filter { it.artwork.id !in seen }
        if ((if (allowPreviouslySeen) approved.size else unseen.size) < 5) throw ReviewedArtworkExhausted()
        fun rotate(entries: List<ReviewedHomeArtwork>): List<ReviewedHomeArtwork> {
            if (entries.isEmpty()) return entries
            val offset = chooseOffset(entries.size).mod(entries.size)
            return entries.drop(offset) + entries.take(offset)
        }
        // Only explicit theme selection may revisit approved artwork. Refresh now
        // and scheduled updates require genuinely unseen images, never a shuffle.
        val currentIds = mutable.value.artworks.map { it.id }.toSet()
        val candidates = rotate(unseen) + if (allowPreviouslySeen) {
            rotate(approved.filter { it.artwork.id in seen && it.artwork.id !in currentIds }) +
                rotate(approved.filter { it.artwork.id in seen && it.artwork.id in currentIds })
        } else emptyList()
        for (reviewed in candidates.take(15)) {
            val artwork = reviewed.artwork
            val result = runCatching {
                val bytes = platform.download(artwork.imageUrl, 8_000_000)
                require(artworkSha256(bytes) == reviewed.sha256) { "Artwork changed since visual review" }
                val image = bytes.decodeToImageBitmap()
                require(image.width in 100..1800 && image.height in 100..1800)
                platform.writeImage(artwork.id, bytes)
                artwork.copy(bitmap = cropArtwork(image, reviewed.crop))
            }.getOrElse {
                if (it is CancellationException) throw it
                null
            }
            if (result != null) chosen[result.id] = result
            if (chosen.size == 5) return chosen.values.toList()
        }
        error("Five reviewed artwork downloads did not succeed")
    }

    @OptIn(ExperimentalResourceApi::class)
    private suspend fun restore() = withContext(ioDispatcher) {
        runCatching {
            val root = Json.parseToJsonElement(platform.readMetadata() ?: return@runCatching).jsonObject
            seen.addAll(root["seen"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content })
            val reviewed = catalog ?: root["reviewedCatalog"]?.let { parseReviewedArtworkFeed(it.toString()) }
            if (reviewed == null) { legacyMetadata = root; return@runCatching }
            activeCatalog = reviewed
            restoreCollection(root, reviewed)
        }
    }

    @OptIn(ExperimentalResourceApi::class)
    private fun restoreCollection(root: JsonObject, reviewed: List<ReviewedHomeArtwork>) {
            val artworks = root.getValue("artworks").jsonArray.map { value ->
                val item = value.jsonObject
                val id = item.getValue("id").jsonPrimitive.content
                // Reject all legacy search results and reconstruct credits from
                // the reviewed registry, never from untrusted cache metadata.
                val approved = requireNotNull(reviewed.find { it.artwork.id == id })
                val bytes = requireNotNull(platform.readImage(id))
                require(artworkSha256(bytes) == approved.sha256)
                val bitmap = bytes.decodeToImageBitmap()
                require(bitmap.width in 100..1800 && bitmap.height in 100..1800)
                approved.artwork.copy(bitmap = cropArtwork(bitmap, approved.crop))
            }
            require(artworks.size == 5 && artworks.distinctBy { it.id }.size == 5)
            val updatedAt = Instant.parse(root.getValue("updated").jsonPrimitive.content)
            activeRotation = root.getValue("rotation").jsonPrimitive.int
            mutable.value = HomeArtworkCollectionState(artworks, updatedAt = updatedAt)
    }
}

private fun encodeCollection(artworks: List<OnlineHomeArtwork>, rotation: Int, updated: Instant, seen: List<String>, reviewed: List<ReviewedHomeArtwork>): String =
    buildJsonObject {
        put("rotation", rotation); put("updated", updated.toString())
        put("reviewedCatalog", encodeReviewedArtworkFeed(reviewed))
        putJsonArray("seen") { seen.forEach { add(it) } }
        putJsonArray("artworks") {
            artworks.forEach { artwork -> add(buildJsonObject {
                put("id", artwork.id); put("title", artwork.title); put("artist", artwork.artist)
                put("source", artwork.sourceUrl); put("image", artwork.imageUrl)
            }) }
        }
    }.toString()

@Composable
expect fun rememberHomeArtworkRepository(): HomeArtworkRepository

/** Allows previews/tests to supply a real interface implementation without making HTTP requests. */
val LocalHomeArtworkRepository = staticCompositionLocalOf<HomeArtworkRepository?> { null }

@Composable
fun rememberHomeArtworkCollection(
    settings: HomeArtworkSettings = rememberHomeArtworkSettings(),
    clock: Clock = Clock.System,
): HomeArtworkCollectionState {
    if (LocalInspectionMode.current) return HomeArtworkCollectionState()
    val repository = LocalHomeArtworkRepository.current ?: rememberHomeArtworkRepository()
    val scheduled = rememberHomeArtworkState(settings, clock)
    val schedule = settings.schedule
    val revision = settings.refreshRevision
    val onlineEnabled = settings.onlineEnabled
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(repository, lifecycle, scheduled.rotation, schedule.frequency, revision, onlineEnabled, settings.theme, settings.reuseReviewedOnRefresh) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            repository.update(settings.schedule.rotationAt(clock.now()),
                settings.schedule.frequency == ArtworkFrequency.FIXED || !settings.onlineEnabled, settings.refreshRevision, settings.theme,
                reuseReviewed = settings.reuseReviewedOnRefresh)
            awaitCancellation()
        }
    }
    return repository.state.collectAsStateWithLifecycle().value
}

@Composable
fun currentHomeArtworkBitmap(page: Int): ImageBitmap? =
    rememberHomeArtworkCollection().artworks.getOrNull(page.coerceIn(0, 4))?.bitmap

@Composable
fun currentHomeArtworkPainter(page: Int, useNightArtwork: Boolean = false): Painter {
    val bitmap = currentHomeArtworkBitmap(page)
    return if (bitmap != null) remember(bitmap) { BitmapPainter(bitmap) }
    else painterResource(currentHomeArtwork(page, useNightArtwork))
}
