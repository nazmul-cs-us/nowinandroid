package com.starception.submission.feature.quran

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlin.math.abs

/** Snap only near a boundary; a viewport inside a long passage remains freely scrollable. */
fun ayahSnapDistance(anchors: List<Int>, insetPx: Int, maxDistancePx: Int): Float? =
    anchors.map { it - insetPx }.minByOrNull { abs(it) }
        ?.takeIf { abs(it) <= maxDistancePx }?.toFloat()

@Composable
fun rememberAyahFlingBehavior(state: LazyListState, firstAyahIndex: Int,
    firstAyahHeaderPx: Int = 0, topInset: Dp = 0.dp): FlingBehavior {
    val decay = ScrollableDefaults.flingBehavior()
    val density = LocalDensity.current
    val inset = with(density) { topInset.roundToPx() }
    val maximumSnap = with(density) { 96.dp.roundToPx() }
    return remember(state, decay, firstAyahIndex, firstAyahHeaderPx, inset, maximumSnap) {
        object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                val left = with(decay) { performFling(initialVelocity) }
                val info = state.layoutInfo
                val coveringAyah = info.visibleItemsInfo.firstOrNull {
                    it.index >= firstAyahIndex && it.offset + (if (it.index == firstAyahIndex) firstAyahHeaderPx else 0) < inset &&
                        it.offset + it.size > info.viewportEndOffset
                }
                if (coveringAyah != null) return left
                val anchors = info.visibleItemsInfo.filter { it.index >= firstAyahIndex }.flatMap {
                    if (it.index == firstAyahIndex && firstAyahHeaderPx > 0)
                        listOf(it.offset, it.offset + firstAyahHeaderPx)
                    else listOf(it.offset)
                }
                val distance = ayahSnapDistance(anchors, inset,
                    minOf(maximumSnap, (info.viewportEndOffset - info.viewportStartOffset) / 4)) ?: return left
                var last = 0f
                AnimationState(0f).animateTo(distance, tween(180)) {
                    val delta = value - last
                    val consumed = scrollBy(delta)
                    last = value
                    if (abs(delta - consumed) > 0.5f) cancelAnimation()
                }
                return left
            }
        }
    }
}

@Stable
class AyahScrollGuide {
    internal var onUserScroll: (() -> Unit)? = null
    val scrollConnection = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (source == NestedScrollSource.UserInput && consumed.y != 0f) onUserScroll?.invoke()
            return Offset.Zero
        }
    }
    var following by mutableStateOf(true)
        private set
    var hintVisible by mutableStateOf(false)
        private set
    var longAyahHint by mutableStateOf(false)
        private set
    private var hintShown = false
    fun manualScroll(narrating: Boolean, longAyah: Boolean) {
        if (narrating) following = false
        else if (!hintShown) { hintShown = true; longAyahHint = longAyah; hintVisible = true }
    }
    fun follow() { following = true; hintVisible = false }
    fun dismissHint() { hintVisible = false }
}

@Composable
fun rememberAyahScrollGuide(state: LazyListState, resetKey: String, enabled: Boolean,
    narration: IntRange?, firstAyahIndex: Int, firstAyahHeaderPx: Int = 0, topInset: Dp = 0.dp, beforeFollow: () -> Unit = {}): AyahScrollGuide {
    val guide = remember(resetKey, enabled) { AyahScrollGuide() }
    val prepareFollow by rememberUpdatedState(beforeFollow)
    val latestNarration by rememberUpdatedState(narration)
    val latestFirstIndex by rememberUpdatedState(firstAyahIndex)
    val latestHeaderHeight by rememberUpdatedState(firstAyahHeaderPx)
    val inset = with(LocalDensity.current) { topInset.roundToPx() }
    SideEffect {
        guide.onUserScroll = {
            if (enabled) {
                val info = state.layoutInfo
                val item = info.visibleItemsInfo.firstOrNull { it.index == state.firstVisibleItemIndex }
                if (item != null && item.index >= latestFirstIndex) {
                    val bodySize = item.size - if (item.index == latestFirstIndex) latestHeaderHeight else 0
                    guide.manualScroll(latestNarration != null, bodySize > info.viewportEndOffset - info.viewportStartOffset - inset)
                }
            }
        }
    }
    LaunchedEffect(state, guide, enabled) {
        if (enabled) state.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) guide.onUserScroll?.invoke()
        }
    }
    LaunchedEffect(guide, narration != null) { if (narration == null) guide.follow() else guide.dismissHint() }
    LaunchedEffect(guide.hintVisible) { if (guide.hintVisible) { delay(5000); guide.dismissHint() } }
    LaunchedEffect(guide, narration?.first, guide.following, firstAyahHeaderPx, firstAyahIndex, inset) {
        if (enabled && guide.following && narration != null) {
            snapshotFlow { state.isScrollInProgress }.first { !it }
            prepareFollow()
            val index = firstAyahIndex + narration.first - 1
            snapshotFlow { state.layoutInfo.totalItemsCount }.first { it > index }
            state.animateScrollToItem(index, (if (narration.first == 1) firstAyahHeaderPx else 0) - inset)
        }
    }
    return guide
}

@Composable
fun AyahScrollHint(guide: AyahScrollGuide, narrating: Boolean, modifier: Modifier = Modifier) {
    val followPaused = narrating && !guide.following
    AnimatedVisibility(visible = followPaused || guide.hintVisible, modifier = modifier,
        enter = fadeIn(), exit = fadeOut()) {
        Surface(shape = RoundedCornerShape(16.dp), shadowElevation = 4.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(when {
                    followPaused -> "Auto-follow paused"
                    guide.longAyahHint -> "Keep scrolling to read this ayah’s tafsir"
                    else -> "Swipe to move between ayahs"
                }, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { if (followPaused) guide.follow() else guide.dismissHint() }) {
                    Text(if (followPaused) "Follow ayah" else "Got it")
                }
            }
        }
    }
}

@Composable
fun AyahNarrationGroup(ayah: Int, narrating: Boolean, studyMode: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val group = Modifier.fillMaxWidth().testTag("tafsir-ayah-$ayah").semantics {
        stateDescription = if (narrating) "Narrating ayah $ayah" else "Ayah $ayah"
    }
    if (studyMode) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 680.dp).then(group).padding(vertical = 12.dp).background(
                if (narrating) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else androidx.compose.ui.graphics.Color.Transparent)) {
                content()
                HorizontalDivider(Modifier.padding(horizontal = 24.dp).padding(top = 20.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
    } else Column(group.background(
        if (narrating) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(16.dp)), content = content)
}
