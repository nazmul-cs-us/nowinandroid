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
)

data class HomeArtworkCollectionState(
    val artworks: List<OnlineHomeArtwork> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val updatedAt: Instant? = null,
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
    private val catalog: List<ReviewedHomeArtwork> = reviewedIslamicArtwork,
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
    private val seen = homeArtworkGallery.map { it.sourceUrl.substringAfterLast('/') }.toMutableSet()
    private val restored = scope.async { restore() }

    /** Manual revisions are consumed once across Home, Settings, previews and expanded tiles. */
    fun update(rotation: Int, frozen: Boolean, manualRevision: Int = 0) {
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
                val previous = mutable.value
                mutable.value = previous.copy(loading = true, error = null)
                try {
                    val downloaded = withContext(ioDispatcher) { fetchUnseen() }
                    ensureActive()
                    if (version != generation) return@withLock
                    val updated = clock.now()
                    val updatedSeen = (seen + downloaded.map { it.id }).toList().takeLast(2000)
                    // The small atomic metadata commit is the collection's publication point.
                    platform.writeMetadata(encodeCollection(downloaded, rotation, updated, updatedSeen))
                    seen.addAll(downloaded.map { it.id })
                    activeRotation = rotation
                    lastFailure = null
                    mutable.value = HomeArtworkCollectionState(downloaded, updatedAt = updated)
                } catch (cancelled: CancellationException) {
                    if (version == generation) mutable.value = previous.copy(loading = false)
                    throw cancelled
                } catch (failure: Exception) {
                    if (version == generation) {
                        lastFailure = clock.now()
                        mutable.value = previous.copy(loading = false,
                            error = if (failure is ReviewedArtworkExhausted) {
                                "You've seen all available reviewed Islamic artwork. Your current collection stays until more reviewed artwork is added."
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
    private suspend fun fetchUnseen(): List<OnlineHomeArtwork> {
        val chosen = linkedMapOf<String, OnlineHomeArtwork>()
        val unseen = catalog.distinctBy { it.artwork.id }.filter { it.artwork.id !in seen }
        if (unseen.size < 5) throw ReviewedArtworkExhausted()
        val offset = chooseOffset(unseen.size).mod(unseen.size)
        val candidates = unseen.drop(offset) + unseen.take(offset)
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
            val artworks = root.getValue("artworks").jsonArray.map { value ->
                val item = value.jsonObject
                val id = item.getValue("id").jsonPrimitive.content
                // Reject all legacy search results and reconstruct credits from
                // the reviewed registry, never from untrusted cache metadata.
                val reviewed = requireNotNull(catalog.find { it.artwork.id == id })
                val bytes = requireNotNull(platform.readImage(id))
                require(artworkSha256(bytes) == reviewed.sha256)
                val bitmap = bytes.decodeToImageBitmap()
                require(bitmap.width in 100..1800 && bitmap.height in 100..1800)
                reviewed.artwork.copy(bitmap = cropArtwork(bitmap, reviewed.crop))
            }
            require(artworks.size == 5 && artworks.distinctBy { it.id }.size == 5)
            val updatedAt = Instant.parse(root.getValue("updated").jsonPrimitive.content)
            activeRotation = root.getValue("rotation").jsonPrimitive.int
            mutable.value = HomeArtworkCollectionState(artworks, updatedAt = updatedAt)
        }
    }
}

private fun encodeCollection(artworks: List<OnlineHomeArtwork>, rotation: Int, updated: Instant, seen: List<String>): String =
    buildJsonObject {
        put("rotation", rotation); put("updated", updated.toString())
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
    LaunchedEffect(repository, lifecycle, scheduled.rotation, schedule.frequency, revision, onlineEnabled) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            repository.update(settings.schedule.rotationAt(clock.now()),
                settings.schedule.frequency == ArtworkFrequency.FIXED || !settings.onlineEnabled, settings.refreshRevision)
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
