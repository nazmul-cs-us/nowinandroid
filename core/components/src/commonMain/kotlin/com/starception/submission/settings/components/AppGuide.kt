package com.starception.submission.settings.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import com.starception.submission.core.images.resources.*
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.composed
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.geometry.Offset
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

/** Stable IDs are also used by the native iOS Family screen. Visits never leave this device. */
enum class GuideSection(val label: String, val title: String, val tip: String, val steps: List<String>, val icon: ImageVector) {
    HOME("Home", "Check your prayer schedule", "See the next prayer and its time on Home.", listOf(
        "Open Home and find Prayer times.",
        "Read the time beside the prayer you want.",
        "If the times look wrong, open prayer settings and check your location and calculation method."
    ), Icons.Outlined.Home),
    READING("Reading", "Read an ayah with tafsir", "Open Tafsir to see the explanation beside the ayah.", listOf(
        "Open the Quran library and choose a surah.",
        "Use the language control to choose your translation.",
        "Tap Tafsir in the reader toolbar and choose an edition. If prompted, download the edition.",
        "Scroll to the ayah and expand its explanation. Use the play control to hear it aloud."
    ), Icons.Outlined.MenuBook),
    SEARCH("Search", "Find a passage or dua", "Search by a name, a phrase or a topic.", listOf(
        "Tap the search field on Home.",
        "Enter a name or topic, or use the microphone if voice search is available.",
        "Choose a result to open the passage."
    ), Icons.Outlined.Search),
    FAMILY("Family", "Connect with a family member", "Accept a family invitation to start a conversation.", listOf(
        "Open Family and sign in or create an account.",
        "Invite your family member using their Family username, or accept their invitation.",
        "Open the accepted contact to send a message or start a voice or video call."
    ), Icons.Outlined.FavoriteBorder),
    SAVED("Saved", "Bookmark and find a surah", "Tap the bookmark while reading. Find the surah here later.", listOf(
        "Open the surah you want to keep.",
        "Tap the bookmark icon in the reader toolbar.",
        "Open Saved from the main navigation and select the surah to return to it."
    ), Icons.Outlined.BookmarkBorder),
    RECITERS("Reciters", "Download a reciter for offline listening", "Search by name, then tap Play to preview a reciter.", listOf(
        "Open Settings, then Content & Storage.",
        "Open Reciters & offline audio and search for a reciter.",
        "Preview the recording, then choose Download. Wait until the recording is available offline."
    ), Icons.Outlined.Headphones),
    SETTINGS("Settings", "Change the look of Deenly", "Choose a theme and display mode in Appearance.", listOf(
        "Open Settings from Home.",
        "Expand Appearance and choose a theme.",
        "Select Light, Dark or System for the display mode."
    ), Icons.Outlined.Settings),
}

interface AppGuideStore {
    fun visited(section: GuideSection): Boolean
    fun visit(section: GuideSection)
    fun tipSeen(section: GuideSection): Boolean
    fun markTipSeen(section: GuideSection)
    fun tipsEnabled(): Boolean
    fun setTipsEnabled(enabled: Boolean)
}

@Composable expect fun rememberAppGuideStore(): AppGuideStore

val LocalAppGuideEnabled = staticCompositionLocalOf { true }

private object GuidePresentation {
    var tourOpen by mutableStateOf(false)
    var activeTip by mutableStateOf<GuideSection?>(null)
    var revision by mutableIntStateOf(0)
}

private val guideAnchors = mutableStateMapOf<Any, Pair<GuideSection, Rect>>()

fun Modifier.appGuideAnchor(section: GuideSection?): Modifier = if (section == null) this else composed {
    val key = remember(section) { Any() }
    DisposableEffect(key) { onDispose { guideAnchors.remove(key) } }
    Modifier.onGloballyPositioned { guideAnchors[key] = section to it.boundsInWindow() }
}

private class GuideTipPosition(val target: Rect?, val gap: Int, val bottomInset: Int, val topInset: Int) {
    fun calculatePosition(windowSize: IntSize,
        popupContentSize: IntSize): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val maxY = (windowSize.height - popupContentSize.height - bottomInset).coerceAtLeast(0)
        val x = target?.let { (it.center.x - popupContentSize.width / 2f).roundToInt() } ?: maxX / 2
        val y = target?.let {
            val above = it.top.roundToInt() - gap - popupContentSize.height
            val below = it.bottom.roundToInt() + gap
            when {
                above >= topInset -> above
                below <= maxY -> below
                topInset - above <= below - maxY -> topInset
                else -> maxY
            }
        } ?: maxY
        return IntOffset(x.coerceIn(0, maxX), y.coerceIn(topInset.coerceAtMost(maxY), maxY))
    }
}

/** Embedded readers record real use without adding a tip over the reading surface. */
@Composable
fun AppGuideRecordVisit(section: GuideSection, active: Boolean, store: AppGuideStore = rememberAppGuideStore()) {
    LaunchedEffect(section, active) { if (active) store.visit(section) }
}

/** A nonmodal first-visit tip. Navigation remains usable; moving away dismisses it. */
@Composable
fun AppGuideHost(section: GuideSection?, enabled: Boolean = true, store: AppGuideStore = rememberAppGuideStore(), modifier: Modifier = Modifier.fillMaxSize()) {
    val allowed = enabled && LocalAppGuideEnabled.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val resumed = lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    val tourOpen = GuidePresentation.tourOpen
    val revision = GuidePresentation.revision
    var tip by remember { mutableStateOf<GuideSection?>(null) }
    LaunchedEffect(section, allowed, resumed, tourOpen, revision) {
        tip = null
        if (section != null && allowed && resumed && !tourOpen) {
            val firstVisit = !store.visited(section)
            if (firstVisit && !store.tipSeen(section) && store.tipsEnabled()) {
                delay(650) // Avoid flashing a hint during a quick navigation transition.
                GuidePresentation.activeTip = section
                tip = section
            }
            store.visit(section)
        }
    }
    var guideTopic by rememberSaveable(section) { mutableStateOf<GuideSection?>(null) }
    guideTopic?.takeIf { allowed && resumed }?.let { topic ->
        AppGuideTour(store, initial = topic, onClose = { guideTopic = null })
    }
    tip?.takeIf { allowed && resumed && !tourOpen && section == it && GuidePresentation.activeTip == it }?.let { current ->
        DisposableEffect(current) {
            store.markTipSeen(current)
            onDispose { if (GuidePresentation.activeTip == current) GuidePresentation.activeTip = null }
        }
        var origin by remember { mutableStateOf(Offset.Zero) }
        val target = guideAnchors.values.lastOrNull { it.first == current && !it.second.isEmpty }?.second?.translate(-origin)
        val density = LocalDensity.current
        val insets = WindowInsets.safeDrawing.union(WindowInsets.ime)
        val clearance = if (target == null && current in listOf(GuideSection.HOME, GuideSection.SAVED, GuideSection.FAMILY)) 88.dp else 8.dp
        val position = GuideTipPosition(target, with(density) { 6.dp.roundToPx() },
            insets.getBottom(density) + with(density) { clearance.roundToPx() }, insets.getTop(density))
        val colors = MaterialTheme.colorScheme
        val spotlight = modifier.onGloballyPositioned { origin = it.positionInWindow() }.drawWithCache {
            val window = Rect(Offset.Zero, size)
            val opening = target?.intersect(window)?.takeUnless { it.isEmpty }?.let { bounds ->
                Path().apply {
                    addRoundRect(RoundRect(bounds.inflate(4.dp.toPx()), CornerRadius(18.dp.toPx())))
                }
            }
            onDrawBehind {
                if (opening != null) {
                    drawPath(opening, colors.primary.copy(alpha = .8f), style = Stroke(1.5.dp.toPx()))
                }
            }
        }
        Layout(modifier = spotlight, content = {
            Surface(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).widthIn(max = 340.dp)
                .fillMaxWidth().heightIn(max = 260.dp).testTag("appGuideTip")
                .semantics { liveRegion = LiveRegionMode.Polite },
                shape = RoundedCornerShape(20.dp), shadowElevation = 6.dp,
                border = BorderStroke(1.dp, colors.outlineVariant), color = colors.surfaceContainerLow) {
                Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(Modifier.padding(top = 5.dp), color = colors.secondaryContainer,
                        shape = RoundedCornerShape(12.dp)) {
                        Icon(current.icon, null, Modifier.padding(9.dp).size(19.dp))
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(current.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                        Text(current.tip, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        TextButton(onClick = { tip = null; guideTopic = current }, contentPadding = PaddingValues(horizontal = 2.dp)) {
                            Text("Show steps")
                        }
                    }
                    IconButton(onClick = { tip = null }, modifier = Modifier.testTag("appGuideTipDismiss")) {
                        Icon(Icons.Outlined.Close, "Dismiss tip", Modifier.size(18.dp), tint = colors.onSurfaceVariant)
                    }
                }
            }
        }) { children, constraints ->
            val card = children.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
            val width = if (constraints.hasBoundedWidth) constraints.maxWidth else card.width
            val height = if (constraints.hasBoundedHeight) constraints.maxHeight else card.height
            val offset = position.calculatePosition(IntSize(width, height), IntSize(card.width, card.height))
            layout(width, height) { card.place(offset.x, offset.y) }
        }
    }
}

/** Replay remains available even after all sections have been visited or tips are disabled. */
@Composable
fun AppGuideSettings(store: AppGuideStore = rememberAppGuideStore()) {
    var open by rememberSaveable { mutableStateOf(false) }
    var tipsEnabled by remember { mutableStateOf(store.tipsEnabled()) }
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("App guide", style = MaterialTheme.typography.titleMedium)
            Text("A quick tour of Home, reading, search, Family and offline listening.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Show first-use tips", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(tipsEnabled, onCheckedChange = {
                    tipsEnabled = it; store.setTipsEnabled(it); GuidePresentation.revision++
                }, modifier = Modifier.testTag("appGuideTipsSwitch").semantics { contentDescription = "Show first-use tips" })
            }
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().testTag("appGuideReplay")) {
                Icon(Icons.Outlined.HelpOutline, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Explore the app guide")
            }
        }
    }
    if (open) AppGuideTour(store, onClose = { open = false })
}

@Composable
private fun GuideArtwork(topic: GuideSection, modifier: Modifier = Modifier) {
    val image = when (topic) {
        GuideSection.HOME -> Res.drawable.home_cover_prayer_day_portrait
        GuideSection.READING -> Res.drawable.home_cover_quran_portrait
        GuideSection.SEARCH -> Res.drawable.home_cover_qibla_portrait
        GuideSection.FAMILY -> Res.drawable.home_getty_courtyard_night
        GuideSection.SAVED -> Res.drawable.home_getty_arches_night
        GuideSection.RECITERS -> Res.drawable.home_cover_prayer_night_portrait
        GuideSection.SETTINGS -> Res.drawable.home_getty_tiles_day
    }
    Box(modifier, contentAlignment = Alignment.BottomStart) {
        Image(painterResource(image), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent,
            Color.Black.copy(alpha = .84f)))))
        Column(Modifier.padding(16.dp)) {
            Text(topic.label, style = MaterialTheme.typography.labelMedium, color = Color.White)
            Text(topic.title, style = MaterialTheme.typography.titleMedium, color = Color.White,
                modifier = Modifier.semantics { heading() })
        }
    }
}

@Composable
private fun AppGuideTour(store: AppGuideStore, initial: GuideSection? = null, onClose: () -> Unit) {
    val topics = remember(store) { GuideSection.entries.sortedBy { store.visited(it) } }
    var selected by rememberSaveable { mutableStateOf(initial) }
    val topic = selected
    LaunchedEffect(topic) { topic?.let(store::markTipSeen) }
    DisposableEffect(Unit) { GuidePresentation.tourOpen = true; onDispose { GuidePresentation.tourOpen = false } }
    Dialog(onDismissRequest = { if (topic == null) onClose() else selected = null }, properties = artworkDialogProperties()) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(
            MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.tertiaryContainer
        ))).windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.Center) {
            val colors = MaterialTheme.colorScheme
            Surface(Modifier.widthIn(max = 580.dp).fillMaxSize()
                .testTag("appGuideTour").semantics { paneTitle = "App guide" }, color = Color.Transparent) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        if (topic != null) TextButton(onClick = { selected = null }, modifier = Modifier.testTag("appGuideBack")) {
                            Text("All topics")
                        } else Text("App guide", style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.semantics { heading() })
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onClose, modifier = Modifier.testTag("appGuideSkip")) {
                            Icon(Icons.Outlined.Close, "Close guide")
                        }
                    }
                    AnimatedContent(targetState = topic, modifier = Modifier.weight(1f),
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut()
                        }, label = "Guide topic") { page ->
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                            verticalArrangement = if (page == null) Arrangement.Center else Arrangement.Top) {
                            if (page == null) {
                                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    val featured = topics.first()
                                    Surface(onClick = { selected = featured }, shape = RoundedCornerShape(26.dp),
                                        shadowElevation = 6.dp) {
                                        GuideArtwork(featured, Modifier.widthIn(max = 280.dp).fillMaxWidth().heightIn(min = 220.dp))
                                    }
                                    Row(
                                        Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        topics.drop(1).forEach { item ->
                                            Surface(onClick = { selected = item }, shape = RoundedCornerShape(18.dp)) {
                                                GuideArtwork(item, Modifier.width(144.dp).heightIn(min = 144.dp))
                                            }
                                        }
                                    }
                                }
                            } else {
                                GuideArtwork(page, Modifier.fillMaxWidth().heightIn(min = if (this@BoxWithConstraints.maxHeight < 400.dp) 76.dp else 240.dp))
                                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    page.steps.forEachIndexed { index, instruction ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                            Surface(color = colors.secondaryContainer, shape = RoundedCornerShape(10.dp)) {
                                                Box(Modifier.sizeIn(minWidth = 32.dp, minHeight = 32.dp), contentAlignment = Alignment.Center) {
                                                    Text((index + 1).toString(), style = MaterialTheme.typography.labelLarge)
                                                }
                                            }
                                            Text(instruction, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (topic != null) {
                        val next = topics.getOrNull(topics.indexOf(topic) + 1)
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { if (next == null) onClose() else selected = next }, modifier = Modifier.testTag("appGuideNext")) {
                                Text(if (next == null) "Done" else "Next topic")
                            }
                        }
                    }
                }
            }
        }
    }
}
