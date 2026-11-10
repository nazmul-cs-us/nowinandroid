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

private const val GETTY_SEARCH = "https://www.getty.edu/art/collection/api/search"
private const val GETTY_MEDIA = "https://media.getty.edu/iiif/image/"
private val queries = listOf("landscape", "clouds", "mountain", "seascape", "geometric")
private val excludedSubjects = listOf("portrait", "figure", "woman", "women", " man ", " men ", "child",
    "people", "person", "family", "animal", "bird", "horse", "cattle", "sheep", "dog", "cat ",
    "nude", "hunting", "battle", "christ", "saint", "madonna", "angel")

/** Search metadata filters are best-effort; they are not a visual review of the downloaded image. */
internal fun parseGettyArtworkSearch(json: String): Pair<Int, List<OnlineHomeArtwork>> {
    val root = Json.parseToJsonElement(json).jsonObject
    val entries = root["data"]?.jsonArray.orEmpty().mapNotNull { element ->
        runCatching {
            val item = element.jsonObject
            if (item["is_parent"]?.jsonPrimitive?.booleanOrNull == true) return@runCatching null
            val manifest = item["manifest"]?.jsonObject ?: return@runCatching null
            val license = manifest["license"]?.jsonPrimitive?.content.orEmpty().replace("http:", "https:")
            if (license != "https://creativecommons.org/publicdomain/zero/1.0/") return@runCatching null
            val title = item["primary_name"]?.jsonPrimitive?.content?.trim('[', ']') ?: return@runCatching null
            val description = " $title ${manifest["altText"]?.jsonPrimitive?.content.orEmpty()} ".lowercase()
            if (excludedSubjects.any { it in description }) return@runCatching null
            val id = item["id_manager_slug"]?.jsonPrimitive?.content ?: return@runCatching null
            val imageId = manifest["thumbUuid"]?.jsonPrimitive?.content ?: return@runCatching null
            if (!id.matches(Regex("[A-Za-z0-9_-]{1,80}")) ||
                !imageId.matches(Regex("[A-Za-z0-9-]{1,80}"))) return@runCatching null
            val artist = item["producers"]?.jsonArray.orEmpty().mapNotNull {
                it.jsonObject["primary_name"]?.jsonPrimitive?.content
            }.distinct().joinToString(", ").ifBlank { "Unknown artist" }
            OnlineHomeArtwork(id, title, artist, "https://www.getty.edu/art/collection/object/$id",
                "$GETTY_MEDIA$imageId/full/!1440,1440/0/default.jpg")
        }.getOrNull()
    }.distinctBy { it.id }
    return (root["total"]?.jsonPrimitive?.intOrNull ?: 0) to entries
}

/** Owns a persistent last-good collection. Never rotates bundled resources as a pretend download. */
class HomeArtworkRepository(
    private val platform: HomeArtworkPlatform,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val clock: Clock = Clock.System,
    private val chooseOffset: (Int) -> Int = { Random.nextInt(it) },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
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
                    val downloaded = withContext(ioDispatcher) { fetchUnseen(rotation) }
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
                } catch (_: Exception) {
                    if (version == generation) {
                        lastFailure = clock.now()
                        mutable.value = previous.copy(loading = false,
                            error = "Couldn't download new artwork. Your current collection is unchanged. Try Refresh now when you're online.")
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
    private suspend fun fetchUnseen(rotation: Int): List<OnlineHomeArtwork> {
        val chosen = linkedMapOf<String, OnlineHomeArtwork>()
        var downloads = 0
        for (attempt in 0 until 6) {
            val query = queries[(rotation.toLong() + attempt).mod(queries.size.toLong()).toInt()]
            val base = "$GETTY_SEARCH?q=$query&open_content=true&images=true&is_standalone=true"
            val first = parseGettyArtworkSearch(platform.download("$base&from=0&size=48", 2_000_000).decodeToString())
            val candidates = if (first.first > 48) {
                val offset = chooseOffset(first.first.coerceAtMost(2000).coerceAtLeast(1))
                parseGettyArtworkSearch(platform.download("$base&from=$offset&size=48", 2_000_000).decodeToString()).second + first.second
            } else first.second
            for (artwork in candidates) {
                if (artwork.id in seen || artwork.id in chosen) continue
                check(downloads++ < 15) { "Too many failed artwork downloads" }
                val result = runCatching {
                    val bytes = platform.download(artwork.imageUrl, 8_000_000)
                    val image = bytes.decodeToImageBitmap()
                    require(image.width in 100..1800 && image.height in 100..1800)
                    platform.writeImage(artwork.id, bytes)
                    artwork.copy(bitmap = image)
                }.getOrElse {
                    if (it is CancellationException) throw it
                    null
                }
                if (result != null) chosen[result.id] = result
                if (chosen.size == 5) return chosen.values.toList()
            }
        }
        error("Getty did not provide five new eligible artworks")
    }

    @OptIn(ExperimentalResourceApi::class)
    private suspend fun restore() = withContext(ioDispatcher) {
        runCatching {
            val root = Json.parseToJsonElement(platform.readMetadata() ?: return@runCatching).jsonObject
            seen.addAll(root["seen"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content })
            val artworks = root.getValue("artworks").jsonArray.map { value ->
                val item = value.jsonObject
                val id = item.getValue("id").jsonPrimitive.content
                val bitmap = requireNotNull(platform.readImage(id)).decodeToImageBitmap()
                OnlineHomeArtwork(id, item.getValue("title").jsonPrimitive.content,
                    item.getValue("artist").jsonPrimitive.content, item.getValue("source").jsonPrimitive.content,
                    item.getValue("image").jsonPrimitive.content, bitmap)
            }
            require(artworks.size == 5)
            activeRotation = root.getValue("rotation").jsonPrimitive.int
            mutable.value = HomeArtworkCollectionState(artworks,
                updatedAt = Instant.parse(root.getValue("updated").jsonPrimitive.content))
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
