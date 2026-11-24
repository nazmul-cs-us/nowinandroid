package com.starception.submission.settings.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.clipPath
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

/** Stable IDs are also used by the native iOS Family screen. Visits never leave this device. */
enum class GuideSection(val label: String, val title: String, val tip: String, val detail: String, val icon: ImageVector) {
    HOME("Home", "Your day, at a glance", "Check your next prayer and today's schedule on Home.",
        "Use Home for prayer times and daily insights. Open prayer settings to check your location, calculation method and reminders.", Icons.Outlined.Home),
    READING("Reading", "Make time for today's reading", "Read an ayah with its translation. Open Tafsir for its explanation.",
        "Tap today's reading to expand it. Use the play control to listen, and open the Quran library to choose a surah.", Icons.Outlined.MenuBook),
    SEARCH("Search", "Find what you need", "Search for Quran passages, hadith, duas and reciters.",
        "Tap the search field and enter a name or topic. The microphone starts voice search when voice setup is available.", Icons.Outlined.Search),
    FAMILY("Family", "Stay close to your family", "Accept a family invitation before starting a chat or call.",
        "Sign in or create an account, add trusted family members, and accept invitations. Then use chat, voice or video calls. Background call alerts need notification setup.", Icons.Outlined.FavoriteBorder),
    SAVED("Saved", "Keep meaningful passages close", "Find bookmarked content in Saved.",
        "Use the bookmark control while reading. Open Saved from navigation to return to those passages.", Icons.Outlined.BookmarkBorder),
    RECITERS("Reciters", "Listen online or offline", "Search by name, then tap Play to preview a reciter.",
        "Open Content & Storage in Settings for the reciter library. Portraits help identify supported reciters; styles identify recordings. Download audio explicitly for offline listening.", Icons.Outlined.Headphones),
    SETTINGS("Settings", "Make Deenly yours", "Adjust themes, prayer settings, audio and notifications here.",
        "Choose a curated theme in Appearance, manage content in Content & Storage, and configure prayer reminders. You can replay this guide here at any time.", Icons.Outlined.Settings),
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
            if (above >= topInset) above else it.bottom.roundToInt() + gap
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
                    clipPath(opening, ClipOp.Difference) { drawRect(colors.scrim.copy(alpha = .32f)) }
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
private fun AppGuideTour(store: AppGuideStore, onClose: () -> Unit) {
    // Unvisited sections come first. Reading a tip does not count as visiting the actual section.
    val steps = remember { GuideSection.entries.sortedBy { store.visited(it) } }
    var step by rememberSaveable { mutableIntStateOf(0) }
    val current = steps[step.coerceIn(steps.indices)]
    LaunchedEffect(current) { store.markTipSeen(current) }
    DisposableEffect(Unit) { GuidePresentation.tourOpen = true; onDispose { GuidePresentation.tourOpen = false } }
    Dialog(onDismissRequest = onClose, properties = artworkDialogProperties()) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp)) {
            val compact = maxHeight < 480.dp
            Surface(Modifier.align(if (maxWidth < 560.dp) Alignment.BottomCenter else Alignment.Center).widthIn(max = 520.dp).fillMaxWidth().heightIn(max = maxHeight)
                .testTag("appGuideTour"), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(if (compact) 16.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("App guide · ${step + 1} of ${steps.size}", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                        TextButton(onClick = onClose, modifier = Modifier.testTag("appGuideSkip")) { Text("Close") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        steps.indices.forEach { index ->
                            Spacer(Modifier.weight(1f).height(3.dp).background(
                                if (index <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                RoundedCornerShape(2.dp)))
                        }
                    }
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
                        if (!compact) Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                            Icon(current.icon, null, Modifier.padding(12.dp).size(28.dp))
                        }
                        Text(current.title, Modifier.semantics { heading() }, style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall)
                        Text(if (store.visited(current)) "Already explored" else "Not explored yet",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(current.detail, style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { step-- }, enabled = step > 0, modifier = Modifier.testTag("appGuideBack")) { Text("Back") }
                        Spacer(Modifier.weight(1f))
                        Button(onClick = { if (step == steps.lastIndex) onClose() else step++ }, modifier = Modifier.testTag("appGuideNext")) {
                            Text(if (step == steps.lastIndex) "Done" else "Next")
                        }
                    }
                }
            }
        }
    }
}
