package com.starception.submission.settings.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.composed
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

/** Stable IDs are also used by the native iOS Family screen. Visits never leave this device. */
enum class GuideSection(val label: String, val tip: String, val icon: ImageVector) {
    HOME("Home", "See the next prayer and its time on Home.", Icons.Outlined.Home),
    READING("Reading", "Open Tafsir to see the explanation beside the ayah.", Icons.Outlined.MenuBook),
    SEARCH("Search", "Search by a name, a phrase or a topic.", Icons.Outlined.Search),
    FAMILY("Family", "Accept a family invitation to start a conversation.", Icons.Outlined.FavoriteBorder),
    SAVED("Saved", "Tap the bookmark while reading. Find the surah here later.", Icons.Outlined.BookmarkBorder),
    RECITERS("Reciters", "Search by name, then tap Play to preview a reciter.", Icons.Outlined.Headphones),
    SETTINGS("Settings", "Choose a theme and display mode in Appearance.", Icons.Outlined.Settings),
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
    var activeTip by mutableStateOf<GuideSection?>(null)
}

private val guideAnchors = mutableStateMapOf<Any, Pair<GuideSection, Rect>>()

fun Modifier.appGuideAnchor(section: GuideSection?): Modifier = if (section == null) this else composed {
    val key = remember(section) { Any() }
    DisposableEffect(key) { onDispose { guideAnchors.remove(key) } }
    Modifier.onGloballyPositioned { guideAnchors[key] = section to it.boundsInWindow() }
}

private class GuideTipPosition(val target: Rect, val gap: Int, val bottomInset: Int, val topInset: Int) {
    fun calculatePosition(windowSize: IntSize,
        popupContentSize: IntSize): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val maxY = (windowSize.height - popupContentSize.height - bottomInset).coerceAtLeast(0)
        val x = (target.center.x - popupContentSize.width / 2f).roundToInt()
        val y = with(target) {
            val above = top.roundToInt() - gap - popupContentSize.height
            val below = bottom.roundToInt() + gap
            when {
                above >= topInset -> above
                below <= maxY -> below
                topInset - above <= below - maxY -> topInset
                else -> maxY
            }
        }
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
    val anchor = guideAnchors.values.lastOrNull { it.first == section && !it.second.isEmpty }?.second
    val hasAnchor = anchor != null
    var tip by remember(section) { mutableStateOf<GuideSection?>(null) }
    LaunchedEffect(section, allowed, resumed) {
        if (section != null && allowed && resumed) store.visit(section)
    }
    LaunchedEffect(section, allowed, resumed, hasAnchor) {
        tip = null
        if (section != null && allowed && resumed && hasAnchor && !store.tipSeen(section) && store.tipsEnabled()) {
            delay(650)
            GuidePresentation.activeTip = section
            tip = section
        }
    }
    val current = tip
    if (current != null && allowed && resumed && anchor != null && GuidePresentation.activeTip == current) {
        DisposableEffect(current) {
            store.markTipSeen(current)
            onDispose { if (GuidePresentation.activeTip == current) GuidePresentation.activeTip = null }
        }
        var origin by remember { mutableStateOf(Offset.Zero) }
        val target = anchor.translate(-origin)
        val density = LocalDensity.current
        val insets = WindowInsets.safeDrawing.union(WindowInsets.ime)
        val position = GuideTipPosition(target, with(density) { 6.dp.roundToPx() },
            insets.getBottom(density) + with(density) { 8.dp.roundToPx() }, insets.getTop(density))
        val colors = MaterialTheme.colorScheme
        val highlight = remember(current) { Animatable(0f) }
        val cardOpacity = remember(current) { Animatable(0f) }
        LaunchedEffect(highlight) {
            highlight.animateTo(1f, tween(durationMillis = 950, easing = LinearOutSlowInEasing))
        }
        LaunchedEffect(cardOpacity) {
            cardOpacity.animateTo(1f, tween(durationMillis = 220))
        }
        val spotlight = modifier.onGloballyPositioned { origin = it.positionInWindow() }.drawWithCache {
            val window = Rect(Offset.Zero, size)
            val bounds = target.intersect(window).takeUnless { it.isEmpty }?.inflate(3.dp.toPx())
            val corner = 18.dp.toPx()
            val ringWidth = 1.5.dp.toPx()
            val rippleDistance = 9.dp.toPx()
            onDrawBehind {
                if (bounds != null) {
                    val progress = highlight.value
                    val ripple = bounds.inflate(rippleDistance * progress)
                    drawRoundRect(
                        color = colors.primary.copy(alpha = .36f * (1f - progress)),
                        topLeft = ripple.topLeft,
                        size = ripple.size,
                        cornerRadius = CornerRadius(corner + rippleDistance * progress),
                        style = Stroke(ringWidth),
                    )
                    drawRoundRect(
                        color = colors.primary.copy(alpha = .84f),
                        topLeft = bounds.topLeft,
                        size = bounds.size,
                        cornerRadius = CornerRadius(corner),
                        style = Stroke(ringWidth),
                    )
                }
            }
        }
        Layout(modifier = spotlight, content = {
            Surface(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).widthIn(max = 340.dp)
                .fillMaxWidth().heightIn(max = 260.dp)
                .graphicsLayer { alpha = cardOpacity.value }.testTag("appGuideTip")
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
